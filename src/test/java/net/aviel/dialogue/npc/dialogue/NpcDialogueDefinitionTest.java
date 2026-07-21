package net.aviel.dialogue.npc.dialogue;

import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcDialogueDefinitionTest {
    private static final String BASIC_DIALOGUE = """
            {
              "title": "Guard",
              "speaker": "Guard",
              "text_speed": 3,
              "nodes": {
                "start": {
                  "text": "Halt!",
                  "choices": [
                    { "text": "Who are you?", "next": "who" },
                    { "text": "Bye.", "close": true }
                  ]
                },
                "who": {
                  "speaker": "Captain",
                  "text": ["I guard this town.", "Move along."],
                  "choices": [ { "text": "Back", "action": "back" } ]
                }
              }
            }
            """;

    @Test
    void parsesNodesChoicesAndInheritance() {
        NpcDialogueDefinition definition = NpcDialogueDefinition.fromJson(BASIC_DIALOGUE);

        assertEquals("Guard", definition.title());
        assertEquals("start", definition.startNode());
        assertEquals(2, definition.nodes().size());

        NpcDialogueDefinition.Node start = definition.node("start");
        assertNotNull(start);
        assertEquals("Guard", start.speaker());
        assertEquals(3, start.textSpeed());
        assertEquals(2, start.choices().size());
        assertEquals("who", start.choices().get(0).next());
        assertTrue(start.choices().get(1).close());

        NpcDialogueDefinition.Node who = definition.node("who");
        assertNotNull(who);
        assertEquals("Captain", who.speaker());
        assertEquals(List.of("I guard this town.", "Move along."), who.text());
        assertEquals("back", who.choices().get(0).action());
    }

    @Test
    void randomStartKeepsOnlyExistingNodes() {
        String json = """
                {
                  "random_start": ["missing", "b", "a", "b"],
                  "nodes": {
                    "a": { "text": "A" },
                    "b": { "text": "B" }
                  }
                }
                """;
        NpcDialogueDefinition definition = NpcDialogueDefinition.fromJson(json);
        assertEquals(List.of("b", "a"), definition.startNodes());
    }

    @Test
    void fallsBackToFirstNodeWhenStartMissing() {
        String json = """
                { "nodes": { "intro": { "text": "Hi" } } }
                """;
        NpcDialogueDefinition definition = NpcDialogueDefinition.fromJson(json);
        assertEquals("intro", definition.startNode());
    }

    @Test
    void choiceFlagAndItemFieldsAreParsed() {
        String json = """
                {
                  "nodes": {
                    "start": {
                      "text": "Quest?",
                      "choices": [
                        {
                          "text": "Done!",
                          "requires_flags": ["quest_started"],
                          "set_flags": ["quest_done"],
                          "take_items": [ { "item": "minecraft:iron_ingot", "count": 3 } ],
                          "give_items": [ "minecraft:diamond" ]
                        }
                      ]
                    }
                  }
                }
                """;
        NpcDialogueDefinition.Choice choice = NpcDialogueDefinition.fromJson(json).node("start").choices().get(0);
        assertEquals(List.of("quest_started"), choice.requiresFlags());
        assertEquals(List.of("quest_done"), choice.setFlags());
        assertEquals(1, choice.takeItems().size());
        assertEquals("minecraft:iron_ingot", choice.takeItems().get(0).item());
        assertEquals(3, choice.takeItems().get(0).count());
        assertEquals(1, choice.giveItems().get(0).count());
    }

    @Test
    void choicesWithoutTextAreDropped() {
        String json = """
                {
                  "nodes": {
                    "start": {
                      "text": "Hi",
                      "choices": [ { "next": "start" }, { "text": "Ok", "close": true } ]
                    }
                  }
                }
                """;
        NpcDialogueDefinition definition = NpcDialogueDefinition.fromJson(json);
        assertEquals(1, definition.node("start").choices().size());
    }

    @Test
    void rejectsNonObjectRoot() {
        assertThrows(JsonSyntaxException.class, () -> NpcDialogueDefinition.fromJson("[1, 2]"));
    }

    @Test
    void rejectsDialogueWithoutNodes() {
        assertThrows(JsonSyntaxException.class, () -> NpcDialogueDefinition.fromJson("{ \"title\": \"Empty\" }"));
    }

    @Test
    void unknownNodeLookupReturnsNull() {
        NpcDialogueDefinition definition = NpcDialogueDefinition.fromJson(BASIC_DIALOGUE);
        assertNull(definition.node("nope"));
        assertFalse(definition.nodes().containsKey("nope"));
    }
}
