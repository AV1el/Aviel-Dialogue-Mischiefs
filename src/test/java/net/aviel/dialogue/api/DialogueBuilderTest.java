package net.aviel.dialogue.api;

import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueBuilderTest {
    @Test
    void buildsParseableDialogue() {
        NpcDialogueDefinition definition = DialogueBuilder.create()
                .title("Intro")
                .speaker("Narrator")
                .start("start")
                .node("start", node -> node
                        .text("Welcome!", "Choose wisely.")
                        .choice(choice -> choice.text("Continue").next("end"))
                        .choice(choice -> choice.text("Leave").close()))
                .node("end", node -> node
                        .speaker("Guide")
                        .text("The end.")
                        .choice(choice -> choice
                                .id("finish")
                                .text("Done")
                                .setFlag("intro_done")
                                .requiresAdvancement("minecraft:story/root")
                                .requiresKills("minecraft:zombie", 2)
                                .condition(condition -> {
                                    condition.addProperty("type", "level");
                                    condition.addProperty("min", 5);
                                })
                                .giveItem("minecraft:bread", 2)
                                .close()))
                .build();

        assertEquals("Intro", definition.title());
        assertEquals("start", definition.startNode());
        assertEquals(List.of("Welcome!", "Choose wisely."), definition.node("start").text());
        assertEquals("Narrator", definition.node("start").speaker());
        assertEquals("Guide", definition.node("end").speaker());

        NpcDialogueDefinition.Choice finish = definition.node("end").choices().get(0);
        assertEquals("finish", finish.id());
        assertTrue(finish.close());
        assertEquals(List.of("intro_done"), finish.setFlags());
        assertEquals(List.of("minecraft:story/root"), finish.requiresAdvancements());
        assertEquals(new NpcDialogueDefinition.KillRule("minecraft:zombie", 2), finish.requiresKills().get(0));
        assertTrue(finish.condition() instanceof net.aviel.dialogue.npc.dialogue.DialogueCondition.Predicate);
        assertEquals("minecraft:bread", finish.giveItems().get(0).item());
        assertEquals(2, finish.giveItems().get(0).count());
    }

    @Test
    void customConditionTypesCannotReplaceBuiltIns() {
        assertThrows(IllegalArgumentException.class,
                () -> AdmDialogueApi.registerConditionType("flag", (player, target, condition) -> true));

        AdmDialogueApi.registerConditionType("mymod.reputation", (player, target, condition) -> true);
        AdmDialogueApi.unregisterConditionType("mymod.reputation");
    }
}
