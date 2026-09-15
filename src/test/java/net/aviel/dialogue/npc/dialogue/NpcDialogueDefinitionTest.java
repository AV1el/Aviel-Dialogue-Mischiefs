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
    void advancementAndKillConditionsAreParsed() {
        String json = """
                {
                  "nodes": {
                    "start": {
                      "text": "Quest?",
                      "choices": [
                        {
                          "text": "I did it.",
                          "requires_advancements": ["minecraft:adventure/kill_a_mob"],
                          "missing_advancement": "example:not_yet",
                          "requires_kills": [
                            { "entity": "minecraft:zombie", "count": 4 },
                            "minecraft:skeleton"
                          ],
                          "requires_kill": { "mob": "minecraft:creeper", "count": 2 }
                        }
                      ]
                    }
                  }
                }
                """;

        NpcDialogueDefinition.Choice choice = NpcDialogueDefinition.fromJson(json).node("start").choices().get(0);
        assertEquals(List.of("minecraft:adventure/kill_a_mob"), choice.requiresAdvancements());
        assertEquals(List.of("example:not_yet"), choice.missingAdvancements());
        assertEquals(3, choice.requiresKills().size());
        assertEquals(new NpcDialogueDefinition.KillRule("minecraft:zombie", 4), choice.requiresKills().get(0));
        assertEquals(new NpcDialogueDefinition.KillRule("minecraft:skeleton", 1), choice.requiresKills().get(1));
        assertEquals(new NpcDialogueDefinition.KillRule("minecraft:creeper", 2), choice.requiresKills().get(2));
    }

    @Test
    void parsesNestedConditionTree() {
        String json = """
                {
                  "nodes": {
                    "start": {
                      "text": "Quest?",
                      "choices": [
                        {
                          "text": "Done",
                          "condition": {
                            "all": [
                              { "type": "advancement", "id": "minecraft:adventure/kill_a_mob" },
                              {
                                "any": [
                                  { "type": "kills", "entity": "minecraft:zombie", "count": 3 },
                                  { "type": "level", "min": 10 }
                                ]
                              },
                              { "not": { "type": "dimension", "id": "minecraft:the_nether" } }
                            ]
                          },
                          "close": true
                        }
                      ]
                    }
                  }
                }
                """;

        DialogueCondition condition = NpcDialogueDefinition.fromJson(json).node("start").choices().get(0).condition();
        DialogueCondition.All all = (DialogueCondition.All) condition;
        assertEquals(3, all.conditions().size());
        assertTrue(all.conditions().get(0) instanceof DialogueCondition.Predicate);
        assertTrue(all.conditions().get(1) instanceof DialogueCondition.Any);
        assertTrue(all.conditions().get(2) instanceof DialogueCondition.Not);
        DialogueCondition.Any any = (DialogueCondition.Any) all.conditions().get(1);
        DialogueCondition.Predicate kills = (DialogueCondition.Predicate) any.conditions().get(0);
        assertEquals("kills", kills.type());
        assertEquals("minecraft:zombie", kills.id());
        assertEquals(3, kills.count());
    }

    @Test
    void preservesCustomConditionArguments() {
        String json = """
                {
                  "nodes": {
                    "start": {
                      "text": "Quest?",
                      "choices": [{
                        "text": "Ask",
                        "condition": {
                          "type": "mymod.reputation",
                          "faction": "mages",
                          "threshold": 12
                        }
                      }]
                    }
                  }
                }
                """;

        DialogueCondition.Predicate condition = (DialogueCondition.Predicate) NpcDialogueDefinition
                .fromJson(json)
                .node("start")
                .choices()
                .get(0)
                .condition();
        assertEquals("mages", condition.stringArgument("faction", ""));
        assertEquals(12.0D, condition.numberArgument("threshold", 0.0D));
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
