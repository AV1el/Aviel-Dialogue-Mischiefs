package net.aviel.dialogue.npc.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public record NpcSocialProfile(String faction, boolean combat, OpinionPolicy opinion) {
    public static final NpcSocialProfile DEFAULT = new NpcSocialProfile("", false, OpinionPolicy.DEFAULT);

    public NpcSocialProfile {
        if (!faction.isEmpty()) OpinionReference.validateId(faction);
        if (opinion.reference().scope().equals("faction") && faction.isEmpty()) {
            throw new IllegalArgumentException("Faction opinion requires an NPC faction");
        }
    }

    public static NpcSocialProfile read(JsonObject json) {
        return new NpcSocialProfile(json.has("faction") ? json.get("faction").getAsString() : "",
                json.has("combat") && json.get("combat").getAsBoolean(),
                json.has("opinion") ? OpinionPolicy.read(json.getAsJsonObject("opinion")) : OpinionPolicy.DEFAULT);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("faction", faction);
        json.addProperty("combat", combat);
        JsonObject policy = new JsonObject();
        if (!opinion.reference().scope().isEmpty()) policy.addProperty("scope", opinion.reference().scope());
        if (!opinion.reference().faction().isEmpty()) policy.addProperty("faction", opinion.reference().faction());
        if (!opinion.reference().key().isEmpty()) policy.addProperty("key", opinion.reference().key());
        if (opinion.hostileAt() != null) policy.addProperty("hostile_at", opinion.hostileAt());
        JsonArray discounts = new JsonArray();
        for (var tier : opinion.discounts()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("min", tier.min());
            entry.addProperty("percent", tier.percent());
            discounts.add(entry);
        }
        policy.add("discounts", discounts);
        json.add("opinion", policy);
        return json;
    }
}
