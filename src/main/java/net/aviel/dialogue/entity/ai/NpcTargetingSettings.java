package net.aviel.dialogue.entity.ai;

import com.google.gson.JsonObject;

public record NpcTargetingSettings(boolean switchOnAttack, boolean protectOwner, int switchCooldown, int lostSightTicks) {
    public static final NpcTargetingSettings DEFAULT = new NpcTargetingSettings(true, true, 20, 40);

    public NpcTargetingSettings {
        if (switchCooldown < 0 || switchCooldown > 200 || lostSightTicks < 0 || lostSightTicks > 200) {
            throw new IllegalArgumentException("Targeting delays must be between 0 and 200 ticks");
        }
    }

    public static NpcTargetingSettings fromJson(JsonObject json) {
        return new NpcTargetingSettings(
                json.has("switch_on_attack") ? json.get("switch_on_attack").getAsBoolean() : DEFAULT.switchOnAttack,
                json.has("protect_owner") ? json.get("protect_owner").getAsBoolean() : DEFAULT.protectOwner,
                delay(json, "switch_cooldown", DEFAULT.switchCooldown), delay(json, "lost_sight_ticks", DEFAULT.lostSightTicks));
    }

    private static int delay(JsonObject json, String key, int fallback) {
        double value = json.has(key) ? json.get(key).getAsDouble() : fallback;
        if (!Double.isFinite(value) || value != Math.rint(value) || value < 0 || value > 200) {
            throw new IllegalArgumentException("Targeting " + key + " must be a whole number between 0 and 200");
        }
        return (int) value;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("switch_on_attack", switchOnAttack);
        json.addProperty("protect_owner", protectOwner);
        json.addProperty("switch_cooldown", switchCooldown);
        json.addProperty("lost_sight_ticks", lostSightTicks);
        return json;
    }

    public static boolean shouldSwitch(int currentPriority, int candidatePriority, boolean cooldownReady) {
        return cooldownReady && candidatePriority > currentPriority;
    }
}
