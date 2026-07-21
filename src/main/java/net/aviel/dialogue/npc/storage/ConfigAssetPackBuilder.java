package net.aviel.dialogue.npc.storage;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aviel.dialogue.AvielsDialogueMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mirrors skins, sounds and lang files from {@code config/adm-dialogues} into the
 * generated resource pack that is delivered to clients.
 */
public final class ConfigAssetPackBuilder {
    public static final String CONFIG_ASSET_NAMESPACE = "adm";
    private static final Gson GSON = new Gson();

    private ConfigAssetPackBuilder() {
    }

    public static Path prepare() {
        Path packRoot = DialogueStorage.resourcePackDirectory();
        try {
            Files.createDirectories(packRoot);
            DialogueStorage.ensureDirectories();
            writePackMetadata(packRoot);
            syncSkins(packRoot);
            syncSounds(packRoot);
            syncLang(packRoot);
        } catch (IOException ex) {
            // Resource loading will simply ignore the generated pack if something goes wrong here.
            AvielsDialogueMod.LOGGER.warn("Failed to prepare ADM config asset pack", ex);
        }
        return packRoot;
    }

    public static String normalizeConfigAssetFile(String raw, String folderPrefix, String extension, boolean appendExtension) {
        String value = raw.replace('\\', '/').trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.toLowerCase(Locale.ROOT).startsWith(folderPrefix)) {
            value = value.substring(folderPrefix.length());
        }
        if (value.isBlank() || value.contains("..") || value.length() > 180) {
            return "";
        }
        if (appendExtension && !value.toLowerCase(Locale.ROOT).endsWith(extension)) {
            value += extension;
        }
        value = value.toLowerCase(Locale.ROOT);
        for (String part : value.split("/")) {
            if (part.isBlank() || !part.matches("[a-z0-9_.-]+")) {
                return "";
            }
        }
        return value;
    }

    private static void writePackMetadata(Path packRoot) throws IOException {
        String metadata = """
                {
                  "pack": {
                    "pack_format": 34,
                    "description": "ADM config assets"
                  }
                }
                """;
        Files.writeString(packRoot.resolve("pack.mcmeta"), metadata, StandardCharsets.UTF_8);
    }

    private static void syncSkins(Path packRoot) throws IOException {
        Path target = packRoot.resolve("assets").resolve(CONFIG_ASSET_NAMESPACE).resolve("textures").resolve("entity").resolve("npc");
        mirrorConfigFiles(DialogueStorage.skinDirectory(), target, ".png");
    }

    private static void syncLang(Path packRoot) throws IOException {
        Path target = packRoot.resolve("assets").resolve(CONFIG_ASSET_NAMESPACE).resolve("lang");
        mirrorConfigFiles(DialogueStorage.langDirectory(), target, ".json");
    }

    private static void syncSounds(Path packRoot) throws IOException {
        Path target = packRoot.resolve("assets").resolve(CONFIG_ASSET_NAMESPACE).resolve("sounds").resolve("dialogue");
        List<String> sounds = mirrorConfigFiles(DialogueStorage.soundDirectory(), target, ".ogg");
        writeGeneratedSoundsJson(packRoot, sounds);
    }

    private static List<String> mirrorConfigFiles(Path source, Path target, String extension) throws IOException {
        List<String> copied = new ArrayList<>();
        deleteDirectoryContents(target);
        Files.createDirectories(target);
        if (!Files.isDirectory(source)) {
            return copied;
        }
        try (var stream = Files.walk(source, 8, FileVisitOption.FOLLOW_LINKS)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String relative = normalizeConfigAssetFile(source.relativize(file).toString(), "", extension, false);
                if (relative.isBlank() || !relative.endsWith(extension)) {
                    continue;
                }
                Path output = target.resolve(relative);
                Files.createDirectories(output.getParent());
                Files.copy(file, output, StandardCopyOption.REPLACE_EXISTING);
                copied.add(relative.substring(0, relative.length() - extension.length()));
            }
        }
        copied.sort(Comparator.naturalOrder());
        return copied;
    }

    private static void writeGeneratedSoundsJson(Path packRoot, List<String> sounds) throws IOException {
        Path soundsJson = packRoot.resolve("assets").resolve(CONFIG_ASSET_NAMESPACE).resolve("sounds.json");
        Files.createDirectories(soundsJson.getParent());
        JsonObject root = readExistingSoundsJson(soundsJson);
        for (String sound : sounds) {
            JsonObject entry = new JsonObject();
            JsonArray soundFiles = new JsonArray();
            soundFiles.add(CONFIG_ASSET_NAMESPACE + ":dialogue/" + sound);
            entry.add("sounds", soundFiles);
            root.add("dialogue/" + sound, entry);
        }
        Files.writeString(soundsJson, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static JsonObject readExistingSoundsJson(Path soundsJson) {
        if (!Files.isRegularFile(soundsJson)) {
            return new JsonObject();
        }
        try {
            JsonElement existing = JsonParser.parseString(Files.readString(soundsJson, StandardCharsets.UTF_8));
            if (!existing.isJsonObject()) {
                return new JsonObject();
            }
            JsonObject root = existing.getAsJsonObject();
            List<String> generatedKeys = root.entrySet().stream()
                    .map(Map.Entry::getKey)
                    .filter(key -> key.startsWith("dialogue/"))
                    .toList();
            for (String key : generatedKeys) {
                root.remove(key);
            }
            return root;
        } catch (IOException | RuntimeException ex) {
            AvielsDialogueMod.LOGGER.warn("Could not read existing sounds.json, regenerating it", ex);
            return new JsonObject();
        }
    }

    private static void deleteDirectoryContents(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            List<Path> paths = stream
                    .filter(path -> !path.equals(directory))
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }
}
