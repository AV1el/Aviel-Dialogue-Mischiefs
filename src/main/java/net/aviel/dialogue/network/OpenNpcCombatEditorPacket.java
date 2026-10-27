package net.aviel.dialogue.network;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.client.ClientPacketHandler;
import net.aviel.dialogue.entity.ai.NpcCompanion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record OpenNpcCombatEditorPacket(UUID npc, String template, String profile, NpcCompanion.Mode mode,
                                         boolean combat, String status, String social, java.util.List<String> factions) implements CustomPacketPayload {
    public OpenNpcCombatEditorPacket(UUID npc, String template, String profile, NpcCompanion.Mode mode, boolean combat, String status) {
        this(npc, template, profile, mode, combat, status,
                net.aviel.dialogue.npc.social.NpcSocialProfile.DEFAULT.toJson().toString(), java.util.List.of());
    }

    public OpenNpcCombatEditorPacket {
        factions = java.util.List.copyOf(factions);
        if (factions.size() > 256) throw new IllegalArgumentException("Too many factions for editor");
    }
    public static final Type<OpenNpcCombatEditorPacket> TYPE = new Type<>(AvielsDialogueMod.id("combat_editor"));
    public static final StreamCodec<FriendlyByteBuf, OpenNpcCombatEditorPacket> STREAM_CODEC = StreamCodec.ofMember(OpenNpcCombatEditorPacket::write, OpenNpcCombatEditorPacket::read);

    private static OpenNpcCombatEditorPacket read(FriendlyByteBuf buf) {
        return new OpenNpcCombatEditorPacket(buf.readUUID(), buf.readUtf(80), buf.readUtf(16384),
                buf.readEnum(NpcCompanion.Mode.class), buf.readBoolean(), buf.readUtf(512), buf.readUtf(16384), readFactions(buf));
    }

    private static java.util.List<String> readFactions(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > 256) throw new io.netty.handler.codec.DecoderException("Invalid faction count");
        var factions = new java.util.ArrayList<String>(count);
        for (int i = 0; i < count; i++) factions.add(buf.readUtf(80));
        return factions;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(npc);
        buf.writeUtf(template, 80);
        buf.writeUtf(profile, 16384);
        buf.writeEnum(mode);
        buf.writeBoolean(combat);
        buf.writeUtf(status, 512);
        buf.writeUtf(social, 16384);
        buf.writeCollection(factions, (buffer, id) -> buffer.writeUtf(id, 80));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenNpcCombatEditorPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.openNpcCombatEditor(packet));
    }
}
