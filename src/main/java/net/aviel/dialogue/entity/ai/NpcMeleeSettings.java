package net.aviel.dialogue.entity.ai;

import com.google.gson.JsonObject;

public record NpcMeleeSettings(double range, double knockback, double resistance) {
    public static final NpcMeleeSettings DEFAULT = new NpcMeleeSettings(3, 0, 0);

    public NpcMeleeSettings {
        check(range, 0.5, 6, "attack_range");
        check(knockback, 0, 5, "knockback");
        check(resistance, 0, 1, "knockback_resistance");
    }

    private static void check(double value, double min, double max, String key) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
    }

    public static NpcMeleeSettings fromJson(JsonObject json) {
        return new NpcMeleeSettings(number(json, "attack_range", DEFAULT.range),
                number(json, "knockback", DEFAULT.knockback), number(json, "knockback_resistance", DEFAULT.resistance));
    }

    private static double number(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    public void write(JsonObject json) {
        json.addProperty("attack_range", range);
        json.addProperty("knockback", knockback);
        json.addProperty("knockback_resistance", resistance);
    }

    public boolean canReach(net.minecraft.world.phys.AABB target, net.minecraft.world.phys.Vec3 eye) {
        return target.distanceToSqr(eye) <= range * range;
    }
}
