package net.aviel.dialogue.npc.social;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

public final class FactionRelations {
    public enum Relation { ALLIED, NEUTRAL, HOSTILE }
    public static final FactionRelations EMPTY = new FactionRelations(Map.of());
    private final Map<String, Map<String, Relation>> relations;

    private FactionRelations(Map<String, Map<String, Relation>> relations) {
        Map<String, Map<String, Relation>> copy = new HashMap<>();
        relations.forEach((id, entries) -> copy.put(id, Map.copyOf(entries)));
        this.relations = Map.copyOf(copy);
    }

    public Relation relation(String first, String second) {
        if (first.isEmpty() || second.isEmpty()) return Relation.NEUTRAL;
        if (first.equals(second)) return Relation.ALLIED;
        return relations.getOrDefault(first, Map.of()).getOrDefault(second, Relation.NEUTRAL);
    }

    public java.util.List<String> ids() {
        return relations.keySet().stream().sorted().toList();
    }

    public static FactionRelations parse(JsonObject root) {
        Map<String, Map<String, Relation>> result = new HashMap<>();
        var factions = root.getAsJsonObject("factions");
        if (factions.size() > 256) throw new IllegalArgumentException("At most 256 factions are supported");
        factions.keySet().forEach(id -> {
            OpinionReference.validateId(id);
            result.put(id, new HashMap<>());
        });
        for (var faction : factions.entrySet()) {
            var definition = faction.getValue().getAsJsonObject();
            if (!definition.has("relations")) continue;
            for (var entry : definition.getAsJsonObject("relations").entrySet()) {
                String target = entry.getKey();
                if (!result.containsKey(target)) throw new IllegalArgumentException("Unknown faction: " + target);
                var relation = Relation.valueOf(entry.getValue().getAsString().toUpperCase(java.util.Locale.ROOT));
                if (faction.getKey().equals(target) && relation != Relation.ALLIED) throw new IllegalArgumentException("A faction must be allied with itself");
                Relation previous = result.get(faction.getKey()).putIfAbsent(target, relation);
                if (previous != null && previous != relation) throw new IllegalArgumentException("Conflicting faction relation: " + target);
                previous = result.get(target).putIfAbsent(faction.getKey(), relation);
                if (previous != null && previous != relation) throw new IllegalArgumentException("Conflicting faction relation: " + target);
            }
        }
        return new FactionRelations(result);
    }
}
