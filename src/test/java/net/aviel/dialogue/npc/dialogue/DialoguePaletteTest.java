package net.aviel.dialogue.npc.dialogue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DialoguePaletteTest {
    @Test
    void fullPaletteOverridesEveryChannelWithoutChangingSourceDefinition() {
        var root = JsonParser.parseString("""
                {"style":{"text_background":"#222222"},
                 "nodes":{"start":{"text":"Hello","text_color":"#111111"},"other":{"speaker_color":"#333333"}}}
                """).getAsJsonObject();
        var original = root.deepCopy();
        var colors = new com.google.gson.JsonObject();
        for (String key : DialoguePalette.KEYS) colors.addProperty(key, "#FE123456");
        DialoguePalette.apply(root, colors.toString());
        for (String key : DialoguePalette.KEYS) {
            if (key.equals("text_color") || key.equals("speaker_color")) {
                for (var node : root.getAsJsonObject("nodes").entrySet()) {
                    assertEquals("#FE123456", node.getValue().getAsJsonObject().get(key).getAsString());
                }
            } else {
                assertEquals("#FE123456", root.getAsJsonObject("style").get(key).getAsString());
            }
        }
        assertEquals("#222222", original.getAsJsonObject("style").get("text_background").getAsString());
    }

    @Test
    void extendedStyleSurvivesDefinitionAndClientSerialization() {
        var definition = NpcDialogueDefinition.fromJson("""
                {"style":{"border_color":"#ABCDEF","button_hover_text_color":"#FEDCBA","header_background":"#222222"},
                 "nodes":{"start":{"text":"Hello","choices":[]}}}
                """);
        assertEquals("#ABCDEF", definition.style().color("border_color", ""));
        String client = net.aviel.dialogue.npc.DialogueClientJson.toClientJson(definition, null, null);
        var restored = NpcDialogueDefinition.fromJson(client);
        assertEquals(definition.style(), restored.style());
        assertEquals("fallback", restored.style().color("error_color", "fallback"));
    }

    @Test
    void migratesLegacyBackgroundAndRejectsUnsupportedOrOversizedPalettes() {
        var migrated = DialoguePalette.read("#f6123456");
        assertEquals(2, migrated.size());
        assertEquals("#F6123456", migrated.get("text_background").getAsString());
        assertEquals(migrated, DialoguePalette.read(DialoguePalette.normalizePalette(migrated.toString())));
        assertTrue(DialoguePalette.read("{\"text_color\":\"\"}").isEmpty());
        for (String invalid : new String[]{"{\"unknown\":\"#123456\"}", "{\"text_color\":42}",
                "{\"text_color\":null}", "{\"text_color\":\"bad\"}", " ".repeat(2050) + "{}"}) {
            assertThrows(RuntimeException.class, () -> DialoguePalette.read(invalid));
        }
    }

    @Test
    void validatesHexWithoutSilentlyAcceptingInvalidColors() {
        assertEquals("#12ABCD", DialoguePalette.normalize(" #12abcd "));
        assertEquals("#F612ABCD", DialoguePalette.normalize("#f612abcd"));
        assertEquals("", DialoguePalette.normalize(" "));
        for (String invalid : new String[]{"#12345", "#1234567", "#GG0000", "red", "123456"}) {
            assertThrows(IllegalArgumentException.class, () -> DialoguePalette.normalize(invalid));
        }
    }

    @Test
    void overrideChangesOnlyPanelBackgroundsAndEmptyValueKeepsDialogueStyle() {
        var root = JsonParser.parseString("""
                {"style":{"divider_color":"#ABCDEF","text_background":"#222222"}}
                """).getAsJsonObject();
        var original = root.deepCopy();
        DialoguePalette.apply(root, "");
        assertEquals(original, root);
        DialoguePalette.apply(root, "#F6123456");
        assertEquals("#F6123456", root.getAsJsonObject("style").get("text_background").getAsString());
        assertEquals("#F6123456", root.getAsJsonObject("style").get("choices_background").getAsString());
        assertEquals("#ABCDEF", root.getAsJsonObject("style").get("divider_color").getAsString());
    }
}
