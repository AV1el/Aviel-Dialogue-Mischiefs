package net.aviel.dialogue.network;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record OpenNpcTradePacket(UUID npcUuid, String npcName, String tradeFile, String tradeJson) implements CustomPacketPayload {
    public static final Type<OpenNpcTradePacket> TYPE = new Type<>(AvielsDialogueMod.id("open_npc_trade"));
    public static final StreamCodec<FriendlyByteBuf, OpenNpcTradePacket> STREAM_CODEC = StreamCodec.ofMember(OpenNpcTradePacket::write, OpenNpcTradePacket::read);

    private static OpenNpcTradePacket read(FriendlyByteBuf buf) {
        return new OpenNpcTradePacket(
                buf.readUUID(),
                buf.readUtf(128),
                buf.readUtf(260),
                buf.readUtf(30000)
        );
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.npcUuid);
        buf.writeUtf(this.npcName, 128);
        buf.writeUtf(this.tradeFile, 260);
        buf.writeUtf(this.tradeJson, 30000);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenNpcTradePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.openTrade(packet.npcUuid, packet.npcName, packet.tradeFile, packet.tradeJson));
    }
}
