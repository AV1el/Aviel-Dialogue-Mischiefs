package net.aviel.dialogue.npc.social;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public record OpinionPolicy(OpinionReference reference, Integer hostileAt, List<Discount> discounts) {
    public static final OpinionPolicy DEFAULT = new OpinionPolicy(new OpinionReference("", "", ""), null, List.of());

    public record Discount(int min, int percent) {
        public Discount {
            if (min < -100 || min > 100 || percent < 0 || percent > 90) throw new IllegalArgumentException("Invalid opinion discount tier");
        }
    }

    public OpinionPolicy {
        if (hostileAt != null && (hostileAt < -100 || hostileAt > 100)) throw new IllegalArgumentException("hostile_at must be -100..100");
        discounts = List.copyOf(discounts);
        if (discounts.size() > 32) throw new IllegalArgumentException("Too many opinion discount tiers");
    }

    public boolean hostile(int score) {
        return hostileAt != null && score <= hostileAt;
    }

    public int discount(int score) {
        return discounts.stream().filter(tier -> score >= tier.min()).mapToInt(Discount::percent).max().orElse(0);
    }

    public static int integer(JsonObject json, String key) {
        double value = json.get(key).getAsDouble();
        if (!Double.isFinite(value) || value != Math.rint(value) || value < -100000 || value > 100000) {
            throw new IllegalArgumentException("Opinion " + key + " must be an integer in -100000..100000");
        }
        return (int) value;
    }

    public static OpinionPolicy read(JsonObject json) {
        List<Discount> tiers = new ArrayList<>();
        if (json.has("discounts")) {
            for (var entry : json.getAsJsonArray("discounts")) {
                var tier = entry.getAsJsonObject();
                tiers.add(new Discount(integer(tier, "min"), integer(tier, "percent")));
            }
        }
        return new OpinionPolicy(OpinionReference.read(json), json.has("hostile_at") ? integer(json, "hostile_at") : null, tiers);
    }
}
