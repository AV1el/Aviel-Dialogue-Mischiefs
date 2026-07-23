package net.aviel.dialogue.network;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.Config;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.npc.DialogueSessionManager;
import net.aviel.dialogue.npc.poi.MoveSpeed;
import net.aviel.dialogue.npc.poi.NpcMovementService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Sent when a {@code <moveto:speed:point>} tag is reached while the dialogue types out. The
 * server decides whether the NPC actually moves: the client only reports that the tag fired.
 */
public record NpcMoveToPacket(UUID npcUuid, String poiId, String speed) implements CustomPacketPayload {
    public static final Type<NpcMoveToPacket> TYPE = new Type<>(AvielsDialogueMod.id("npc_move_to"));
    public static final StreamCodec<FriendlyByteBuf, NpcMoveToPacket> STREAM_CODEC =
            StreamCodec.ofMember(NpcMoveToPacket::write, NpcMoveToPacket::read);

    private static NpcMoveToPacket read(FriendlyByteBuf buf) {
        return new NpcMoveToPacket(buf.readUUID(), buf.readUtf(64), buf.readUtf(16));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.npcUuid);
        buf.writeUtf(this.poiId, 64);
        buf.writeUtf(this.speed, 16);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NpcMoveToPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                handleOnServer(packet, player);
            }
        });
    }

    private static void handleOnServer(NpcMoveToPacket packet, ServerPlayer player) {
        // Only the NPC the player is actually talking to may be moved, and only from nearby.
        if (!DialogueSessionManager.isTalkingTo(player, packet.npcUuid)) {
            return;
        }

        Entity target = player.serverLevel().getEntity(packet.npcUuid);
        if (!(target instanceof DialogueNpcEntity npc)
                || player.distanceToSqr(npc) > Config.maxInteractDistanceSqr()) {
            return;
        }

        NpcMovementService.sendTo(player.server, npc, packet.poiId, MoveSpeed.parse(packet.speed));
    }
}
