package net.aviel.dialogue.npc.social;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public record OpinionChange(OpinionReference reference, int amount, boolean replace) {
    public static final com.google.gson.JsonSerializer<OpinionChange> SERIALIZER = (change, type, context) -> change.toJson();

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (!reference.scope().isEmpty()) json.addProperty("scope", reference.scope());
        if (!reference.faction().isEmpty()) json.addProperty("faction", reference.faction());
        if (!reference.key().isEmpty()) json.addProperty("key", reference.key());
        json.addProperty(replace ? "set" : "amount", amount);
        return json;
    }

    public static List<OpinionChange> read(JsonObject choice) {
        List<OpinionChange> changes = new ArrayList<>();
        if (!choice.has("opinion_changes")) return List.of();
        var entries = choice.getAsJsonArray("opinion_changes");
        if (entries.size() > 32) throw new IllegalArgumentException("Too many opinion changes");
        for (var entry : entries) {
            var json = entry.getAsJsonObject();
            if (json.has("amount") == json.has("set")) throw new IllegalArgumentException("Opinion change requires either amount or set");
            boolean replace = json.has("set");
            int amount = OpinionPolicy.integer(json, replace ? "set" : "amount");
            if (replace && (amount < -100 || amount > 100)) throw new IllegalArgumentException("Opinion set must be -100..100");
            changes.add(new OpinionChange(OpinionReference.read(json), amount, replace));
        }
        return List.copyOf(changes);
    }
}
