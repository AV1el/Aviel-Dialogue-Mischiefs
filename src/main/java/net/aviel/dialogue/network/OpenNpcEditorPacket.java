package net.aviel.dialogue.network;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

/** Opens the admin NPC editor on the client with the server-side option lists. */
public record OpenNpcEditorPacket(
        int entityId,
        UUID npcUuid,
        List<String> dialogues,
        List<String> skins,
        List<String> templates
) implements CustomPacketPayload {
    public static final Type<OpenNpcEditorPacket> TYPE = new Type<>(AvielsDialogueMod.id("open_npc_editor"));
    public static final StreamCodec<FriendlyByteBuf, OpenNpcEditorPacket> STREAM_CODEC = StreamCodec.ofMember(OpenNpcEditorPacket::write, OpenNpcEditorPacket::read);

    private static OpenNpcEditorPacket read(FriendlyByteBuf buf) {
        return new OpenNpcEditorPacket(
                buf.readVarInt(),
                buf.readUUID(),
                buf.readList(b -> b.readUtf(260)),
                buf.readList(b -> b.readUtf(260)),
                buf.readList(b -> b.readUtf(120))
        );
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeUUID(this.npcUuid);
        buf.writeCollection(this.dialogues, (b, value) -> b.writeUtf(value, 260));
        buf.writeCollection(this.skins, (b, value) -> b.writeUtf(value, 260));
        buf.writeCollection(this.templates, (b, value) -> b.writeUtf(value, 120));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenNpcEditorPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.openNpcEditor(packet));
    }
}
