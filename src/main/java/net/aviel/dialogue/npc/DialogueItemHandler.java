package net.aviel.dialogue.npc;

import net.aviel.dialogue.npc.dialogue.NpcDialogueDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class DialogueItemHandler {
    private DialogueItemHandler() {
    }

    public static Item resolveItem(String rawItemId) {
        ResourceLocation id = ResourceLocation.tryParse(rawItemId == null ? "" : rawItemId.trim());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? null : item;
    }

    public static boolean hasItemCount(ServerPlayer player, Item item, int count) {
        if (player == null || item == null || count <= 0) {
            return false;
        }
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                found += stack.getCount();
                if (found >= count) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean removeItems(ServerPlayer player, Item item, int count) {
        if (!hasItemCount(player, item, count)) {
            return false;
        }
        int remaining = count;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.is(item)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
            if (remaining <= 0) {
                break;
            }
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return true;
    }

    public static boolean hasItemRules(ServerPlayer player, List<NpcDialogueDefinition.ItemRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        for (NpcDialogueDefinition.ItemRule rule : rules) {
            Item item = resolveItem(rule.item());
            if (item == null || !hasItemCount(player, item, rule.count())) {
                return false;
            }
        }
        return true;
    }

    public static boolean removeItemRules(ServerPlayer player, List<NpcDialogueDefinition.ItemRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        if (!hasItemRules(player, rules)) {
            return false;
        }
        for (NpcDialogueDefinition.ItemRule rule : rules) {
            Item item = resolveItem(rule.item());
            if (item == null || !removeItems(player, item, rule.count())) {
                return false;
            }
        }
        return true;
    }

    public static void giveItemRules(ServerPlayer player, List<NpcDialogueDefinition.ItemRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        for (NpcDialogueDefinition.ItemRule rule : rules) {
            Item item = resolveItem(rule.item());
            if (item == null) continue;
            int remaining = rule.count();
            while (remaining > 0) {
                int amount = Math.min(remaining, new ItemStack(item).getMaxStackSize());
                ItemStack stack = new ItemStack(item, amount);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                remaining -= amount;
            }
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
}
