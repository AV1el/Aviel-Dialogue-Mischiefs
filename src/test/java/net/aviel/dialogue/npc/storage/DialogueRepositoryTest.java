package net.aviel.dialogue.npc.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueRepositoryTest {
    @Test
    void normalizesPlainFileNames() {
        assertEquals("guard.json", DialogueRepository.normalizeReference("guard"));
        assertEquals("guard.json", DialogueRepository.normalizeReference(" guard.json "));
        assertEquals("Guard.JSON", DialogueRepository.normalizeReference("Guard.JSON"));
    }

    @Test
    void normalizesNamespacedIds() {
        assertEquals("mymod:intro", DialogueRepository.normalizeReference("mymod:intro"));
        assertEquals("mymod:intro", DialogueRepository.normalizeReference("mymod:intro.json"));
        assertEquals("mymod:npcs/guard", DialogueRepository.normalizeReference("mymod:npcs/guard"));
    }

    @Test
    void rejectsTraversalAndInvalidInput() {
        assertEquals("", DialogueRepository.normalizeReference("../secrets"));
        assertEquals("", DialogueRepository.normalizeReference("sub/dir.json"));
        assertEquals("", DialogueRepository.normalizeReference("bad id:with spaces"));
        assertEquals("", DialogueRepository.normalizeReference(null));
        assertEquals("", DialogueRepository.normalizeReference("   "));
    }

    @Test
    void classifiesDataIds() {
        assertTrue(DialogueRepository.isDataId("mymod:intro"));
        assertFalse(DialogueRepository.isDataId("guard.json"));
        assertFalse(DialogueRepository.isDataId(null));
    }

    @Test
    void runtimeDialogueRegistrationValidatesIdAndJson() {
        String json = """
                { "nodes": { "start": { "text": "Hi" } } }
                """;
        DialogueRepository.registerRuntimeDialogue("testmod:intro", json);
        try {
            assertTrue(DialogueRepository.resolveDialogueFileName(null, "testmod:intro") != null);
        } finally {
            DialogueRepository.unregisterRuntimeDialogue("testmod:intro");
        }

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> DialogueRepository.registerRuntimeDialogue("no_namespace", json));
        org.junit.jupiter.api.Assertions.assertThrows(com.google.gson.JsonSyntaxException.class,
                () -> DialogueRepository.registerRuntimeDialogue("testmod:broken", "{}"));
    }
}
