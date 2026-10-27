package net.aviel.dialogue.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcStatsCommandsTest {
    @Test
    void acceptsStatEditsAndRejectsUnknownPolicies() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(NpcStatsCommands.subtree());
        for (String command : new String[]{"template guard stats", "template guard stats health 40", "template guard stats attack_speed 4",
                "template guard stats speed 0.35", "template guard stats policy defend_owner", "template guard apply"}) {
            var parsed = dispatcher.parse(command, null);
            assertFalse(parsed.getReader().canRead(), command);
            assertNotNull(parsed.getContext().getCommand(), command);
        }
        assertTrue(dispatcher.parse("template guard stats policy invalid", null).getReader().canRead());
        assertTrue(dispatcher.parse("template guard stats damage invalid", null).getReader().canRead());
    }
}
