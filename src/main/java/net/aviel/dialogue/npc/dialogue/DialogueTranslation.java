package net.aviel.dialogue.npc.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DialogueTranslation {
    public static final DialogueTranslation EMPTY = new DialogueTranslation("", "", Map.of());

    private final String title;
    private final String speaker;
    private final Map<String, NodeTranslation> nodes;

    public DialogueTranslation(String title, String speaker, Map<String, NodeTranslation> nodes) {
        this.title = clean(title, 80);
        this.speaker = clean(speaker, 80);
        this.nodes = Map.copyOf(nodes == null ? Map.of() : nodes);
    }

    public String title() {
        return title;
    }

    public String speaker() {
        return speaker;
    }

    public Map<String, NodeTranslation> nodes() {
        return nodes;
    }

    public static DialogueTranslation fromJson(String json) {
        JsonElement rootElement = JsonParser.parseString(json == null ? "" : json);
        if (!rootElement.isJsonObject()) {
            throw new JsonSyntaxException("Dialogue translation root must be a JSON object.");
        }
        JsonObject root = rootElement.getAsJsonObject();
        Map<String, NodeTranslation> nodes = new LinkedHashMap<>();
        JsonElement nodesElement = root.get("nodes");
        if (nodesElement != null && nodesElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : nodesElement.getAsJsonObject().entrySet()) {
                NodeTranslation node = readNode(entry.getValue());
                if (node != null) {
                    nodes.put(clean(entry.getKey(), 80), node);
                }
            }
        }
        return new DialogueTranslation(readString(root, "title"), readString(root, "speaker"), nodes);
    }

    public String titleOr(String fallback) {
        return title.isBlank() ? fallback : title;
    }

    public String speakerFor(NpcDialogueDefinition definition, NpcDialogueDefinition.Node node) {
        NodeTranslation translation = nodes.get(node.id());
        if (translation != null && !translation.speaker().isBlank()) {
            return translation.speaker();
        }
        if (!speaker.isBlank() && node.speaker().equals(definition.speaker())) {
            return speaker;
        }
        return node.speaker();
    }

    public List<String> textFor(NpcDialogueDefinition.Node node) {
        NodeTranslation translation = nodes.get(node.id());
        return translation == null || translation.text().isEmpty() ? node.text() : translation.text();
    }

    public String choiceTextFor(NpcDialogueDefinition.Node node, NpcDialogueDefinition.Choice choice) {
        NodeTranslation translation = nodes.get(node.id());
        if (translation == null) {
            return choice.text();
        }
        String byId = choice.id().isBlank() ? null : translation.choices().get(choice.id());
        if (byId != null && !byId.isBlank()) {
            return byId;
        }
        return translation.choices().getOrDefault(Integer.toString(choice.serverIndex()), choice.text());
    }

    private static NodeTranslation readNode(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        return new NodeTranslation(
                readString(object, "speaker"),
                readLines(object.get("text")),
                readChoices(object.get("choices"))
        );
    }

    private static List<String> readLines(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                String line = readText(value, 500);
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        } else {
            String line = readText(element, 1200);
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return List.copyOf(lines);
    }

    private static Map<String, String> readChoices(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Map.of();
        }
        Map<String, String> choices = new LinkedHashMap<>();
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                String text = readText(entry.getValue(), 140);
                if (!text.isBlank()) {
                    choices.put(clean(entry.getKey(), 80), text);
                }
            }
            return Map.copyOf(choices);
        }
        if (!element.isJsonArray()) {
            return Map.of();
        }
        JsonArray array = element.getAsJsonArray();
        for (int index = 0; index < array.size(); index++) {
            JsonElement value = array.get(index);
            String key = Integer.toString(index);
            if (value != null && value.isJsonObject()) {
                String id = readString(value.getAsJsonObject(), "id");
                if (!id.isBlank()) {
                    key = id;
                }
            }
            String text = readText(value, 140);
            if (!text.isBlank()) {
                choices.put(key, text);
            }
        }
        return Map.copyOf(choices);
    }

    private static String readText(JsonElement element, int maxLength) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String text = readString(object, "text");
            return clean(text.isBlank() ? readString(object, "label") : text, maxLength);
        }
        return element.isJsonPrimitive() ? clean(element.getAsString(), maxLength) : "";
    }

    private static String readString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        JsonElement value = object.get(key);
        return value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static String clean(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String result = value.replace('\r', '\n').trim();
        return result.length() > maxLength ? result.substring(0, maxLength) : result;
    }

    public record NodeTranslation(String speaker, List<String> text, Map<String, String> choices) {
        public NodeTranslation {
            speaker = clean(speaker, 80);
            text = List.copyOf(text == null ? List.of() : text);
            choices = Map.copyOf(choices == null ? Map.of() : choices);
        }
    }
}
