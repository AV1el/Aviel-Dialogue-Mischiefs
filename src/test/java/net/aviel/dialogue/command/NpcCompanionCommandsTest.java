package net.aviel.dialogue.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcCompanionCommandsTest {
    @Test
    void acceptsExplicitNpcAndOwnerForDialogueCommands() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(NpcCompanionCommands.subtree());
        String npc = "12345678-1234-1234-1234-123456789abc";
        for (String action : new String[]{"follow Steve", "guard", "idle", "combat true", "combat false", "release"}) {
            var parsed = dispatcher.parse("companion " + npc + " " + action, null);
            assertFalse(parsed.getReader().canRead(), action);
            assertTrue(parsed.getExceptions().isEmpty(), action);
            assertNotNull(parsed.getContext().getCommand(), action);
        }
        assertTrue(dispatcher.parse("companion " + npc + " combat sometimes", null).getReader().canRead());
    }
}
