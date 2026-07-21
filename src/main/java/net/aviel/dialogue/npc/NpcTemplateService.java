package net.aviel.dialogue.npc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.npc.storage.DialogueStorage;
import net.aviel.dialogue.util.JsonReaders;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class NpcTemplateService {
    public static final String AVIEL_TEMPLATE_ID = "Aviel__";
    /** Texture bundled with the mod, also used as the fallback look for skinless NPCs. */
    public static final String AVIEL_SKIN = "adm:textures/entity/npc/aviel__.png";
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private NpcTemplateService() {
    }

    public static String normalizeTemplateId(String input) {
        if (input == null) return "";
        String raw = input.trim();
        if (raw.isBlank() || raw.length() > 80 || raw.contains("/") || raw.contains("\\") || raw.contains("..") || raw.contains(" ")) {
            return "";
        }
        return raw;
    }

    public static List<String> listNpcTemplates(MinecraftServer server) {
        DialogueStorage.ensureDirectories();
        Set<String> names = new LinkedHashSet<>();
        DialogueStorage.collectJsonFileNames(DialogueStorage.npcTemplateDirectory(), names);
        List<String> result = new ArrayList<>();
        names.stream()
                .map(name -> name.substring(0, name.length() - ".json".length()))
                .sorted(Comparator.naturalOrder())
                .forEach(result::add);
        if (!result.contains(AVIEL_TEMPLATE_ID)) {
            result.add(0, AVIEL_TEMPLATE_ID);
        }
        return result;
    }

    public static NpcTemplate loadNpcTemplate(MinecraftServer server, String templateId) throws IOException {
        String normalized = normalizeTemplateId(templateId);
        if (normalized.isBlank()) {
            throw new IOException("Invalid NPC template id.");
        }
        Path path = DialogueStorage.npcTemplateDirectory().resolve(normalized + ".json");
        if (!Files.isRegularFile(path)) {
            if (AVIEL_TEMPLATE_ID.equals(normalized)) {
                return avielTemplate();
            }
            throw new IOException("NPC template does not exist: " + normalized);
        }
        JsonElement element = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
        if (!element.isJsonObject()) {
            throw new IOException("NPC template root must be an object.");
        }
        JsonObject object = element.getAsJsonObject();
        String name = JsonReaders.readString(object, "name", normalized);
        String dialogue = NpcDialogueService.normalizeReference(
                JsonReaders.readString(object, "dialogue", JsonReaders.readString(object, "dialogue_file", "")));
        float scale = clampFloat(JsonReaders.readFloat(object, "scale", DialogueNpcEntity.DEFAULT_MODEL_SCALE), 0.25F, 3.0F);
        boolean nameVisible = JsonReaders.readBoolean(object, "name_visible", true);
        boolean invulnerable = JsonReaders.readBoolean(object, "invulnerable", true);
        float lookDistance = clampFloat(JsonReaders.readFloat(object, "look_distance", DialogueNpcEntity.DEFAULT_LOOK_DISTANCE), 0.0F, 64.0F);
        String skin = NpcDialogueService.normalizeSkin(JsonReaders.readString(object, "skin", ""));
        String model = NpcDialogueService.normalizePlayerModel(
                JsonReaders.readString(object, "model", JsonReaders.readString(object, "player_model", DialogueNpcEntity.DEFAULT_PLAYER_MODEL)));
        List<NpcEquipment.Entry> equipment = NpcEquipment.readFromJson(object);
        return new NpcTemplate(normalized, name, dialogue, scale, nameVisible, invulnerable, lookDistance, skin, model, equipment);
    }

    public static void applyTemplate(DialogueNpcEntity npc, NpcTemplate template) {
        if (npc == null || template == null) {
            return;
        }
        npc.setTemplateId(template.id());
        npc.setCustomName(Component.literal(template.name().isBlank() ? template.id() : template.name()));
        npc.setCustomNameVisible(template.nameVisible());
        npc.setDialogueFile(template.dialogueFile());
        npc.setModelScale(template.scale());
        npc.setDialogueInvulnerable(template.invulnerable());
        npc.setLookDistance(template.lookDistance());
        npc.setSkin(template.skin());
        npc.setPlayerModel(template.model());
        NpcEquipment.apply(npc, template.equipment());
        npc.setPersistenceRequired();
    }

    /** Serializes a live NPC into template-file JSON, including equipment. */
    public static String templateJsonFor(DialogueNpcEntity npc) {
        JsonObject root = new JsonObject();
        root.addProperty("name", npc.getProfileName().replace('§', '&'));
        root.addProperty("dialogue", npc.getDialogueFile());
        root.addProperty("scale", npc.getModelScale());
        root.addProperty("name_visible", npc.isCustomNameVisible());
        root.addProperty("invulnerable", npc.isDialogueInvulnerable());
        root.addProperty("look_distance", npc.getLookDistance());
        root.addProperty("model", npc.getPlayerModel());
        root.addProperty("skin", npc.getSkin());
        if (NpcEquipment.hasAny(npc)) {
            root.add("equipment", NpcEquipment.toJson(npc));
        }
        return PRETTY_GSON.toJson(root);
    }

    private static NpcTemplate avielTemplate() {
        return new NpcTemplate(AVIEL_TEMPLATE_ID, AVIEL_TEMPLATE_ID, "aviel__.json", DialogueNpcEntity.DEFAULT_MODEL_SCALE, true, true, DialogueNpcEntity.DEFAULT_LOOK_DISTANCE, AVIEL_SKIN, DialogueNpcEntity.DEFAULT_PLAYER_MODEL, List.of());
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public record NpcTemplate(
            String id,
            String name,
            String dialogueFile,
            float scale,
            boolean nameVisible,
            boolean invulnerable,
            float lookDistance,
            String skin,
            String model,
            List<NpcEquipment.Entry> equipment
    ) {
        public NpcTemplate {
            id = normalizeTemplateId(id);
            name = name == null ? "" : name.trim();
            dialogueFile = NpcDialogueService.normalizeReference(dialogueFile);
            scale = clampFloat(scale, 0.25F, 3.0F);
            lookDistance = clampFloat(lookDistance, 0.0F, 64.0F);
            skin = NpcDialogueService.normalizeSkin(skin);
            model = NpcDialogueService.normalizePlayerModel(model);
            equipment = List.copyOf(equipment == null ? List.of() : equipment);
        }
    }
}
