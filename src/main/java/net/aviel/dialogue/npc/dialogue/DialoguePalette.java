package net.aviel.dialogue.npc.dialogue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Locale;

public final class DialoguePalette {
    public static final List<String> EXTRA_KEYS = List.of("border_color", "header_background", "npc_name_color",
            "button_border_color", "button_hover_border_color", "button_hover_text_color", "inactive_color",
            "error_color", "scroll_track_color", "scroll_thumb_color", "typing_indicator_color");
    public static final List<String> KEYS = java.util.stream.Stream.concat(
            List.of("text_background", "choices_background", "outer_color", "divider_color", "text_color", "speaker_color",
                    "button_color", "button_hover_color", "button_disabled_color", "button_text_color", "button_disabled_text_color").stream(),
            EXTRA_KEYS.stream()).toList();
    private DialoguePalette() { }

    public static String normalize(String value) {
        String color = value == null ? "" : value.trim();
        if (color.isEmpty()) return "";
        if (!color.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
            throw new IllegalArgumentException("Expected #RRGGBB or #AARRGGBB");
        }
        return color.toUpperCase(Locale.ROOT);
    }

    public static String normalizePalette(String value) {
        return read(value).toString();
    }

    public static JsonObject read(String value) {
        JsonObject colors = new JsonObject();
        if (value == null || value.isBlank()) return colors;
        if (value.trim().startsWith("#")) {
            String color = normalize(value);
            colors.addProperty("text_background", color);
            colors.addProperty("choices_background", color);
            return colors;
        }
        if (value.length() > 2048) throw new IllegalArgumentException("Dialogue palette is too large");
        JsonObject source = JsonParser.parseString(value).getAsJsonObject();
        for (var entry : source.entrySet()) {
            if (!KEYS.contains(entry.getKey())) throw new IllegalArgumentException("Unknown dialogue color: " + entry.getKey());
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Dialogue colors must be HEX strings");
            }
            String color = normalize(entry.getValue().getAsString());
            if (!color.isEmpty()) colors.addProperty(entry.getKey(), color);
        }
        return colors;
    }

    public static void apply(JsonObject root, String value) {
        JsonObject colors = read(value);
        JsonObject style = root.has("style") ? root.getAsJsonObject("style") : new JsonObject();
        for (var entry : colors.entrySet()) {
            String key = entry.getKey();
            if (key.equals("text_color") || key.equals("speaker_color")) {
                if (root.has("nodes")) {
                    for (var node : root.getAsJsonObject("nodes").entrySet()) node.getValue().getAsJsonObject().add(key, entry.getValue());
                }
            } else {
                style.add(key, entry.getValue());
                root.add("style", style);
            }
        }
    }
}
