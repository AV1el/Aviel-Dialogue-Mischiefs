package net.aviel.dialogue.network;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.Config;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.npc.DialogueSessionManager;
import net.aviel.dialogue.npc.NpcEmoteService;
import net.aviel.dialogue.npc.emote.NpcJsonEmoteParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Locale;
import java.util.UUID;

public record NpcDialogueAnimationPacket(UUID npcUuid, String emote, boolean repeat) implements CustomPacketPayload {
    public static final Type<NpcDialogueAnimationPacket> TYPE = new Type<>(AvielsDialogueMod.id("npc_dialogue_animation"));
    public static final StreamCodec<FriendlyByteBuf, NpcDialogueAnimationPacket> STREAM_CODEC = StreamCodec.ofMember(NpcDialogueAnimationPacket::write, NpcDialogueAnimationPacket::read);

    private static NpcDialogueAnimationPacket read(FriendlyByteBuf buf) {
        return new NpcDialogueAnimationPacket(buf.readUUID(), buf.readUtf(260), buf.readBoolean());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.npcUuid);
        buf.writeUtf(this.emote, 260);
        buf.writeBoolean(this.repeat);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NpcDialogueAnimationPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || packet.emote.isBlank()) {
                return;
            }
            if (!DialogueSessionManager.isTalkingTo(player, packet.npcUuid)) {
                return;
            }
            Entity target = player.serverLevel().getEntity(packet.npcUuid);
            if (!(target instanceof DialogueNpcEntity npc)
                    || npc.getDialogueFile().isBlank()
                    || player.distanceToSqr(npc) > Config.maxInteractDistanceSqr()) {
                return;
            }

            String action = packet.emote.trim();
            String lower = action.toLowerCase(Locale.ROOT);
            if ("stop".equals(lower) || "clear".equals(lower) || "idle".equals(lower) || "none".equals(lower)) {
                npc.stopNpcEmote();
                return;
            }

            String fileName = NpcEmoteService.resolveEmoteFileName(player.server, action);
            if (fileName == null) {
                return;
            }
            byte[] bytes = NpcEmoteService.readEmoteBytes(player.server, fileName);
            if (bytes == null || bytes.length == 0) {
                return;
            }
            try {
                NpcJsonEmoteParser.parse(bytes);
            } catch (Exception ex) {
                AvielsDialogueMod.LOGGER.warn("Rejected invalid emote {} requested by player {}", fileName, player.getGameProfile().getName(), ex);
                return;
            }

            NpcEmoteService.syncEmoteToAll(player.server, fileName);
            npc.playNpcEmote(fileName, npc.level().getGameTime(), packet.repeat);
        });
    }
}
