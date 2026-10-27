package net.aviel.dialogue.npc.storage;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aviel.dialogue.entity.ai.NpcCombatProfile;
import net.aviel.dialogue.npc.NpcTemplateService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

public final class NpcTemplateProfiles {
    private NpcTemplateProfiles() {
    }

    private static Path path(Path directory, String id) throws IOException {
        if (NpcTemplateService.normalizeTemplateId(id).isBlank() || id.contains(":")) throw new IOException("Invalid NPC template ID");
        return directory.resolve(id + ".json");
    }

    private static Path file(Path directory, String id) throws IOException {
        Path file = path(directory, id);
        if (!Files.isRegularFile(file)) throw new IOException("Template file not found: " + id);
        return file;
    }

    public static NpcCombatProfile read(Path directory, String id) throws IOException {
        return profile(readJson(file(directory, id)));
    }

    public static net.aviel.dialogue.npc.social.NpcSocialProfile readSocial(Path directory, String id) throws IOException {
        var root = readJson(file(directory, id));
        try {
            return root.has("social") ? net.aviel.dialogue.npc.social.NpcSocialProfile.read(root.getAsJsonObject("social"))
                    : net.aviel.dialogue.npc.social.NpcSocialProfile.DEFAULT;
        } catch (RuntimeException ex) {
            throw new IOException("Invalid social profile: " + ex.getMessage(), ex);
        }
    }

    private static JsonObject readJson(Path path) throws IOException {
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new IOException("Invalid template JSON: " + path.getFileName(), ex);
        }
    }

    private static NpcCombatProfile profile(JsonObject root) throws IOException {
        try {
            return root.has("combat") ? NpcCombatProfile.fromJson(root.getAsJsonObject("combat")) : NpcCombatProfile.DEFAULT;
        } catch (RuntimeException ex) {
            throw new IOException("Invalid combat profile: " + ex.getMessage(), ex);
        }
    }

    public static void edit(Path directory, String id, Consumer<JsonObject> change) throws IOException {
        Path path = file(directory, id);
        JsonObject root = readJson(path);
        JsonObject combat = profile(root).toJson();
        change.accept(combat);
        NpcCombatProfile.fromJson(combat);
        // Preserve extension fields in both the template and its combat object.
        JsonObject merged = root.has("combat") ? root.getAsJsonObject("combat") : new JsonObject();
        combat.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
        root.add("combat", merged);
        writeRoot(directory, path, root);
    }

    public static void saveSettings(Path directory, String id, NpcCombatProfile combat,
                                    net.aviel.dialogue.npc.social.NpcSocialProfile social) throws IOException {
        saveSettings(directory, id, combat, social, null);
    }

    public static void saveSettings(Path directory, String id, NpcCombatProfile combat,
                                    net.aviel.dialogue.npc.social.NpcSocialProfile social, JsonObject appearance) throws IOException {
        Path path = path(directory, id);
        JsonObject root = Files.isRegularFile(path) ? readJson(path) : new JsonObject();
        if (!Files.isRegularFile(path) && appearance == null) throw new IOException("Template file not found: " + id);
        if (appearance != null) {
            for (String key : java.util.List.of("name", "dialogue", "dialogue_color", "dialogue_style", "scale", "name_visible", "invulnerable", "look_distance", "model", "skin", "equipment")) {
                if (appearance.has(key)) root.add(key, appearance.get(key).deepCopy());
                else root.remove(key);
            }
        }
        JsonObject oldCombat = root.has("combat") ? root.getAsJsonObject("combat") : new JsonObject();
        combat.toJson().entrySet().forEach(entry -> oldCombat.add(entry.getKey(), entry.getValue()));
        root.add("combat", oldCombat);
        if (social != null) mergeSocial(root, social);
        writeRoot(directory, path, root);
    }

    private static void mergeSocial(JsonObject root, net.aviel.dialogue.npc.social.NpcSocialProfile social) {
        JsonObject oldSocial = root.has("social") ? root.getAsJsonObject("social") : new JsonObject();
        JsonObject nextSocial = social.toJson();
        JsonObject opinion = oldSocial.has("opinion") ? oldSocial.getAsJsonObject("opinion") : new JsonObject();
        for (String key : java.util.List.of("scope", "faction", "key", "hostile_at")) opinion.remove(key);
        nextSocial.getAsJsonObject("opinion").entrySet().forEach(entry -> opinion.add(entry.getKey(), entry.getValue()));
        oldSocial.addProperty("faction", social.faction());
        oldSocial.addProperty("combat", social.combat());
        oldSocial.add("opinion", opinion);
        root.add("social", oldSocial);
    }

    private static void writeRoot(Path directory, Path path, JsonObject root) throws IOException {
        Path temporary = Files.createTempFile(directory, "adm-template-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
