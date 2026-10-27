package net.aviel.dialogue.npc.social;

import net.aviel.dialogue.AvielsDialogueMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class OpinionData extends SavedData {
    private final Map<UUID, Map<String, Integer>> scores = new HashMap<>();

    public static OpinionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(OpinionData::new, OpinionData::load), "adm_opinions");
    }

    public static int clamp(long value) {
        return (int) Math.max(-100, Math.min(100, value));
    }

    public int score(UUID player, String key) {
        return scores.getOrDefault(player, Map.of()).getOrDefault(key, 0);
    }

    public void set(UUID player, String key, long value) {
        if (key == null || key.isBlank() || key.length() > 128) throw new IllegalArgumentException("Invalid opinion key");
        scores.computeIfAbsent(player, ignored -> new HashMap<>()).put(key, clamp(value));
        setDirty();
    }

    public void add(UUID player, String key, int value) {
        set(player, key, (long) score(player, key) + value);
    }

    public static OpinionData load(CompoundTag tag, HolderLookup.Provider registries) {
        OpinionData data = new OpinionData();
        CompoundTag players = tag.getCompound("players");
        for (String id : players.getAllKeys()) {
            try {
                UUID player = UUID.fromString(id);
                CompoundTag values = players.getCompound(id);
                for (String key : values.getAllKeys()) data.set(player, key, values.getInt(key));
            } catch (IllegalArgumentException ex) {
                AvielsDialogueMod.LOGGER.warn("Invalid saved opinion entry for {}", id, ex);
            }
        }
        data.setDirty(false);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        scores.forEach((player, values) -> {
            CompoundTag entry = new CompoundTag();
            values.forEach(entry::putInt);
            players.put(player.toString(), entry);
        });
        tag.put("players", players);
        return tag;
    }
}
