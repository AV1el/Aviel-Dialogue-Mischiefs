package net.aviel.dialogue.npc.storage;

import net.aviel.dialogue.AvielsDialogueMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Central place for every path under {@code config/adm-dialogues}.
 */
public final class DialogueStorage {
    private static final String ROOT_FOLDER = "adm-dialogues";
    private static final String DIALOGUE_FOLDER = "dialogues";
    private static final String TEMPLATE_FOLDER = "npc_templates";
    private static final String TRADE_FOLDER = "trades";
    private static final String EMOTE_FOLDER = "emotes";
    private static final String SKIN_FOLDER = "skins";
    private static final String SOUND_FOLDER = "sounds";
    private static final String LANG_FOLDER = "lang";
    private static final String RESOURCE_PACK_FOLDER = "resources";
    private static final String ENTITY_DIALOGUES_FILE = "entity_dialogues.json";

    private DialogueStorage() {
    }

    public static Path rootDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(ROOT_FOLDER);
    }

    public static Path dialogueDirectory() {
        return rootDirectory().resolve(DIALOGUE_FOLDER);
    }

    public static Path npcTemplateDirectory() {
        return rootDirectory().resolve(TEMPLATE_FOLDER);
    }

    public static Path tradeDirectory() {
        return rootDirectory().resolve(TRADE_FOLDER);
    }

    public static Path emoteDirectory() {
        return rootDirectory().resolve(EMOTE_FOLDER);
    }

    public static Path skinDirectory() {
        return rootDirectory().resolve(SKIN_FOLDER);
    }

    public static Path soundDirectory() {
        return rootDirectory().resolve(SOUND_FOLDER);
    }

    public static Path langDirectory() {
        return rootDirectory().resolve(LANG_FOLDER);
    }

    public static Path resourcePackDirectory() {
        return rootDirectory().resolve(RESOURCE_PACK_FOLDER);
    }

    public static Path entityDialogueConfigPath() {
        return rootDirectory().resolve(ENTITY_DIALOGUES_FILE);
    }

    public static void ensureDirectories() {
        createDirectory(dialogueDirectory());
        createDirectory(npcTemplateDirectory());
        createDirectory(tradeDirectory());
        createDirectory(emoteDirectory());
        createDirectory(skinDirectory());
        createDirectory(soundDirectory());
        createDirectory(langDirectory());
    }

    private static void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            AvielsDialogueMod.LOGGER.warn("Could not create ADM config directory {}", directory, ex);
        }
    }

    public static List<String> listSkinFiles() {
        List<String> result = new ArrayList<>();
        if (!Files.isDirectory(skinDirectory())) {
            return result;
        }
        try (var stream = Files.list(skinDirectory())) {
            stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(result::add);
        } catch (IOException ex) {
            AvielsDialogueMod.LOGGER.warn("Could not list skin files in {}", skinDirectory(), ex);
        }
        return result;
    }

    public static void collectJsonFileNames(Path directory, Set<String> names) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".json"))
                    .forEach(names::add);
        } catch (IOException ex) {
            AvielsDialogueMod.LOGGER.warn("Could not list JSON files in {}", directory, ex);
        }
    }
}
