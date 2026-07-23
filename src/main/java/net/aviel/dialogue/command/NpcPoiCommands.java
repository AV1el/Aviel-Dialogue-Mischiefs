package net.aviel.dialogue.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.item.PoiMarkerName;
import net.aviel.dialogue.npc.poi.MoveSpeed;
import net.aviel.dialogue.npc.poi.NpcMovementService;
import net.aviel.dialogue.npc.poi.NpcPoiData;
import net.aviel.dialogue.npc.poi.PointOfInterest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;

/** {@code /npc point} — manage the points NPCs walk to, and send NPCs to them. */
final class NpcPoiCommands {
    private NpcPoiCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("point")
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("here")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> here(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(ids(context.getSource()), builder))
                                .executes(context -> remove(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("marker")
                        .executes(context -> giveMarker(context.getSource(), ""))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> giveMarker(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("send")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(ids(context.getSource()), builder))
                                .executes(context -> send(context.getSource(), StringArgumentType.getString(context, "id"), "walk"))
                                .then(Commands.argument("speed", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(speedNames(), builder))
                                        .executes(context -> send(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                StringArgumentType.getString(context, "speed"))))))
                .then(Commands.literal("stop")
                        .executes(context -> stop(context.getSource())));
    }

    private static List<String> ids(CommandSourceStack source) {
        return NpcPoiData.get(source.getServer()).ids();
    }

    private static List<String> speedNames() {
        return Arrays.stream(MoveSpeed.values()).map(MoveSpeed::tagName).toList();
    }

    private static int list(CommandSourceStack source) {
        List<PointOfInterest> points = NpcPoiData.get(source.getServer()).all();
        if (points.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("message.adm.poi.list_empty"), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("message.adm.poi.list_header", points.size()), false);
        for (PointOfInterest point : points) {
            source.sendSuccess(() -> Component.literal("- " + point.describe()), false);
        }
        return points.size();
    }

    /** Drops a point at the command source's feet. */
    private static int here(CommandSourceStack source, String id) {
        BlockPos pos = BlockPos.containing(source.getPosition());
        PointOfInterest point = NpcPoiData.get(source.getServer())
                .put(id, pos, source.getLevel().dimension());
        if (point == null) {
            source.sendFailure(Component.translatable("message.adm.poi.bad_id", id));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("message.adm.poi.placed", point.id(), point.pos().toShortString()), true);
        return 1;
    }

    private static int remove(CommandSourceStack source, String id) {
        if (!NpcPoiData.get(source.getServer()).remove(id)) {
            source.sendFailure(Component.translatable("message.adm.poi.unknown", id));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("message.adm.poi.removed", id), true);
        return 1;
    }

    /** Hands the player a marker, optionally pre-named so every placement reuses that id. */
    private static int giveMarker(CommandSourceStack source, String id) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can receive a marker."));
            return 0;
        }

        ItemStack stack = new ItemStack(AvielsDialogueMod.POI_MARKER.get());
        PoiMarkerName.write(stack, id);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        source.sendSuccess(() -> Component.translatable("message.adm.poi.marker_given"), false);
        return 1;
    }

    private static int send(CommandSourceStack source, String id, String speed) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }

        if (!NpcMovementService.sendTo(source.getServer(), npc, id, MoveSpeed.parse(speed))) {
            source.sendFailure(Component.translatable("message.adm.poi.unreachable", id));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("message.adm.poi.sent", npc.getProfileName(), id), true);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        DialogueNpcEntity npc = NpcCommandTargets.requireTarget(source);
        if (npc == null) {
            return 0;
        }

        npc.stopWalking();
        source.sendSuccess(() -> Component.translatable("message.adm.poi.stopped", npc.getProfileName()), true);
        return 1;
    }
}
