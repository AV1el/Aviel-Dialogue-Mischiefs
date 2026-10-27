package net.aviel.dialogue.npc.social;

import com.google.gson.JsonObject;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.minecraft.world.entity.Entity;

public record OpinionReference(String scope, String faction, String key) {
    public OpinionReference {
        if (!scope.isEmpty() && !scope.equals("npc") && !scope.equals("faction")) throw new IllegalArgumentException("Opinion scope must be npc or faction");
        if (!faction.isEmpty()) validateId(faction);
        if (!key.isEmpty()) validateId(key);
        if ((!faction.isEmpty() && !key.isEmpty()) || (!scope.isEmpty() && (!faction.isEmpty() || !key.isEmpty()))) {
            throw new IllegalArgumentException("Choose one opinion reference: scope, faction or key");
        }
    }

    public static void validateId(String id) {
        if (!id.matches("[a-z0-9_.:-]{1,80}")) throw new IllegalArgumentException("Invalid social ID: " + id);
    }

    public static OpinionReference read(JsonObject json) {
        return new OpinionReference(string(json, "scope"), string(json, "faction"), string(json, "key"));
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsString() : "";
    }

    public String resolve(Entity target) {
        if (!faction.isEmpty()) return "faction:" + faction;
        if (!key.isEmpty()) return "custom:" + key;
        if (!(target instanceof DialogueNpcEntity npc)) return "";
        if (scope.equals("npc")) return "npc:" + npc.getStringUUID();
        if (scope.equals("faction")) return npc.social().faction().isEmpty() ? "" : "faction:" + npc.social().faction();
        return npc.social().opinion().reference().resolveDefault(npc);
    }

    private String resolveDefault(DialogueNpcEntity npc) {
        if (scope.isEmpty() && faction.isEmpty() && key.isEmpty()) return "npc:" + npc.getStringUUID();
        return resolve(npc);
    }
}
