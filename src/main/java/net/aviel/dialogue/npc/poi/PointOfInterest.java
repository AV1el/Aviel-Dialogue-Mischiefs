package net.aviel.dialogue.npc.poi;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * A named spot in the world an NPC can be sent to. Dialogues reference these by id, so a
 * builder can move the marker without touching any JSON.
 */
public record PointOfInterest(String id, BlockPos pos, ResourceKey<Level> dimension) {
    private static final int MAX_ID_LENGTH = 64;

    /**
     * Ids are lowercase, without spaces, so {@code <moveto:slow:market>} always resolves to the
     * same point no matter how it was typed.
     */
    public static String normalizeId(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (value.isBlank() || value.length() > MAX_ID_LENGTH) {
            return "";
        }
        return value.matches("[a-z0-9_.:-]+") ? value : "";
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        tag.putString("Dimension", dimension.location().toString());
        return tag;
    }

    public static PointOfInterest load(CompoundTag tag) {
        String id = normalizeId(tag.getString("Id"));
        if (id.isBlank()) {
            return null;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimensionId == null) {
            return null;
        }
        return new PointOfInterest(
                id,
                new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")),
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId));
    }

    public String describe() {
        return id + " @ " + pos.toShortString() + " (" + dimension.location() + ")";
    }
}
