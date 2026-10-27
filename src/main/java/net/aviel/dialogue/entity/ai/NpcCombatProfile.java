package net.aviel.dialogue.entity.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

public record NpcCombatProfile(double health, double damage, double armor, double speed,
                               double radius, double movementMultiplier, Policy policy, List<String> targets, double attackSpeed,
                               NpcTargetingSettings targeting, NpcMeleeSettings melee) {
    public enum Policy { PASSIVE, RETALIATE, DEFEND_OWNER, HOSTILE }

    public static final NpcCombatProfile DEFAULT = new NpcCombatProfile(20, 3, 0, 0.5, 12, 0.6, Policy.HOSTILE, List.of());

    public NpcCombatProfile(double health, double damage, double armor, double speed, double radius,
                            double movementMultiplier, Policy policy, List<String> targets) {
        this(health, damage, armor, speed, radius, movementMultiplier, policy, targets, 4);
    }

    public NpcCombatProfile(double health, double damage, double armor, double speed, double radius,
                            double movementMultiplier, Policy policy, List<String> targets, double attackSpeed) {
        this(health, damage, armor, speed, radius, movementMultiplier, policy, targets, attackSpeed, NpcTargetingSettings.DEFAULT, NpcMeleeSettings.DEFAULT);
    }

    public NpcCombatProfile {
        check(health, 1, 1024, "health");
        check(damage, 0, 2048, "damage");
        check(armor, 0, 30, "armor");
        check(speed, 0.01, 1, "speed");
        check(radius, 1, 32, "radius");
        check(movementMultiplier, 0.1, 2, "movement_multiplier");
        check(attackSpeed, 0.1, 20, "attack_speed");
        if (policy == null) throw new IllegalArgumentException("Combat policy is required");
        if (targeting == null) throw new IllegalArgumentException("Targeting settings are required");
        if (melee == null) throw new IllegalArgumentException("Melee settings are required");
        targets = List.copyOf(targets);
        if (targets.size() > 64) throw new IllegalArgumentException("At most 64 combat target IDs are supported");
        for (String id : targets) {
            if (ResourceLocation.tryParse(id) == null) throw new IllegalArgumentException("Invalid combat target ID: " + id);
        }
        targets = targets.stream().map(ResourceLocation::parse).map(ResourceLocation::toString).distinct().toList();
    }

    private static void check(double value, double min, double max, String name) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("Combat " + name + " must be between " + min + " and " + max);
        }
    }

    public static NpcCombatProfile fromJson(JsonObject json) {
        Policy policy = Policy.valueOf(json.has("policy")
                ? json.get("policy").getAsString().toUpperCase(Locale.ROOT) : DEFAULT.policy.name());
        List<String> targets = json.has("targets") ? json.getAsJsonArray("targets").asList().stream()
                .map(JsonElement::getAsString).toList() : List.of();
        return new NpcCombatProfile(number(json, "health", DEFAULT.health), number(json, "damage", DEFAULT.damage),
                number(json, "armor", DEFAULT.armor), number(json, "speed", DEFAULT.speed),
                number(json, "radius", DEFAULT.radius), number(json, "movement_multiplier", DEFAULT.movementMultiplier), policy, targets,
                number(json, "attack_speed", DEFAULT.attackSpeed),
                json.has("targeting") ? NpcTargetingSettings.fromJson(json.getAsJsonObject("targeting")) : NpcTargetingSettings.DEFAULT,
                NpcMeleeSettings.fromJson(json));
    }

    private static double number(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("health", health);
        json.addProperty("damage", damage);
        json.addProperty("armor", armor);
        json.addProperty("speed", speed);
        json.addProperty("attack_speed", attackSpeed);
        json.addProperty("radius", radius);
        json.addProperty("movement_multiplier", movementMultiplier);
        json.addProperty("policy", policy.name().toLowerCase(Locale.ROOT));
        JsonArray ids = new JsonArray();
        targets.forEach(ids::add);
        json.add("targets", ids);
        json.add("targeting", targeting.toJson());
        melee.write(json);
        return json;
    }
}
