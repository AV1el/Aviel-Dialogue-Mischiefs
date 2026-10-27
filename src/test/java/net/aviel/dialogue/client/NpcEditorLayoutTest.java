package net.aviel.dialogue.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcEditorLayoutTest {
    @Test
    void paletteUsesMoreRowsOnNarrowScreens() {
        assertTrue(NpcDialogueColorControl.height(180) > NpcDialogueColorControl.height(500));
        assertEquals(44 + net.aviel.dialogue.npc.dialogue.DialoguePalette.KEYS.size() * 38, NpcDialogueColorControl.height(180));
    }
    @Test
    void editorFitsSmallAndLargeWindowsWithoutOverlappingRegions() {
        for (int[] viewport : new int[][]{{320, 240}, {426, 240}, {512, 320}, {960, 540}, {1920, 1080}}) {
            var layout = NpcEditorLayout.of(viewport[0], viewport[1]);
            assertTrue(layout.frame().x() >= 0);
            assertTrue(layout.frame().y() >= 0);
            assertTrue(layout.frame().x() + layout.frame().w() <= viewport[0]);
            assertTrue(layout.frame().y() + layout.frame().h() <= viewport[1]);
            assertTrue(layout.tabs().y() + layout.tabs().h() < layout.content().y());
            assertTrue(layout.preview().x() + layout.preview().w() < layout.content().x());
            assertTrue(layout.content().y() + layout.content().h() < layout.footer().y());
            assertTrue(layout.content().w() >= 180);
            assertTrue(layout.content().h() >= 100);
            assertTrue(layout.footer().y() + layout.footer().h() <= layout.frame().y() + layout.frame().h());
            int tabs = NpcEditorScreen.Category.values().length;
            assertEquals(5, tabs);
            assertTrue(tabs * ((layout.tabs().w() - (tabs - 1) * 3) / tabs) + (tabs - 1) * 3 <= layout.tabs().w());
        }
    }
}
