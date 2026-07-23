package net.aviel.dialogue.npc.poi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every point of interest in the world, saved with the level so markers survive restarts.
 * Ids are unique world-wide: placing a marker with an existing id moves that point.
 */
public class NpcPoiData extends SavedData {
    private static final String DATA_NAME = "adm_points_of_interest";
    private static final String NBT_POINTS = "points";

    private final Map<String, PointOfInterest> points = new LinkedHashMap<>();

    public static NpcPoiData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NpcPoiData::new, NpcPoiData::load),
                DATA_NAME);
    }

    public static NpcPoiData load(CompoundTag tag, HolderLookup.Provider registries) {
        NpcPoiData data = new NpcPoiData();
        ListTag list = tag.getList(NBT_POINTS, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            PointOfInterest point = PointOfInterest.load(list.getCompound(index));
            if (point != null) {
                data.points.put(point.id(), point);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (PointOfInterest point : points.values()) {
            list.add(point.save());
        }
        tag.put(NBT_POINTS, list);
        return tag;
    }

    /** Adds or moves a point. Returns the stored value, or {@code null} for an invalid id. */
    public PointOfInterest put(String rawId, BlockPos pos, ResourceKey<Level> dimension) {
        String id = PointOfInterest.normalizeId(rawId);
        if (id.isBlank()) {
            return null;
        }

        PointOfInterest point = new PointOfInterest(id, pos.immutable(), dimension);
        points.put(id, point);
        setDirty();
        return point;
    }

    public PointOfInterest find(String rawId) {
        String id = PointOfInterest.normalizeId(rawId);
        return id.isBlank() ? null : points.get(id);
    }

    public boolean remove(String rawId) {
        String id = PointOfInterest.normalizeId(rawId);
        if (id.isBlank() || points.remove(id) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    /** Point nearest to {@code pos} within {@code maxDistance}, in the same dimension. */
    public PointOfInterest nearest(BlockPos pos, ResourceKey<Level> dimension, double maxDistance) {
        double maxSqr = maxDistance * maxDistance;
        return points.values().stream()
                .filter(point -> point.dimension() == dimension)
                .filter(point -> point.pos().distSqr(pos) <= maxSqr)
                .min(Comparator.comparingDouble(point -> point.pos().distSqr(pos)))
                .orElse(null);
    }

    public List<String> ids() {
        List<String> result = new ArrayList<>(points.keySet());
        result.sort(Comparator.naturalOrder());
        return result;
    }

    public List<PointOfInterest> all() {
        return List.copyOf(points.values());
    }

    /** Suggests {@code poi_1}, {@code poi_2}… so a marker click needs no typing. */
    public String nextFreeId() {
        for (int index = 1; ; index++) {
            String candidate = "poi_" + index;
            if (!points.containsKey(candidate)) {
                return candidate;
            }
        }
    }
}
