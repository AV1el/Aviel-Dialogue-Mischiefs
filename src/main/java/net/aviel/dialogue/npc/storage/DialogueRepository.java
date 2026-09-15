package net.aviel.dialogue.npc.storage;

import com.google.gson.JsonSyntaxException;
import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.npc.dialogue.DialogueTranslation;
import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves and loads dialogues from the three supported sources: config-folder files
 * (plain names), datapack entries and API-registered dialogues (both {@code ns:path} ids).
 * Parsed definitions are cached; file entries invalidate by modification time.
 */
public final class DialogueRepository {
    private static final int MAX_DIALOGUE_JSON_CHARS = 30000;
    private static final Map<String, String> RUNTIME_DIALOGUES = new ConcurrentHashMap<>();
    private static final Map<String, String> RUNTIME_TRANSLATIONS = new ConcurrentHashMap<>();
    private static final Map<String, NpcDialogueDefinition> ID_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, DialogueTranslation> ID_TRANSLATION_CACHE = new ConcurrentHashMap<>();
    private static final DialogueFileCache<NpcDialogueDefinition> FILE_CACHE = new DialogueFileCache<>(MAX_DIALOGUE_JSON_CHARS);
    private static final DialogueFileCache<DialogueTranslation> TRANSLATION_FILE_CACHE = new DialogueFileCache<>(MAX_DIALOGUE_JSON_CHARS);

    private DialogueRepository() {
    }

    /** {@code true} when the reference points at a datapack or API-registered entry rather than a config file. */
    public static boolean isDataId(String reference) {
        return reference != null && reference.indexOf(':') >= 0;
    }

    /**
     * Accepts either a config-folder file name ({@code guard} / {@code guard.json}) or a
     * namespaced id ({@code mymod:npcs/guard}). Returns the canonical form, or {@code ""}.
     */
    public static String normalizeReference(String input) {
        if (input == null) {
            return "";
        }
        String raw = input.trim();
        if (raw.isBlank()) {
            return "";
        }
        if (raw.indexOf(':') >= 0) {
            String id = raw.replace('\\', '/');
            if (id.toLowerCase(Locale.ROOT).endsWith(".json")) {
                id = id.substring(0, id.length() - ".json".length());
            }
            ResourceLocation location = ResourceLocation.tryParse(id);
            return location == null ? "" : location.toString();
        }
        return normalizeFileName(raw);
    }

    public static String normalizeFileName(String input) {
        if (input == null) return "";
        String raw = input.trim();
        if (raw.isBlank()) return "";
        raw = raw.replace('\\', '/');
        if (raw.contains("/") || raw.contains("..")) return "";
        if (!raw.toLowerCase(Locale.ROOT).endsWith(".json")) {
            raw += ".json";
        }
        return raw.length() > 260 ? raw.substring(0, 260) : raw;
    }

    public static List<String> listDialogueFiles(MinecraftServer server) {
        DialogueStorage.ensureDirectories();
        Set<String> names = new LinkedHashSet<>();
        DialogueStorage.collectJsonFileNames(DialogueStorage.dialogueDirectory(), names);
        AdmDataPackManager.DIALOGUES.ids().stream()
                .filter(id -> !isTranslationDataId(id))
                .forEach(names::add);
        names.addAll(RUNTIME_DIALOGUES.keySet());
        List<String> result = new ArrayList<>(names);
        result.sort(Comparator.naturalOrder());
        return result;
    }

    public static String resolveDialogueFileName(MinecraftServer server, String input) {
        String requested = normalizeReference(input);
        if (requested.isBlank()) {
            return null;
        }
        if (isDataId(requested)) {
            boolean known = !isTranslationDataId(requested)
                    && (RUNTIME_DIALOGUES.containsKey(requested) || AdmDataPackManager.DIALOGUES.contains(requested));
            return known ? requested : null;
        }
        for (String file : listDialogueFiles(server)) {
            if (file.equalsIgnoreCase(requested)) {
                return file;
            }
        }
        return null;
    }

    public static String readDialogueJson(MinecraftServer server, String fileName) throws IOException {
        String normalized = normalizeReference(fileName);
        if (normalized.isBlank()) {
            throw new IOException("Invalid dialogue reference.");
        }
        if (isDataId(normalized)) {
            String runtime = RUNTIME_DIALOGUES.get(normalized);
            if (runtime != null) {
                return runtime;
            }
            String datapack = AdmDataPackManager.DIALOGUES.rawJson(normalized);
            if (datapack != null) {
                return datapack;
            }
            throw new IOException("Dialogue not found in datapacks or API registry: " + normalized);
        }
        Path path = existingDialogueFile(normalized);
        String json = Files.readString(path, StandardCharsets.UTF_8);
        if (json.length() > MAX_DIALOGUE_JSON_CHARS) {
            throw new IOException("Dialogue file is too large: " + normalized);
        }
        return json;
    }

    public static NpcDialogueDefinition loadDialogue(MinecraftServer server, String fileName) throws IOException, JsonSyntaxException {
        String normalized = normalizeReference(fileName);
        if (normalized.isBlank()) {
            throw new IOException("Invalid dialogue reference.");
        }
        if (isDataId(normalized)) {
            NpcDialogueDefinition cached = ID_CACHE.get(normalized);
            if (cached != null) {
                return cached;
            }
            NpcDialogueDefinition parsed = NpcDialogueDefinition.fromJson(readDialogueJson(server, normalized));
            ID_CACHE.put(normalized, parsed);
            return parsed;
        }
        return FILE_CACHE.get(existingDialogueFile(normalized), NpcDialogueDefinition::fromJson);
    }

    public static DialogueTranslation loadDialogueTranslation(
            MinecraftServer server,
            String fileName,
            String language
    ) throws IOException, JsonSyntaxException {
        String normalized = normalizeReference(fileName);
        if (normalized.isBlank()) {
            throw new IOException("Invalid dialogue reference.");
        }
        for (String candidate : languageCandidates(language)) {
            DialogueTranslation translation = isDataId(normalized)
                    ? loadDataTranslation(normalized, candidate)
                    : loadFileTranslation(normalized, candidate);
            if (translation != null) {
                return translation;
            }
        }
        return DialogueTranslation.EMPTY;
    }

    public static List<String> listDialogueTranslationLanguages(MinecraftServer server, String fileName) {
        String normalized = normalizeReference(fileName);
        if (normalized.isBlank()) {
            return List.of();
        }
        Set<String> languages = new LinkedHashSet<>();
        if (isDataId(normalized)) {
            String prefix = normalized + "|";
            RUNTIME_TRANSLATIONS.keySet().stream()
                    .filter(key -> key.startsWith(prefix))
                    .map(key -> key.substring(prefix.length()))
                    .forEach(languages::add);
            ResourceLocation base = ResourceLocation.tryParse(normalized);
            if (base != null) {
                String dataPrefix = "langs/";
                String dataSuffix = "/" + base.getPath();
                AdmDataPackManager.DIALOGUES.ids().stream()
                        .map(ResourceLocation::tryParse)
                        .filter(java.util.Objects::nonNull)
                        .filter(id -> id.getNamespace().equals(base.getNamespace()))
                        .map(ResourceLocation::getPath)
                        .filter(path -> path.startsWith(dataPrefix) && path.endsWith(dataSuffix))
                        .map(path -> path.substring(dataPrefix.length(), path.length() - dataSuffix.length()))
                        .filter(language -> !language.contains("/"))
                        .forEach(languages::add);
            }
        } else {
            Path root = DialogueStorage.dialogueLanguageDirectory();
            if (Files.isDirectory(root)) {
                try (var stream = Files.list(root)) {
                    stream.filter(Files::isDirectory)
                            .filter(directory -> Files.isRegularFile(directory.resolve(normalized)))
                            .map(directory -> normalizeLanguage(directory.getFileName().toString()))
                            .filter(language -> !language.isBlank())
                            .forEach(languages::add);
                } catch (IOException ex) {
                    AvielsDialogueMod.LOGGER.warn("Could not list dialogue translations in {}", root, ex);
                    return List.of();
                }
            }
        }
        return languages.stream().sorted().toList();
    }

    public static void registerRuntimeDialogue(String id, String json) {
        String normalized = normalizeReference(id);
        if (!isDataId(normalized)) {
            throw new IllegalArgumentException("Dialogue id must be namespaced, e.g. mymod:intro");
        }
        NpcDialogueDefinition.fromJson(json);
        RUNTIME_DIALOGUES.put(normalized, json);
        ID_CACHE.remove(normalized);
    }

    public static void unregisterRuntimeDialogue(String id) {
        String normalized = normalizeReference(id);
        RUNTIME_DIALOGUES.remove(normalized);
        String prefix = normalized + "|";
        RUNTIME_TRANSLATIONS.keySet().removeIf(key -> key.startsWith(prefix));
        ID_CACHE.remove(normalized);
        ID_TRANSLATION_CACHE.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public static void registerRuntimeDialogueTranslation(String id, String language, String json) {
        String normalized = normalizeReference(id);
        String normalizedLanguage = normalizeLanguage(language);
        if (!isDataId(normalized) || normalizedLanguage.isBlank()) {
            throw new IllegalArgumentException("Dialogue translation requires a namespaced id and language code.");
        }
        DialogueTranslation.fromJson(json);
        String key = translationCacheKey(normalized, normalizedLanguage);
        RUNTIME_TRANSLATIONS.put(key, json);
        ID_TRANSLATION_CACHE.remove(key);
    }

    public static void invalidateCaches() {
        FILE_CACHE.clear();
        TRANSLATION_FILE_CACHE.clear();
        ID_CACHE.clear();
        ID_TRANSLATION_CACHE.clear();
    }

    private static Path existingDialogueFile(String normalizedFileName) throws IOException {
        DialogueStorage.ensureDirectories();
        Path path = DialogueStorage.dialogueDirectory().resolve(normalizedFileName);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Dialogue file does not exist: " + normalizedFileName);
        }
        return path;
    }

    static String normalizeLanguage(String language) {
        if (language == null) {
            return "";
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.matches("[a-z0-9_]{2,32}") ? normalized : "";
    }

    static String localizedDataId(String dialogueId, String language) {
        ResourceLocation base = ResourceLocation.tryParse(dialogueId);
        String normalizedLanguage = normalizeLanguage(language);
        if (base == null || normalizedLanguage.isBlank()) {
            return "";
        }
        return ResourceLocation.fromNamespaceAndPath(
                base.getNamespace(),
                "langs/" + normalizedLanguage + "/" + base.getPath()
        ).toString();
    }

    private static DialogueTranslation loadDataTranslation(String dialogueId, String language) {
        String key = translationCacheKey(dialogueId, language);
        DialogueTranslation cached = ID_TRANSLATION_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        String raw = RUNTIME_TRANSLATIONS.get(key);
        if (raw == null) {
            raw = AdmDataPackManager.DIALOGUES.rawJson(localizedDataId(dialogueId, language));
        }
        if (raw == null) {
            return null;
        }
        DialogueTranslation parsed = DialogueTranslation.fromJson(raw);
        ID_TRANSLATION_CACHE.put(key, parsed);
        return parsed;
    }

    private static DialogueTranslation loadFileTranslation(String fileName, String language) throws IOException {
        Path path = DialogueStorage.dialogueLanguageDirectory().resolve(language).resolve(fileName).normalize();
        Path languageRoot = DialogueStorage.dialogueLanguageDirectory().resolve(language).normalize();
        if (!path.startsWith(languageRoot) || !Files.isRegularFile(path)) {
            return null;
        }
        return TRANSLATION_FILE_CACHE.get(path, DialogueTranslation::fromJson);
    }

    private static List<String> languageCandidates(String language) {
        String normalized = normalizeLanguage(language);
        if (normalized.isBlank()) {
            return List.of();
        }
        int separator = normalized.indexOf('_');
        return separator > 0
                ? List.of(normalized, normalized.substring(0, separator))
                : List.of(normalized);
    }

    private static boolean isTranslationDataId(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null && location.getPath().startsWith("langs/");
    }

    private static String translationCacheKey(String dialogueId, String language) {
        return dialogueId + "|" + language;
    }
}
