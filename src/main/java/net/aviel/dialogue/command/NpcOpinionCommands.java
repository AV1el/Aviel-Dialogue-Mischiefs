package net.aviel.dialogue.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.aviel.dialogue.npc.social.OpinionData;
import net.aviel.dialogue.npc.social.OpinionReference;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class NpcOpinionCommands {
    private NpcOpinionCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        var player = Commands.argument("player", EntityArgument.player());
        player.then(Commands.literal("get").executes(context -> execute(context.getSource(), EntityArgument.getPlayer(context, "player"), "get", 0)));
        for (String action : java.util.List.of("add", "set")) {
            player.then(Commands.literal(action).then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
                    .executes(context -> execute(context.getSource(), EntityArgument.getPlayer(context, "player"), action,
                            IntegerArgumentType.getInteger(context, "value")))));
        }
        return Commands.literal("opinion").then(player);
    }

    private static int execute(CommandSourceStack source, ServerPlayer player, String action, int value) {
        var npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) return 0;
        String key = new OpinionReference("", "", "").resolve(npc);
        if (key.isEmpty()) {
            source.sendFailure(Component.literal("NPC has an unresolved opinion reference."));
            return 0;
        }
        OpinionData data = OpinionData.get(source.getServer());
        if (action.equals("set")) data.set(player.getUUID(), key, value);
        if (action.equals("add")) data.add(player.getUUID(), key, value);
        source.sendSuccess(() -> Component.literal(key + " / " + player.getGameProfile().getName() + " = " + data.score(player.getUUID(), key)), false);
        return 1;
    }
}
