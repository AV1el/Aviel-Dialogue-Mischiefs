package net.aviel.dialogue.npc.dialogue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NpcDialoguePlayerData extends SavedData {
    private static final String DATA_NAME = "aviel_npc_dialogue_players";
    private static final String NBT_PLAYERS = "players";
    private static final String NBT_FLAGS = "flags";
    private static final String NBT_CHOICES = "choices";

    private final Map<UUID, PlayerState> players = new HashMap<>();

    public static NpcDialoguePlayerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NpcDialoguePlayerData::new, NpcDialoguePlayerData::load),
                DATA_NAME
        );
    }

    public static NpcDialoguePlayerData load(CompoundTag tag, HolderLookup.Provider registries) {
        NpcDialoguePlayerData data = new NpcDialoguePlayerData();
        CompoundTag playersTag = tag.getCompound(NBT_PLAYERS);
        for (String uuidText : playersTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(uuidText);
                PlayerState state = new PlayerState();
                CompoundTag stateTag = playersTag.getCompound(uuidText);
                ListTag flags = stateTag.getList(NBT_FLAGS, Tag.TAG_STRING);
                for (int index = 0; index < flags.size(); index++) {
                    String flag = normalizeKey(flags.getString(index));
                    if (!flag.isBlank()) {
                        state.flags.add(flag);
                    }
                }
                CompoundTag choices = stateTag.getCompound(NBT_CHOICES);
                for (String key : choices.getAllKeys()) {
                    String normalizedKey = normalizeKey(key);
                    String value = choices.getString(key);
                    if (!normalizedKey.isBlank()) {
                        state.choices.put(normalizedKey, value);
                    }
                }
                data.players.put(uuid, state);
            } catch (Exception ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag playersTag = new CompoundTag();
        for (Map.Entry<UUID, PlayerState> entry : this.players.entrySet()) {
            CompoundTag stateTag = new CompoundTag();
            ListTag flags = new ListTag();
            for (String flag : entry.getValue().flags) {
                flags.add(StringTag.valueOf(flag));
            }
            stateTag.put(NBT_FLAGS, flags);

            CompoundTag choices = new CompoundTag();
            for (Map.Entry<String, String> choice : entry.getValue().choices.entrySet()) {
                choices.putString(choice.getKey(), choice.getValue());
            }
            stateTag.put(NBT_CHOICES, choices);
            playersTag.put(entry.getKey().toString(), stateTag);
        }
        tag.put(NBT_PLAYERS, playersTag);
        return tag;
    }

    public boolean hasFlag(UUID playerUuid, String flag) {
        String key = normalizeKey(flag);
        if (key.isBlank()) return false;
        return state(playerUuid).flags.contains(key);
    }

    public void setFlag(UUID playerUuid, String flag) {
        String key = normalizeKey(flag);
        if (key.isBlank()) return;
        state(playerUuid).flags.add(key);
        setDirty();
    }

    public void clearFlag(UUID playerUuid, String flag) {
        String key = normalizeKey(flag);
        if (key.isBlank()) return;
        state(playerUuid).flags.remove(key);
        setDirty();
    }

    public boolean hasChoice(UUID playerUuid, String key) {
        String normalized = normalizeKey(key);
        if (normalized.isBlank()) return false;
        return state(playerUuid).choices.containsKey(normalized);
    }

    public void recordChoice(UUID playerUuid, String key, String value) {
        String normalized = normalizeKey(key);
        if (normalized.isBlank()) return;
        state(playerUuid).choices.put(normalized, value == null ? "" : value);
        setDirty();
    }

    private PlayerState state(UUID playerUuid) {
        return this.players.computeIfAbsent(playerUuid, ignored -> new PlayerState());
    }

    public static String normalizeKey(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.isBlank() || value.length() > 120 || value.contains(" ")) {
            return "";
        }
        return value;
    }

    private static final class PlayerState {
        private final Set<String> flags = new HashSet<>();
        private final Map<String, String> choices = new HashMap<>();
    }
}
