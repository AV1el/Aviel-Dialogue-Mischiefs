package net.aviel.dialogue.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcDialogueRefreshPacketTest {
    @Test
    void distinguishesOpeningDialogueFromUpdatingVisibleChoices() {
        for (boolean refresh : new boolean[]{false, true}) {
            var packet = new OpenNpcDialoguePacket(UUID.randomUUID(), "Guard", "guard.json", "start", "{}", refresh);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                OpenNpcDialoguePacket.STREAM_CODEC.encode(buffer, packet);
                assertEquals(packet, OpenNpcDialoguePacket.STREAM_CODEC.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }
}
