package net.aviel.dialogue.network;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.npc.NpcEditorService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** One edit action from the NPC editor screen; validated server-side before applying. */
public record NpcEditorActionPacket(UUID npcUuid, String action, String stringValue, float floatValue) implements CustomPacketPayload {
    public static final Type<NpcEditorActionPacket> TYPE = new Type<>(AvielsDialogueMod.id("npc_editor_action"));
    public static final StreamCodec<FriendlyByteBuf, NpcEditorActionPacket> STREAM_CODEC = StreamCodec.ofMember(NpcEditorActionPacket::write, NpcEditorActionPacket::read);

    private static NpcEditorActionPacket read(FriendlyByteBuf buf) {
        return new NpcEditorActionPacket(buf.readUUID(), buf.readUtf(32), buf.readUtf(260), buf.readFloat());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.npcUuid);
        buf.writeUtf(this.action, 32);
        buf.writeUtf(this.stringValue, 260);
        buf.writeFloat(this.floatValue);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NpcEditorActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                NpcEditorService.handleAction(player, packet.npcUuid, packet.action, packet.stringValue, packet.floatValue);
            }
        });
    }
}
