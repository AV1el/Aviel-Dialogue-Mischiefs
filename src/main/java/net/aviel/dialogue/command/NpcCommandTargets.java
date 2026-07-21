package net.aviel.dialogue.command;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

final class NpcCommandTargets {
    private NpcCommandTargets() {
    }

    /**
     * Returns the NPC the command should act on (selection, then crosshair, then nearest),
     * or {@code null} after sending a failure message.
     */
    static DialogueNpcEntity requireTarget(CommandSourceStack source) {
        DialogueNpcEntity npc = NpcSelections.resolveTarget(source);
        if (npc == null) {
            source.sendFailure(Component.literal("No dialogue NPC targeted. Select one with /adm_npc select, look at one, or stand within 8 blocks."));
        }
        return npc;
    }

    static DialogueNpcEntity nearestNpc(CommandSourceStack source) {
        return nearbyNpcs(source, 8.0D).stream()
                .min(Comparator.comparingDouble(npc -> npc.distanceToSqr(source.getPosition())))
                .orElse(null);
    }

    static List<DialogueNpcEntity> nearbyNpcs(CommandSourceStack source, double radius) {
        AABB box = new AABB(source.getPosition(), source.getPosition()).inflate(radius);
        return source.getLevel().getEntities(AvielsDialogueMod.DIALOGUE_NPC.get(), box, Entity::isAlive);
    }

    static String message(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
