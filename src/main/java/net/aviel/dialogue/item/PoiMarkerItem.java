package net.aviel.dialogue.item;

import net.aviel.dialogue.npc.poi.NpcPoiData;
import net.aviel.dialogue.npc.poi.PointOfInterest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Places the points NPCs walk to. Right-click a block to drop a point there, sneak
 * right-click to remove the nearest one, and right-click in the air to list them.
 *
 * <p>The id is stored on the stack, so a builder can name a marker once and then place a
 * whole route with it. With no id set the next free {@code poi_N} is used.</p>
 */
public class PoiMarkerItem extends Item {
    /** How far the remove mode looks for a point, in blocks. */
    private static final double REMOVE_RADIUS = 6.0D;

    public PoiMarkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }

        NpcPoiData data = NpcPoiData.get(player.server);
        BlockPos target = context.getClickedPos().relative(context.getClickedFace());

        if (player.isShiftKeyDown()) {
            return removeNearest(player, data, target);
        }

        String requestedId = PoiMarkerName.read(context.getItemInHand());
        String id = requestedId.isBlank() ? data.nextFreeId() : requestedId;
        PointOfInterest point = data.put(id, target, player.level().dimension());
        if (point == null) {
            player.displayClientMessage(Component.translatable("message.adm.poi.bad_id", id), true);
            return InteractionResult.FAIL;
        }

        player.displayClientMessage(Component.translatable("message.adm.poi.placed", point.id(), point.pos().toShortString()), true);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult removeNearest(ServerPlayer player, NpcPoiData data, BlockPos around) {
        PointOfInterest nearest = data.nearest(around, player.level().dimension(), REMOVE_RADIUS);
        if (nearest == null) {
            player.displayClientMessage(Component.translatable("message.adm.poi.none_nearby"), true);
            return InteractionResult.FAIL;
        }

        data.remove(nearest.id());
        player.displayClientMessage(Component.translatable("message.adm.poi.removed", nearest.id()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        List<PointOfInterest> points = NpcPoiData.get(serverPlayer.server).all();
        if (points.isEmpty()) {
            serverPlayer.displayClientMessage(Component.translatable("message.adm.poi.list_empty"), false);
            return InteractionResultHolder.success(stack);
        }

        serverPlayer.displayClientMessage(Component.translatable("message.adm.poi.list_header", points.size()), false);
        for (PointOfInterest point : points) {
            serverPlayer.displayClientMessage(Component.literal("- " + point.describe()), false);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String id = PoiMarkerName.read(stack);
        lines.add(id.isBlank()
                ? Component.translatable("item.adm.poi_marker.tip.auto")
                : Component.translatable("item.adm.poi_marker.tip.named", id));
        lines.add(Component.translatable("item.adm.poi_marker.tip.usage"));
    }
}
