package net.aviel.dialogue.npc.poi;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Turns "walk to this point" requests from dialogues and commands into actual movement. */
public final class NpcMovementService {
    private NpcMovementService() {
    }

    /**
     * Sends the NPC to a point by id.
     *
     * @return {@code true} when the NPC is on its way; {@code false} if the point is unknown
     *         or sits in another dimension, which cannot be walked to.
     */
    public static boolean sendTo(MinecraftServer server, DialogueNpcEntity npc, String poiId, MoveSpeed speed) {
        if (server == null || npc == null) {
            return false;
        }

        PointOfInterest point = NpcPoiData.get(server).find(poiId);
        if (point == null) {
            AvielsDialogueMod.LOGGER.warn("NPC {} was sent to unknown point '{}'", npc.getProfileName(), poiId);
            return false;
        }

        if (point.dimension() != npc.level().dimension()) {
            AvielsDialogueMod.LOGGER.warn(
                    "NPC {} cannot walk to '{}': it is in {}", npc.getProfileName(), point.id(), point.dimension().location());
            return false;
        }

        npc.walkTo(point.pos(), speed);
        return true;
    }

    /** Same as {@link #sendTo}, but tells the player what went wrong. */
    public static boolean sendToWithFeedback(ServerPlayer player, DialogueNpcEntity npc, String poiId, MoveSpeed speed) {
        if (sendTo(player.server, npc, poiId, speed)) {
            return true;
        }

        player.displayClientMessage(Component.translatable("message.adm.poi.unreachable", poiId), true);
        return false;
    }
}
