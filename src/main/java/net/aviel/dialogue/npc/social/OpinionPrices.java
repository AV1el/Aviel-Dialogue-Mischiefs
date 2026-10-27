package net.aviel.dialogue.npc.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.aviel.dialogue.npc.trade.NpcTradeDefinition;

import java.util.List;

public final class OpinionPrices {
    private OpinionPrices() {
    }

    public static int count(int original, int percent) {
        if (percent < 0 || percent > 90) throw new IllegalArgumentException("Discount must be 0..90 percent");
        return Math.max(1, (int) (((long) original * (100 - percent) + 99) / 100));
    }

    public static List<NpcTradeDefinition.TradeItem> costs(List<NpcTradeDefinition.TradeItem> cost, int percent) {
        return cost.stream().map(item -> new NpcTradeDefinition.TradeItem(item.item(), count(item.count(), percent))).toList();
    }

    public static String clientJson(String raw, int percent) {
        if (percent == 0) return raw;
        var definition = NpcTradeDefinition.fromJson(raw);
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
        for (String key : List.of("offers", "trades")) {
            if (!root.has(key) || !root.get(key).isJsonArray()) continue;
            int index = 0;
            for (var element : root.getAsJsonArray(key)) {
                if (element.isJsonObject()) {
                    var offer = element.getAsJsonObject();
                    String id = offer.has("id") ? offer.get("id").getAsString().trim() : "";
                    var parsed = definition.offerByIdOrIndex(id.isEmpty() ? "offer_" + index : id, -1);
                    if (parsed != null) {
                        JsonArray cost = new JsonArray();
                        for (var item : costs(parsed.cost(), percent)) {
                            JsonObject entry = new JsonObject();
                            entry.addProperty("item", item.item());
                            entry.addProperty("count", item.count());
                            cost.add(entry);
                        }
                        offer.add("cost", cost);
                    }
                }
                index++;
            }
        }
        return root.toString();
    }
}
