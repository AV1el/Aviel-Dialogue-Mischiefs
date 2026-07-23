package net.aviel.dialogue.entity.ai;

import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Walks the NPC to the destination a dialogue asked for. The goal owns the whole trip: it
 * starts when a destination appears, re-paths if the NPC gets stuck, and clears the
 * destination on arrival so the NPC goes back to standing still.
 */
public class NpcWalkToPoiGoal extends Goal {
    /** Close enough to count as arrived, in blocks. */
    private static final double ARRIVAL_DISTANCE = 1.6D;

    /** Give up after this many ticks so a blocked NPC does not walk into a wall forever. */
    private static final int TIMEOUT_TICKS = 20 * 60;

    /** Re-issue the path this often; the world may have changed since the last one. */
    private static final int REPATH_INTERVAL = 20;

    private final DialogueNpcEntity npc;
    private int ticksRunning;
    private int ticksUntilRepath;

    public NpcWalkToPoiGoal(DialogueNpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return npc.getMoveDestination() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return npc.getMoveDestination() != null && ticksRunning < TIMEOUT_TICKS;
    }

    @Override
    public void start() {
        ticksRunning = 0;
        ticksUntilRepath = 0;
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        ticksRunning = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        BlockPos destination = npc.getMoveDestination();
        if (destination == null) {
            return;
        }

        ticksRunning++;
        npc.getLookControl().setLookAt(
                destination.getX() + 0.5D, destination.getY() + 1.0D, destination.getZ() + 0.5D);

        if (npc.position().distanceToSqr(
                destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D)
                <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE) {
            npc.onReachedDestination();
            return;
        }

        if (--ticksUntilRepath <= 0) {
            ticksUntilRepath = REPATH_INTERVAL;
            npc.getNavigation().moveTo(
                    destination.getX() + 0.5D,
                    destination.getY(),
                    destination.getZ() + 0.5D,
                    npc.getMoveSpeedMultiplier());
        }
    }
}
