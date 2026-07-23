package net.aviel.dialogue.item;

import net.aviel.dialogue.npc.poi.PointOfInterest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The id a marker will use, carried on the stack itself. 1.21 dropped free-form NBT on items,
 * so the custom name doubles as the field: rename the marker in an anvil, or with
 * {@code /npc point marker <id>}, and every placement uses that id.
 */
public final class PoiMarkerName {
    private PoiMarkerName() {
    }

    /** Reads the id from the stack, or {@code ""} when the marker is unnamed. */
    public static String read(ItemStack stack) {
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        return name == null ? "" : PointOfInterest.normalizeId(name.getString());
    }

    /** Writes the id onto the stack; a blank value clears it back to automatic numbering. */
    public static void write(ItemStack stack, String rawId) {
        String id = PointOfInterest.normalizeId(rawId);
        if (id.isBlank()) {
            stack.remove(DataComponents.CUSTOM_NAME);
            return;
        }
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(id));
    }
}
