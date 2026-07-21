package net.aviel.dialogue.api;

import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals("minecraft:bread", finish.giveItems().get(0).item());
        assertEquals(2, finish.giveItems().get(0).count());
    }
}
