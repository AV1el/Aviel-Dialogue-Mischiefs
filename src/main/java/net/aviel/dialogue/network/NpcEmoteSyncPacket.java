package net.aviel.dialogue.network;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record NpcEmoteSyncPacket(String emoteFileName, byte[] jsonBytes) implements CustomPacketPayload {
    public static final Type<NpcEmoteSyncPacket> TYPE = new Type<>(AvielsDialogueMod.id("npc_emote_sync"));
    public static final StreamCodec<FriendlyByteBuf, NpcEmoteSyncPacket> STREAM_CODEC = StreamCodec.ofMember(NpcEmoteSyncPacket::write, NpcEmoteSyncPacket::read);

    private static NpcEmoteSyncPacket read(FriendlyByteBuf buf) {
        return new NpcEmoteSyncPacket(buf.readUtf(260), buf.readByteArray());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.emoteFileName, 260);
        buf.writeByteArray(this.jsonBytes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NpcEmoteSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.handleNpcEmoteSync(packet.emoteFileName, packet.jsonBytes));
    }
}
