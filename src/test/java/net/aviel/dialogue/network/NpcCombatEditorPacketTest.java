package net.aviel.dialogue.network;

import io.netty.buffer.Unpooled;
import net.aviel.dialogue.entity.ai.NpcCombatProfile;
import net.aviel.dialogue.entity.ai.NpcCompanion;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcCombatEditorPacketTest {
    @Test
    void roundTripsTemplateAndServerAcknowledgement() {
        var packet = new OpenNpcCombatEditorPacket(UUID.randomUUID(), "guard", NpcCombatProfile.DEFAULT.toJson().toString(),
                NpcCompanion.Mode.GUARD, true, "saved",
                "{\"faction\":\"wardens\",\"opinion\":{\"scope\":\"faction\",\"hostile_at\":-50}}",
                java.util.List.of("bandits", "wardens"));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            OpenNpcCombatEditorPacket.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, OpenNpcCombatEditorPacket.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void profileActionsSupportLargerPayloadThanAppearanceActions() {
        var packet = new NpcEditorActionPacket(UUID.randomUUID(), "combat_save", NpcCombatProfile.DEFAULT.toJson().toString(), 0);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            NpcEditorActionPacket.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, NpcEditorActionPacket.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
