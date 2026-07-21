package net.aviel.dialogue.network;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.npc.DialogueSessionManager;
import net.aviel.dialogue.npc.NpcDialogueService;
import net.aviel.dialogue.npc.NpcTradeService;
import net.aviel.dialogue.npc.storage.DialogueRepository;
import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record NpcDialogueChoicePacket(UUID npcUuid, String nodeId, int choiceIndex) implements CustomPacketPayload {
    public static final Type<NpcDialogueChoicePacket> TYPE = new Type<>(AvielsDialogueMod.id("npc_dialogue_choice"));
    public static final StreamCodec<FriendlyByteBuf, NpcDialogueChoicePacket> STREAM_CODEC = StreamCodec.ofMember(NpcDialogueChoicePacket::write, NpcDialogueChoicePacket::read);

    private static NpcDialogueChoicePacket read(FriendlyByteBuf buf) {
        return new NpcDialogueChoicePacket(buf.readUUID(), buf.readUtf(80), buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.npcUuid);
        buf.writeUtf(this.nodeId, 80);
        buf.writeVarInt(this.choiceIndex);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NpcDialogueChoicePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                handleOnServer(packet, player);
            }
        });
    }

    private static void handleOnServer(NpcDialogueChoicePacket packet, ServerPlayer player) {
        DialogueSessionManager.Session session = DialogueSessionManager.get(player);
        if (session == null
                || !session.targetUuid().equals(packet.npcUuid)
                || !session.currentNode().equals(packet.nodeId)) {
            return;
        }
        Entity target = player.serverLevel().getEntity(packet.npcUuid);
        if (target == null || player.distanceToSqr(target) > net.aviel.dialogue.Config.maxInteractDistanceSqr()) {
            return;
        }
        String dialogueFile = session.dialogueFile();
        try {
            NpcDialogueDefinition definition = DialogueRepository.loadDialogue(player.server, dialogueFile);
            NpcDialogueDefinition.Node node = definition.node(packet.nodeId);
            if (node == null) {
                return;
            }
            NpcDialogueDefinition.Choice choice = choiceByServerIndex(node, packet.choiceIndex);
            if (choice == null) {
                return;
            }
            if (NpcDialogueService.applyChoiceActions(player, target, dialogueFile, packet.nodeId, choice)) {
                DialogueSessionManager.advance(player, definition, choice);
                if (!choice.trade().isBlank()) {
                    NpcTradeService.openTrade(player, target, choice.trade());
                }
            }
        } catch (Exception ex) {
            AvielsDialogueMod.LOGGER.warn("Dialogue choice failed for player {} in {}", player.getGameProfile().getName(), dialogueFile, ex);
        }
    }

    private static NpcDialogueDefinition.Choice choiceByServerIndex(NpcDialogueDefinition.Node node, int serverIndex) {
        for (NpcDialogueDefinition.Choice choice : node.choices()) {
            if (choice.serverIndex() == serverIndex) {
                return choice;
            }
        }
        return null;
    }
}
