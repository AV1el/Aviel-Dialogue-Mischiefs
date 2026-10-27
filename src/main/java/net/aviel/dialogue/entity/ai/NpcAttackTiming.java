package net.aviel.dialogue.entity.ai;

public final class NpcAttackTiming {
    private NpcAttackTiming() {
    }

    public static int cooldownTicks(double attackSpeed) {
        return (int) Math.ceil(20.0 / Math.max(0.1, Math.min(20, attackSpeed)));
    }
}
