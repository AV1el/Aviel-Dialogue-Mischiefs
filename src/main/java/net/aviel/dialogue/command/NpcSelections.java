package net.aviel.dialogue.command;

import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player NPC selection so edit commands stop guessing which NPC is meant. */
public final class NpcSelections {
    private static final double LOOK_RANGE = 24.0D;
    private static final Map<UUID, UUID> SELECTED = new ConcurrentHashMap<>();

    private NpcSelections() {
    }

    public static void select(ServerPlayer player, DialogueNpcEntity npc) {
        SELECTED.put(player.getUUID(), npc.getUUID());
    }

    public static void deselect(ServerPlayer player) {
        SELECTED.remove(player.getUUID());
    }

    public static void forget(UUID playerUuid) {
        SELECTED.remove(playerUuid);
    }

    public static boolean isSelected(ServerPlayer player, DialogueNpcEntity npc) {
        return npc.getUUID().equals(SELECTED.get(player.getUUID()));
    }

    public static DialogueNpcEntity selected(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return null;
        }
        UUID npcUuid = SELECTED.get(player.getUUID());
        if (npcUuid == null) {
            return null;
        }
        Entity entity = source.getLevel().getEntity(npcUuid);
        return entity instanceof DialogueNpcEntity npc && npc.isAlive() ? npc : null;
    }

    /** The NPC the command sender is aiming at, within {@value #LOOK_RANGE} blocks. */
    public static DialogueNpcEntity lookedAt(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return null;
        }
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getViewVector(1.0F).scale(LOOK_RANGE));
        DialogueNpcEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        AABB searchBox = player.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0D);
        for (DialogueNpcEntity npc : source.getLevel().getEntities(
                AvielsDialogueMod.DIALOGUE_NPC.get(), searchBox, Entity::isAlive)) {
            var hit = npc.getBoundingBox().inflate(0.25D).clip(from, to);
            if (hit.isPresent()) {
                double distance = from.distanceToSqr(hit.get());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = npc;
                }
            }
        }
        return best;
    }

    /** Resolution order: explicit selection, then crosshair target, then nearest within 8 blocks. */
    public static DialogueNpcEntity resolveTarget(CommandSourceStack source) {
        DialogueNpcEntity npc = selected(source);
        if (npc != null) {
            return npc;
        }
        npc = lookedAt(source);
        if (npc != null) {
            return npc;
        }
        return NpcCommandTargets.nearestNpc(source);
    }
}
