package net.aviel.dialogue.client;

import net.aviel.dialogue.npc.trade.NpcTradeDefinition;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Client-side item lookups for the trade screen. */
final class NpcTradeItems {
    private NpcTradeItems() {
    }

    static Item resolveItem(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        ResourceLocation id = ResourceLocation.tryParse(itemId.trim());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? null : item;
    }

    static ItemStack stackFor(NpcTradeDefinition.TradeItem item) {
        if (item == null) {
            return ItemStack.EMPTY;
        }
        Item resolved = resolveItem(item.item());
        return resolved == null ? ItemStack.EMPTY : new ItemStack(resolved, Math.min(64, item.count()));
    }

    static ItemStack firstStack(List<NpcTradeDefinition.TradeItem> items) {
        return items == null || items.isEmpty() ? ItemStack.EMPTY : stackFor(items.get(0));
    }

    static String displayName(NpcTradeDefinition.TradeItem item) {
        ItemStack stack = stackFor(item);
        return stack.isEmpty() ? item.item() : stack.getHoverName().getString();
    }

    static int countInventory(LocalPlayer player, Item item) {
        if (player == null || item == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) count += stack.getCount();
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    static boolean canAfford(LocalPlayer player, NpcTradeDefinition.Offer offer, int amount) {
        if (player == null || offer == null) {
            return false;
        }
        for (NpcTradeDefinition.TradeItem item : offer.cost()) {
            Item resolved = resolveItem(item.item());
            if (resolved == null || countInventory(player, resolved) < item.multipliedCount(amount)) {
                return false;
            }
        }
        return true;
    }
}
