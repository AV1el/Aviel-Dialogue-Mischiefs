package net.aviel.dialogue.npc.social;

import com.google.gson.JsonParser;
import net.aviel.dialogue.AvielsDialogueMod;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.npc.storage.DialogueStorage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public final class NpcSocialService {
    private static FactionRelations factions = FactionRelations.EMPTY;

    private NpcSocialService() {
    }

    public static void starting(ServerStartingEvent event) {
        factions = FactionRelations.EMPTY;
        if (!reload()) throw new IllegalStateException("Invalid ADM factions.json; see server log");
    }

    public static void stopped(ServerStoppedEvent event) {
        factions = FactionRelations.EMPTY;
    }

    public static boolean reload() {
        var file = DialogueStorage.rootDirectory().resolve("factions.json");
        try {
            factions = Files.isRegularFile(file) ? FactionRelations.parse(JsonParser.parseString(Files.readString(file)).getAsJsonObject()) : FactionRelations.EMPTY;
            return true;
        } catch (IOException | RuntimeException ex) {
            AvielsDialogueMod.LOGGER.error("Could not load ADM factions; previous relations remain active", ex);
            return false;
        }
    }

    public static java.util.List<String> factionIds() {
        return factions.ids();
    }

    public static boolean allied(DialogueNpcEntity first, DialogueNpcEntity second) {
        return factions.relation(first.social().faction(), second.social().faction()) == FactionRelations.Relation.ALLIED
                || first.companion().owner() != null && first.companion().owner().equals(second.companion().owner());
    }

    public static boolean hostile(DialogueNpcEntity npc, Entity target) {
        if (target instanceof DialogueNpcEntity other) {
            return !allied(npc, other) && factions.relation(npc.social().faction(), other.social().faction()) == FactionRelations.Relation.HOSTILE;
        }
        if (target instanceof ServerPlayer player) {
            return !player.isCreative() && !player.isSpectator() && npc.social().opinion().hostile(score(player, npc));
        }
        return false;
    }

    public static int score(ServerPlayer player, DialogueNpcEntity npc) {
        String key = new OpinionReference("", "", "").resolve(npc);
        return key.isEmpty() ? 0 : OpinionData.get(player.server).score(player.getUUID(), key);
    }

    public static int discount(ServerPlayer player, Entity target) {
        return target instanceof DialogueNpcEntity npc ? npc.social().opinion().discount(score(player, npc)) : 0;
    }

    public static boolean referencesValid(Entity target, List<OpinionChange> changes) {
        return changes.stream().allMatch(change -> !change.reference().resolve(target).isEmpty());
    }

    public static void apply(ServerPlayer player, Entity target, List<OpinionChange> changes) {
        OpinionData data = OpinionData.get(player.server);
        for (var change : changes) {
            String key = change.reference().resolve(target);
            if (change.replace()) data.set(player.getUUID(), key, change.amount());
            else data.add(player.getUUID(), key, change.amount());
        }
    }
}
