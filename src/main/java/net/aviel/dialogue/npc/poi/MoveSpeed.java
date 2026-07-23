package net.aviel.dialogue.npc.poi;

import java.util.Locale;

/**
 * Walking speeds a dialogue can ask for, as multipliers of the NPC's base movement speed.
 * Named rather than numeric so {@code <moveto:slow:market>} reads as intent, not tuning.
 */
public enum MoveSpeed {
    /** A deliberate, in-character walk. */
    SLOW(0.55D),

    /** The default when a tag omits the speed. */
    WALK(1.0D),

    /** Jogging, for urgent scenes. */
    FAST(1.5D),

    /** Flat out; use sparingly, it looks frantic. */
    SPRINT(2.0D);

    private final double multiplier;

    MoveSpeed(double multiplier) {
        this.multiplier = multiplier;
    }

    public double multiplier() {
        return multiplier;
    }

    /** Parses a tag's speed word, falling back to {@link #WALK} for anything unknown. */
    public static MoveSpeed parse(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "slow", "stroll", "walk_slow" -> SLOW;
            case "fast", "jog", "hurry" -> FAST;
            case "sprint", "run", "flee" -> SPRINT;
            default -> WALK;
        };
    }

    public String tagName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
