package net.aviel.dialogue.network;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.npc.NpcTradeService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record NpcTradeActionPacket(UUID npcUuid, String tradeFile, String offerId, int offerIndex, int amount) implements CustomPacketPayload {
    public static final Type<NpcTradeActionPacket> TYPE = new Type<>(AvielsDialogueMod.id("npc_trade_action"));
    public static final StreamCodec<FriendlyByteBuf, NpcTradeActionPacket> STREAM_CODEC = StreamCodec.ofMember(NpcTradeActionPacket::write, NpcTradeActionPacket::read);

    private static NpcTradeActionPacket read(FriendlyByteBuf buf) {
        return new NpcTradeActionPacket(
                buf.readUUID(),
                buf.readUtf(260),
                buf.readUtf(80),
                buf.readVarInt(),
                buf.readVarInt()
        );
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.npcUuid);
        buf.writeUtf(this.tradeFile, 260);
        buf.writeUtf(this.offerId, 80);
        buf.writeVarInt(this.offerIndex);
        buf.writeVarInt(this.amount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NpcTradeActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                NpcTradeService.executeTrade(player, packet.npcUuid, packet.tradeFile, packet.offerId, packet.offerIndex, packet.amount);
            }
        });
    }
}
