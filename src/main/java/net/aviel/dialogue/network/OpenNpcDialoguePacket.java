package net.aviel.dialogue.network;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record OpenNpcDialoguePacket(UUID npcUuid, String npcName, String dialogueFile, String startNode, String rawJson) implements CustomPacketPayload {
    public static final Type<OpenNpcDialoguePacket> TYPE = new Type<>(AvielsDialogueMod.id("open_npc_dialogue"));
    public static final StreamCodec<FriendlyByteBuf, OpenNpcDialoguePacket> STREAM_CODEC = StreamCodec.ofMember(OpenNpcDialoguePacket::write, OpenNpcDialoguePacket::read);

    private static OpenNpcDialoguePacket read(FriendlyByteBuf buf) {
        return new OpenNpcDialoguePacket(
                buf.readUUID(),
                buf.readUtf(128),
                buf.readUtf(260),
                buf.readUtf(80),
                buf.readUtf(30000)
        );
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.npcUuid);
        buf.writeUtf(this.npcName, 128);
        buf.writeUtf(this.dialogueFile, 260);
        buf.writeUtf(this.startNode, 80);
        buf.writeUtf(this.rawJson, 30000);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenNpcDialoguePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.openDialogue(packet.npcUuid, packet.npcName, packet.dialogueFile, packet.startNode, packet.rawJson));
    }
}
