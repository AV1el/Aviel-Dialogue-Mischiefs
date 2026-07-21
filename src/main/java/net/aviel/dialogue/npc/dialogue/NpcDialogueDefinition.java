package net.aviel.dialogue.npc.dialogue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.aviel.dialogue.npc.NpcDialogueService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NpcDialogueDefinition {
    public static final String DEFAULT_START_NODE = "start";
    public static final int DEFAULT_TEXT_SPEED = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String title;
    private final String startNode;
    private final List<String> startNodes;
    private final DialogueStyle style;
    private final Map<String, Node> nodes;

    private NpcDialogueDefinition(String title, List<String> startNodes, DialogueStyle style, Map<String, Node> nodes) {
        this.title = clean(title, 80);
        this.style = style == null ? DialogueStyle.DEFAULT : style;
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        List<String> validStarts = new ArrayList<>();
        if (startNodes != null) {
            for (String start : startNodes) {
                String cleanStart = clean(start, 80);
                if (!cleanStart.isBlank() && this.nodes.containsKey(cleanStart) && !validStarts.contains(cleanStart)) {
                    validStarts.add(cleanStart);
                }
            }
        }
        if (validStarts.isEmpty() && this.nodes.containsKey(DEFAULT_START_NODE)) {
            validStarts.add(DEFAULT_START_NODE);
        }
        if (validStarts.isEmpty()) {
            this.nodes.keySet().stream().findFirst().ifPresent(validStarts::add);
        }
        this.startNodes = List.copyOf(validStarts);
        this.startNode = this.startNodes.isEmpty() ? DEFAULT_START_NODE : this.startNodes.get(0);
    }

    public static NpcDialogueDefinition fromJson(String json) throws JsonSyntaxException {
        JsonElement rootElement = JsonParser.parseString(json == null ? "" : json);
        if (!rootElement.isJsonObject()) {
            throw new JsonSyntaxException("Dialogue root must be a JSON object.");
        }

        JsonObject root = rootElement.getAsJsonObject();
        String title = readString(root, "title", "");
        String rootSpeaker = readString(root, "speaker", "");
        int rootTextSpeed = clampTextSpeed(readInt(root, "text_speed", readInt(root, "default_text_speed", DEFAULT_TEXT_SPEED)));
        String rootTextColor = clean(readString(root, "text_color", ""), 16);
        String rootSpeakerColor = clean(readString(root, "speaker_color", ""), 16);
        DialogueStyle style = readStyle(root);
        DialogueSound rootSound = readSound(root, DialogueSound.DEFAULT);
        List<String> starts = readStartNodes(root);
        Map<String, Node> nodes = parseNodes(root.get("nodes"), rootSpeaker, rootTextSpeed, rootTextColor, rootSpeakerColor, rootSound);
        if (nodes.isEmpty()) {
            throw new JsonSyntaxException("Dialogue must contain at least one node.");
        }
        return new NpcDialogueDefinition(title, starts, style, nodes);
    }

    public String title() {
        return title;
    }

    public String startNode() {
        return startNode;
    }

    public List<String> startNodes() {
        return startNodes;
    }

    public Map<String, Node> nodes() {
        return nodes;
    }

    public DialogueStyle style() {
        return style;
    }

    public Node node(String id) {
        return nodes.get(id);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    private static Map<String, Node> parseNodes(
            JsonElement element,
            String rootSpeaker,
            int rootTextSpeed,
            String rootTextColor,
            String rootSpeakerColor,
            DialogueSound rootSound
    ) {
        Map<String, Node> parsed = new LinkedHashMap<>();
        if (element == null || element.isJsonNull()) {
            return parsed;
        }

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                Node node = parseNode(entry.getKey(), entry.getValue(), rootSpeaker, rootTextSpeed, rootTextColor, rootSpeakerColor, rootSound);
                if (node != null) {
                    parsed.put(node.id(), node);
                }
            }
            return parsed;
        }

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement nodeElement : array) {
                String id = "";
                if (nodeElement != null && nodeElement.isJsonObject()) {
                    id = readString(nodeElement.getAsJsonObject(), "id", "");
                }
                Node node = parseNode(id, nodeElement, rootSpeaker, rootTextSpeed, rootTextColor, rootSpeakerColor, rootSound);
                if (node != null) {
                    parsed.put(node.id(), node);
                }
            }
        }
        return parsed;
    }

    private static List<String> readStartNodes(JsonObject root) {
        List<String> starts = new ArrayList<>();
        addStartValues(starts, root.get("random_start"));
        addStartValues(starts, root.get("starts"));
        addStartValues(starts, root.get("start"));
        if (starts.isEmpty()) {
            starts.add(DEFAULT_START_NODE);
        }
        return starts;
    }

    private static void addStartValues(List<String> starts, JsonElement element) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                if (value == null || value.isJsonNull()) continue;
                String start = clean(value.getAsString(), 80);
                if (!start.isBlank()) {
                    starts.add(start);
                }
            }
            return;
        }
        String start = clean(element.getAsString(), 80);
        if (!start.isBlank()) {
            starts.add(start);
        }
    }

    private static Node parseNode(
            String id,
            JsonElement element,
            String rootSpeaker,
            int rootTextSpeed,
            String rootTextColor,
            String rootSpeakerColor,
            DialogueSound rootSound
    ) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        String cleanId = clean(id, 80);
        if (cleanId.isBlank()) {
            cleanId = clean(readString(object, "id", ""), 80);
        }
        if (cleanId.isBlank()) {
            return null;
        }

        String speaker = readString(object, "speaker", rootSpeaker);
        int textSpeed = clampTextSpeed(readInt(object, "text_speed", readInt(object, "speed", rootTextSpeed)));
        String textColor = clean(readString(object, "text_color", rootTextColor), 16);
        String speakerColor = clean(readString(object, "speaker_color", rootSpeakerColor), 16);
        DialogueSound sound = readSound(object, rootSound);
        List<String> text = readTextLines(object.get("text"));
        List<Choice> choices = readChoices(object.get("choices"));
        return new Node(cleanId, speaker, text, textSpeed, textColor, speakerColor, sound, choices);
    }

    private static List<String> readTextLines(JsonElement element) {
        List<String> lines = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return lines;
        }
        if (element.isJsonArray()) {
            for (JsonElement part : element.getAsJsonArray()) {
                if (part == null || part.isJsonNull()) continue;
                String line = clean(part.getAsString(), 500);
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        } else {
            String line = clean(element.getAsString(), 1200);
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static List<Choice> readChoices(JsonElement element) {
        List<Choice> choices = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return choices;
        }

        int index = 0;
        for (JsonElement choiceElement : element.getAsJsonArray()) {
            if (choiceElement == null || !choiceElement.isJsonObject()) {
                index++;
                continue;
            }
            JsonObject object = choiceElement.getAsJsonObject();
            String text = readString(object, "text", "");
            if (text.isBlank()) {
                text = readString(object, "label", "");
            }
            text = clean(text, 140);
            if (text.isBlank()) {
                continue;
            }
            String next = clean(readString(object, "next", ""), 80);
            String action = clean(readString(object, "action", ""), 80).toLowerCase(Locale.ROOT);
            boolean close = readBoolean(object, "close", false) || "close".equals(action) || "exit".equals(action);
            int serverIndex = readInt(object, "server_index", index);
            String trade = clean(readString(object, "trade", readString(object, "shop", readString(object, "open_trade", ""))), 260);
            choices.add(new Choice(
                    serverIndex,
                    clean(readString(object, "id", ""), 80),
                    text,
                    next,
                    close,
                    action,
                    trade,
                    readStringList(object, "commands", "command", 2000),
                    readStringList(object, "add_tags", "add_tag", 64),
                    readStringList(object, "remove_tags", "remove_tag", 64),
                    readStringList(object, "set_flags", "set_flag", 80),
                    readStringList(object, "add_flags", "add_flag", 80),
                    readStringList(object, "clear_flags", "clear_flag", 80),
                    readStringList(object, "remove_flags", "remove_flag", 80),
                    readStringList(object, "requires_flags", "requires_flag", 80),
                    readStringList(object, "missing_flags", "missing_flag", 80),
                    readStringList(object, "requires_missing_flags", "requires_missing_flag", 80),
                    readStringList(object, "requires_tags", "requires_tag", 64),
                    readStringList(object, "missing_tags", "missing_tag", 64),
                    readStringList(object, "requires_missing_tags", "requires_missing_tag", 64),
                    readStringList(object, "requires_choices", "requires_choice", 120),
                    readItemRules(object.get("requires_items")),
                    readItemRules(object.get("take_items")),
                    readItemRules(object.get("give_items"))
            ));
            index++;
        }
        return choices;
    }

    private static List<String> readStringList(JsonObject object, String arrayKey, String singleKey, int maxLength) {
        List<String> values = new ArrayList<>();
        if (object == null) {
            return values;
        }
        JsonElement arrayElement = object.get(arrayKey);
        if (arrayElement != null && arrayElement.isJsonArray()) {
            for (JsonElement valueElement : arrayElement.getAsJsonArray()) {
                if (valueElement == null || valueElement.isJsonNull()) continue;
                String value = clean(valueElement.getAsString(), maxLength);
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }

        String single = clean(readString(object, singleKey, ""), maxLength);
        if (!single.isBlank()) {
            values.add(single);
        }
        return values;
    }

    private static String readString(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float readFloat(JsonObject object, String key, float fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsFloat();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clampTextSpeed(int value) {
        return Math.max(0, Math.min(12, value));
    }

    private static DialogueSound readSound(JsonObject object, DialogueSound fallback) {
        if (fallback == null) {
            fallback = DialogueSound.DEFAULT;
        }
        JsonObject source = object;
        boolean nestedSound = object != null && object.has("sound") && object.get("sound").isJsonObject();
        if (nestedSound) {
            source = object.getAsJsonObject("sound");
        }
        String mode = normalizeSoundMode(readString(source, "mode", readString(object, "sound_mode", fallback.mode())));
        String textSound = nestedSound
                ? clean(readString(source, "text", readString(source, "text_sound", fallback.textSound())), 160)
                : clean(readString(object, "text_sound", fallback.textSound()), 160);
        if (textSound.isBlank()) {
            textSound = nestedSound
                    ? clean(readString(source, "sound", fallback.textSound()), 160)
                    : clean(readString(object, "sound", fallback.textSound()), 160);
        }
        String fullSound = nestedSound
                ? clean(readString(source, "full", readString(source, "full_sound", fallback.fullSound())), 160)
                : clean(readString(object, "full_sound", fallback.fullSound()), 160);
        if (fullSound.isBlank()) {
            fullSound = nestedSound
                    ? clean(readString(source, "voice", fallback.fullSound()), 160)
                    : clean(readString(object, "voice_sound", fallback.fullSound()), 160);
        }
        textSound = NpcDialogueService.normalizeSound(textSound);
        fullSound = NpcDialogueService.normalizeSound(fullSound);
        float volume = clampFloat(readFloat(source, "volume", readFloat(object, "sound_volume", fallback.volume())), 0.0F, 4.0F);
        float pitch = clampFloat(readFloat(source, "pitch", readFloat(object, "sound_pitch", fallback.pitch())), 0.1F, 4.0F);
        int interval = Math.max(1, Math.min(12, readInt(source, "letter_interval", readInt(object, "sound_interval", fallback.letterInterval()))));
        return new DialogueSound(mode, textSound, fullSound, volume, pitch, interval);
    }

    private static DialogueStyle readStyle(JsonObject root) {
        JsonObject source = root;
        if (root != null && root.has("style") && root.get("style").isJsonObject()) {
            source = root.getAsJsonObject("style");
        }
        DialogueStyle fallback = DialogueStyle.DEFAULT;
        return new DialogueStyle(
                clean(readString(source, "outer_color", readString(root, "outer_color", fallback.outerColor())), 16),
                clean(readString(source, "text_background", readString(root, "text_background", fallback.textBackground())), 16),
                clean(readString(source, "choices_background", readString(root, "choices_background", fallback.choicesBackground())), 16),
                clean(readString(source, "divider_color", readString(root, "divider_color", fallback.dividerColor())), 16),
                clean(readString(source, "button_color", readString(root, "button_color", fallback.buttonColor())), 16),
                clean(readString(source, "button_hover_color", readString(root, "button_hover_color", fallback.buttonHoverColor())), 16),
                clean(readString(source, "button_disabled_color", readString(root, "button_disabled_color", fallback.buttonDisabledColor())), 16),
                clean(readString(source, "button_text_color", readString(root, "button_text_color", fallback.buttonTextColor())), 16),
                clean(readString(source, "button_disabled_text_color", readString(root, "button_disabled_text_color", fallback.buttonDisabledTextColor())), 16)
        );
    }

    private static String normalizeSoundMode(String mode) {
        String value = clean(mode, 32).toLowerCase(Locale.ROOT);
        if ("repeat".equals(value)) {
            return "repeating";
        }
        if ("off".equals(value)) {
            return "none";
        }
        if ("full".equals(value) || "repeating".equals(value) || "none".equals(value)) {
            return value;
        }
        return "none";
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<ItemRule> readItemRules(JsonElement element) {
        List<ItemRule> rules = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return rules;
        }
        if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                ItemRule rule = readItemRule(value);
                if (rule != null) {
                    rules.add(rule);
                }
            }
            return rules;
        }
        ItemRule rule = readItemRule(element);
        if (rule != null) {
            rules.add(rule);
        }
        return rules;
    }

    private static ItemRule readItemRule(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            String item = clean(element.getAsString(), 160);
            return item.isBlank() ? null : new ItemRule(item, 1);
        }
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        String item = clean(readString(object, "item", ""), 160);
        if (item.isBlank()) {
            item = clean(readString(object, "id", ""), 160);
        }
        if (item.isBlank()) {
            return null;
        }
        int count = Math.max(1, readInt(object, "count", 1));
        return new ItemRule(item, count);
    }

    private static String clean(String value, int maxLength) {
        if (value == null) return "";
        String clean = value.replace('\r', '\n').trim();
        while (clean.contains("\n\n\n")) {
            clean = clean.replace("\n\n\n", "\n\n");
        }
        return clean.length() > maxLength ? clean.substring(0, maxLength) : clean;
    }

    public record Node(
            String id,
            String speaker,
            List<String> text,
            int textSpeed,
            String textColor,
            String speakerColor,
            DialogueSound sound,
            List<Choice> choices
    ) {
        public Node {
            id = clean(id, 80);
            speaker = clean(speaker, 80);
            text = List.copyOf(text == null ? List.of() : text);
            textSpeed = clampTextSpeed(textSpeed);
            textColor = clean(textColor, 16);
            speakerColor = clean(speakerColor, 16);
            sound = sound == null ? DialogueSound.DEFAULT : sound;
            choices = List.copyOf(choices == null ? List.of() : choices);
        }
    }

    public record DialogueSound(String mode, String textSound, String fullSound, float volume, float pitch, int letterInterval) {
        public static final DialogueSound DEFAULT = new DialogueSound("none", "", "", 0.6F, 1.0F, 1);

        public DialogueSound {
            mode = normalizeSoundMode(mode);
            textSound = clean(textSound, 160);
            fullSound = clean(fullSound, 160);
            volume = clampFloat(volume, 0.0F, 4.0F);
            pitch = clampFloat(pitch, 0.1F, 4.0F);
            letterInterval = Math.max(1, Math.min(12, letterInterval));
        }
    }

    public record DialogueStyle(
            String outerColor,
            String textBackground,
            String choicesBackground,
            String dividerColor,
            String buttonColor,
            String buttonHoverColor,
            String buttonDisabledColor,
            String buttonTextColor,
            String buttonDisabledTextColor
    ) {
        public static final DialogueStyle DEFAULT = new DialogueStyle(
                "#66000000",
                "#F60F0817",
                "#F6160D22",
                "#FFA986EC",
                "#FF1B1026",
                "#FF3C2A55",
                "#FF241732",
                "#FFF2E7FF",
                "#FF6A5F78"
        );

        public DialogueStyle {
            outerColor = clean(outerColor, 16);
            textBackground = clean(textBackground, 16);
            choicesBackground = clean(choicesBackground, 16);
            dividerColor = clean(dividerColor, 16);
            buttonColor = clean(buttonColor, 16);
            buttonHoverColor = clean(buttonHoverColor, 16);
            buttonDisabledColor = clean(buttonDisabledColor, 16);
            buttonTextColor = clean(buttonTextColor, 16);
            buttonDisabledTextColor = clean(buttonDisabledTextColor, 16);
        }
    }

    public record Choice(
            int serverIndex,
            String id,
            String text,
            String next,
            boolean close,
            String action,
            String trade,
            List<String> commands,
            List<String> addTags,
            List<String> removeTags,
            List<String> setFlags,
            List<String> addFlags,
            List<String> clearFlags,
            List<String> removeFlags,
            List<String> requiresFlags,
            List<String> missingFlags,
            List<String> requiresMissingFlags,
            List<String> requiresTags,
            List<String> missingTags,
            List<String> requiresMissingTags,
            List<String> requiresChoices,
            List<ItemRule> requiresItems,
            List<ItemRule> takeItems,
            List<ItemRule> giveItems
    ) {
        public Choice {
            serverIndex = Math.max(0, serverIndex);
            id = clean(id, 80);
            text = clean(text, 140);
            next = clean(next, 80);
            action = clean(action, 80).toLowerCase(Locale.ROOT);
            trade = clean(trade, 260);
            commands = List.copyOf(commands == null ? List.of() : commands);
            addTags = List.copyOf(addTags == null ? List.of() : addTags);
            removeTags = List.copyOf(removeTags == null ? List.of() : removeTags);
            setFlags = List.copyOf(setFlags == null ? List.of() : setFlags);
            addFlags = List.copyOf(addFlags == null ? List.of() : addFlags);
            clearFlags = List.copyOf(clearFlags == null ? List.of() : clearFlags);
            removeFlags = List.copyOf(removeFlags == null ? List.of() : removeFlags);
            requiresFlags = List.copyOf(requiresFlags == null ? List.of() : requiresFlags);
            missingFlags = List.copyOf(missingFlags == null ? List.of() : missingFlags);
            requiresMissingFlags = List.copyOf(requiresMissingFlags == null ? List.of() : requiresMissingFlags);
            requiresTags = List.copyOf(requiresTags == null ? List.of() : requiresTags);
            missingTags = List.copyOf(missingTags == null ? List.of() : missingTags);
            requiresMissingTags = List.copyOf(requiresMissingTags == null ? List.of() : requiresMissingTags);
            requiresChoices = List.copyOf(requiresChoices == null ? List.of() : requiresChoices);
            requiresItems = List.copyOf(requiresItems == null ? List.of() : requiresItems);
            takeItems = List.copyOf(takeItems == null ? List.of() : takeItems);
            giveItems = List.copyOf(giveItems == null ? List.of() : giveItems);
        }
    }

    public record ItemRule(String item, int count) {
        public ItemRule {
            item = clean(item, 160);
            count = Math.max(1, count);
        }
    }
}
