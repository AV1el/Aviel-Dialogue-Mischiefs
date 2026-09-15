package net.aviel.dialogue.npc.dialogue;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialogueTranslationTest {
    @Test
    void overlaysTextByNodeAndChoiceId() {
        NpcDialogueDefinition definition = NpcDialogueDefinition.fromJson("""
                {
                  "title": "Warden",
                  "speaker": "Guard",
                  "nodes": {
                    "start": {
                      "text": "Clear the road.",
                      "choices": [
                        { "id": "accept", "text": "I accept.", "close": true },
                        { "text": "Later.", "close": true }
                      ]
                    }
                  }
                }
                """);
        DialogueTranslation translation = DialogueTranslation.fromJson("""
                {
                  "title": "Смотритель",
                  "speaker": "Страж",
                  "nodes": {
                    "start": {
                      "text": ["Очисти дорогу.", "И возвращайся."],
                      "choices": {
                        "accept": "Я согласен.",
                        "1": "Позже."
                      }
                    }
                  }
                }
                """);

        NpcDialogueDefinition.Node node = definition.node("start");
        assertEquals("Смотритель", translation.titleOr(definition.title()));
        assertEquals("Страж", translation.speakerFor(definition, node));
        assertEquals(List.of("Очисти дорогу.", "И возвращайся."), translation.textFor(node));
        assertEquals("Я согласен.", translation.choiceTextFor(node, node.choices().get(0)));
        assertEquals("Позже.", translation.choiceTextFor(node, node.choices().get(1)));
    }

    @Test
    void missingEntriesFallBackToBaseDialogue() {
        NpcDialogueDefinition definition = NpcDialogueDefinition.fromJson("""
                {
                  "title": "Warden",
                  "nodes": {
                    "start": {
                      "text": "Base text",
                      "choices": [{ "text": "Base choice", "close": true }]
                    }
                  }
                }
                """);
        DialogueTranslation translation = DialogueTranslation.fromJson("{ \"title\": \"Смотритель\" }");
        NpcDialogueDefinition.Node node = definition.node("start");

        assertEquals(List.of("Base text"), translation.textFor(node));
        assertEquals("Base choice", translation.choiceTextFor(node, node.choices().get(0)));
    }
}
