package net.aviel.dialogue.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.entity.ai.NpcCompanion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class NpcCompanionCommands {
    private NpcCompanionCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        var target = Commands.argument("npc", EntityArgument.entity());
        for (NpcCompanion.Mode mode : NpcCompanion.Mode.values()) {
            var option = Commands.literal(mode.name().toLowerCase(java.util.Locale.ROOT))
                    .executes(context -> {
                        var entity = EntityArgument.getEntity(context, "npc");
                        ServerPlayer owner = context.getSource().getEntity() instanceof ServerPlayer player ? player : null;
                        return configure(context.getSource(), entity, mode, owner);
                    });
            if (mode == NpcCompanion.Mode.FOLLOW) {
                option.then(Commands.argument("owner", EntityArgument.player()).executes(context ->
                        configure(context.getSource(), EntityArgument.getEntity(context, "npc"), mode,
                                EntityArgument.getPlayer(context, "owner"))));
            }
            target.then(option);
        }
        target.then(Commands.literal("combat")
                .then(Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                        .executes(context -> {
                            if (!(EntityArgument.getEntity(context, "npc") instanceof DialogueNpcEntity npc)) {
                                context.getSource().sendFailure(Component.literal("Target must be an ADM NPC."));
                                return 0;
                            }
                            if (!authorize(context.getSource(), npc)) return 0;
                            claimForSource(context.getSource(), npc);
                            npc.companion().setCombat(com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled"));
                            context.getSource().sendSuccess(() -> Component.literal("NPC combat: " + npc.companion().combatEnabled()), true);
                            return 1;
                        })));
        target.then(Commands.literal("release").executes(context -> {
            if (!(EntityArgument.getEntity(context, "npc") instanceof DialogueNpcEntity npc)) {
                context.getSource().sendFailure(Component.literal("Target must be an ADM NPC."));
                return 0;
            }
            if (!authorize(context.getSource(), npc)) return 0;
            npc.companion().release();
            context.getSource().sendSuccess(() -> Component.literal("Companion released."), true);
            return 1;
        }));
        return Commands.literal("companion").then(target);
    }

    private static boolean authorize(CommandSourceStack source, DialogueNpcEntity npc) {
        if (source.getEntity() instanceof ServerPlayer player) {
            if (NpcCompanion.canControl(npc.companion().owner(), player.getUUID())
                    || player.hasPermissions(net.aviel.dialogue.Config.COMMAND_PERMISSION_LEVEL.get())) return true;
            source.sendFailure(Component.literal("This companion belongs to another player."));
            return false;
        }
        return true;
    }

    private static void claimForSource(CommandSourceStack source, DialogueNpcEntity npc) {
        if (source.getEntity() instanceof ServerPlayer player) npc.companion().claim(player.getUUID());
    }

    private static int configure(CommandSourceStack source, net.minecraft.world.entity.Entity entity,
                                 NpcCompanion.Mode mode, ServerPlayer owner) {
        if (!(entity instanceof DialogueNpcEntity npc)) {
            source.sendFailure(Component.literal("Target must be an ADM NPC."));
            return 0;
        }
        if (mode == NpcCompanion.Mode.FOLLOW && owner == null) {
            source.sendFailure(Component.literal("Specify a player to follow."));
            return 0;
        }
        if (!authorize(source, npc)) return 0;
        claimForSource(source, npc);
        npc.companion().configure(mode, owner == null ? null : owner.getUUID());
        source.sendSuccess(() -> Component.literal("NPC companion mode: " + mode), true);
        return 1;
    }
}
