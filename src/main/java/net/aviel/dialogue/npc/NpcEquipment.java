package net.aviel.dialogue.npc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.aviel.dialogue.entity.DialogueNpcEntity;
import net.aviel.dialogue.util.JsonReaders;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Slot naming, JSON mapping and item transfer for NPC armor and held items. */
public final class NpcEquipment {
    /** Slots in the order they are shown to users. */
    public static final List<EquipmentSlot> SLOTS = List.of(
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    );

    private NpcEquipment() {
    }

    /** Canonical lowercase name used in JSON and commands. */
    public static String slotName(EquipmentSlot slot) {
        return slot.getName().toLowerCase(Locale.ROOT);
    }

    public static List<String> slotNames() {
        return SLOTS.stream().map(NpcEquipment::slotName).toList();
    }

    /** Accepts canonical names plus common aliases; returns {@code null} when unknown. */
    public static EquipmentSlot parseSlot(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "mainhand", "hand", "main", "weapon", "held" -> EquipmentSlot.MAINHAND;
            case "offhand", "off", "shield" -> EquipmentSlot.OFFHAND;
            case "head", "helmet", "hat" -> EquipmentSlot.HEAD;
            case "chest", "chestplate", "body" -> EquipmentSlot.CHEST;
            case "legs", "leggings", "pants" -> EquipmentSlot.LEGS;
            case "feet", "boots", "shoes" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    public static void clearAll(DialogueNpcEntity npc) {
        for (EquipmentSlot slot : SLOTS) {
            npc.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    /** Copies every slot from {@code source} onto {@code target}. */
    public static void copy(DialogueNpcEntity source, DialogueNpcEntity target) {
        for (EquipmentSlot slot : SLOTS) {
            target.setItemSlot(slot, source.getItemBySlot(slot).copy());
        }
    }

    public static boolean hasAny(DialogueNpcEntity npc) {
        for (EquipmentSlot slot : SLOTS) {
            if (!npc.getItemBySlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** One resolved slot assignment from a template. */
    public record Entry(EquipmentSlot slot, ItemStack stack) {
    }

    /**
     * Reads an {@code equipment} object from a template. Slot values are either an item id
     * string or an object with {@code item} and {@code count}; unknown slots and items are skipped.
     */
    public static List<Entry> readFromJson(JsonObject root) {
        List<Entry> entries = new ArrayList<>();
        if (root == null || !root.has("equipment") || !root.get("equipment").isJsonObject()) {
            return entries;
        }
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("equipment").entrySet()) {
            EquipmentSlot slot = parseSlot(entry.getKey());
            if (slot == null) {
                continue;
            }
            ItemStack stack = readStack(entry.getValue());
            if (!stack.isEmpty()) {
                entries.add(new Entry(slot, stack));
            }
        }
        return entries;
    }

    public static void apply(DialogueNpcEntity npc, List<Entry> entries) {
        clearAll(npc);
        if (entries == null) {
            return;
        }
        for (Entry entry : entries) {
            npc.setItemSlot(entry.slot(), entry.stack().copy());
        }
    }

    private static ItemStack readStack(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return ItemStack.EMPTY;
        }
        String itemId;
        int count = 1;
        if (element.isJsonPrimitive()) {
            itemId = element.getAsString();
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            itemId = JsonReaders.readString(object, "item", JsonReaders.readString(object, "id", ""));
            count = Math.max(1, Math.min(64, (int) JsonReaders.readFloat(object, "count", 1.0F)));
        } else {
            return ItemStack.EMPTY;
        }
        Item item = DialogueItemHandler.resolveItem(itemId);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    /** Serializes the NPC's non-empty slots back into template JSON form. */
    public static JsonObject toJson(DialogueNpcEntity npc) {
        JsonObject equipment = new JsonObject();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = npc.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (stack.getCount() <= 1) {
                equipment.addProperty(slotName(slot), itemId);
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("item", itemId);
            entry.addProperty("count", stack.getCount());
            equipment.add(slotName(slot), entry);
        }
        return equipment;
    }

    /** Human-readable summary of filled slots, e.g. {@code mainhand=Iron Sword, head=Diamond Helmet}. */
    public static Map<String, String> summary(DialogueNpcEntity npc) {
        Map<String, String> result = new LinkedHashMap<>();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = npc.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                String name = stack.getHoverName().getString();
                result.put(slotName(slot), stack.getCount() > 1 ? stack.getCount() + "x " + name : name);
            }
        }
        return result;
    }
}
