package net.aviel.dialogue.npc.trade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.List;

public final class NpcTradeDefinition {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String title;
    private final String subtitle;
    private final TradeStyle style;
    private final List<Offer> offers;

    private NpcTradeDefinition(String title, String subtitle, TradeStyle style, List<Offer> offers) {
        this.title = clean(title, 80).isBlank() ? "Trade" : clean(title, 80);
        this.subtitle = clean(subtitle, 180);
        this.style = style == null ? TradeStyle.DEFAULT : style;
        this.offers = List.copyOf(offers == null ? List.of() : offers);
    }

    public static NpcTradeDefinition fromJson(String json) throws JsonSyntaxException {
        JsonElement rootElement = JsonParser.parseString(json == null ? "" : json);
        if (!rootElement.isJsonObject()) {
            throw new JsonSyntaxException("Trade root must be a JSON object.");
        }
        JsonObject root = rootElement.getAsJsonObject();
        String title = readString(root, "title", readString(root, "name", "Trade"));
        String subtitle = readString(root, "subtitle", readString(root, "description", ""));
        TradeStyle style = readStyle(root);
        List<Offer> offers = readOffers(root.get("offers"));
        if (offers.isEmpty()) {
            offers = readOffers(root.get("trades"));
        }
        if (offers.isEmpty()) {
            throw new JsonSyntaxException("Trade must contain at least one offer.");
        }
        return new NpcTradeDefinition(title, subtitle, style, offers);
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    public TradeStyle style() {
        return style;
    }

    public List<Offer> offers() {
        return offers;
    }

    public Offer offerByIdOrIndex(String offerId, int index) {
        String normalized = clean(offerId, 80);
        if (!normalized.isBlank()) {
            for (Offer offer : offers) {
                if (offer.id().equals(normalized)) {
                    return offer;
                }
            }
        }
        return index >= 0 && index < offers.size() ? offers.get(index) : null;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    private static List<Offer> readOffers(JsonElement element) {
        List<Offer> offers = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return offers;
        }
        JsonArray array = element.getAsJsonArray();
        int index = 0;
        for (JsonElement offerElement : array) {
            if (offerElement == null || !offerElement.isJsonObject()) {
                index++;
                continue;
            }
            Offer offer = readOffer(offerElement.getAsJsonObject(), index);
            if (offer != null) {
                offers.add(offer);
            }
            index++;
        }
        return offers;
    }

    private static Offer readOffer(JsonObject object, int index) {
        String id = clean(readString(object, "id", ""), 80);
        if (id.isBlank()) {
            id = "offer_" + index;
        }
        String title = clean(readString(object, "title", readString(object, "name", "")), 80);
        String description = clean(readString(object, "description", readString(object, "lore", "")), 360);
        String category = clean(readString(object, "category", ""), 80);
        List<TradeItem> cost = readItems(firstPresent(object, "cost", "price", "buy", "take", "take_items"));
        List<TradeItem> result = readItems(firstPresent(object, "result", "results", "sell", "give", "give_items", "receive"));
        if (cost.isEmpty() || result.isEmpty()) {
            return null;
        }
        if (title.isBlank()) {
            title = result.get(0).item();
        }
        return new Offer(
                index,
                id,
                title,
                description,
                category,
                cost,
                result,
                readStringList(object, "requires_flags", "requires_flag", 80),
                readStringList(object, "missing_flags", "missing_flag", 80),
                readStringList(object, "requires_tags", "requires_tag", 64),
                readStringList(object, "missing_tags", "missing_tag", 64),
                readStringList(object, "commands", "command", 2000),
                readBoolean(object, "locked", false)
        );
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        JsonElement value = object.get(key);
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : fallback;
    }

    private static JsonElement firstPresent(JsonObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) {
                return value;
            }
        }
        return null;
    }

    private static List<TradeItem> readItems(JsonElement element) {
        List<TradeItem> items = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return items;
        }
        if (element.isJsonArray()) {
            for (JsonElement itemElement : element.getAsJsonArray()) {
                TradeItem item = readItem(itemElement);
                if (item != null) {
                    items.add(item);
                }
            }
            return items;
        }
        TradeItem item = readItem(element);
        if (item != null) {
            items.add(item);
        }
        return items;
    }

    private static TradeItem readItem(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            String item = clean(element.getAsString(), 160);
            return item.isBlank() ? null : new TradeItem(item, 1);
        }
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        String item = clean(readString(object, "item", readString(object, "id", "")), 160);
        if (item.isBlank()) {
            return null;
        }
        int count = Math.max(1, Math.min(999, readInt(object, "count", readInt(object, "amount", 1))));
        return new TradeItem(item, count);
    }

    private static TradeStyle readStyle(JsonObject root) {
        JsonObject source = root;
        if (root != null && root.has("style") && root.get("style").isJsonObject()) {
            source = root.getAsJsonObject("style");
        }
        TradeStyle fallback = TradeStyle.DEFAULT;
        return new TradeStyle(
                clean(readString(source, "outer_color", fallback.outerColor()), 16),
                clean(readString(source, "panel_color", fallback.panelColor()), 16),
                clean(readString(source, "list_color", fallback.listColor()), 16),
                clean(readString(source, "details_color", fallback.detailsColor()), 16),
                clean(readString(source, "accent_color", fallback.accentColor()), 16),
                clean(readString(source, "button_color", fallback.buttonColor()), 16),
                clean(readString(source, "button_hover_color", fallback.buttonHoverColor()), 16),
                clean(readString(source, "button_disabled_color", fallback.buttonDisabledColor()), 16),
                clean(readString(source, "text_color", fallback.textColor()), 16),
                clean(readString(source, "muted_text_color", fallback.mutedTextColor()), 16),
                clean(readString(source, "price_color", fallback.priceColor()), 16),
                clean(readString(source, "reward_color", fallback.rewardColor()), 16)
        );
    }

    private static List<String> readStringList(JsonObject object, String arrayKey, String singleKey, int maxLength) {
        List<String> values = new ArrayList<>();
        if (object == null) {
            return values;
        }
        JsonElement arrayElement = object.get(arrayKey);
        if (arrayElement != null && arrayElement.isJsonArray()) {
            for (JsonElement valueElement : arrayElement.getAsJsonArray()) {
                if (valueElement == null || valueElement.isJsonNull()) continue;
                String value = clean(valueElement.getAsString(), maxLength);
                if (!value.isBlank()) values.add(value);
            }
        }
        String single = clean(readString(object, singleKey, ""), maxLength);
        if (!single.isBlank()) {
            values.add(single);
        }
        return values;
    }

    private static String readString(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String clean(String value, int maxLength) {
        if (value == null) return "";
        String clean = value.replace('\r', '\n').trim();
        while (clean.contains("\n\n\n")) {
            clean = clean.replace("\n\n\n", "\n\n");
        }
        return clean.length() > maxLength ? clean.substring(0, maxLength) : clean;
    }

    public record Offer(
            int index,
            String id,
            String title,
            String description,
            String category,
            List<TradeItem> cost,
            List<TradeItem> result,
            List<String> requiresFlags,
            List<String> missingFlags,
            List<String> requiresTags,
            List<String> missingTags,
            List<String> commands,
            boolean locked
    ) {
        public Offer {
            index = Math.max(0, index);
            id = clean(id, 80);
            title = clean(title, 80);
            description = clean(description, 360);
            category = clean(category, 80);
            cost = List.copyOf(cost == null ? List.of() : cost);
            result = List.copyOf(result == null ? List.of() : result);
            requiresFlags = List.copyOf(requiresFlags == null ? List.of() : requiresFlags);
            missingFlags = List.copyOf(missingFlags == null ? List.of() : missingFlags);
            requiresTags = List.copyOf(requiresTags == null ? List.of() : requiresTags);
            missingTags = List.copyOf(missingTags == null ? List.of() : missingTags);
            commands = List.copyOf(commands == null ? List.of() : commands);
        }
    }

    public record TradeItem(String item, int count) {
        public TradeItem {
            item = clean(item, 160);
            count = Math.max(1, Math.min(999, count));
        }

        public int multipliedCount(int amount) {
            return Math.max(1, Math.min(64000, count * Math.max(1, amount)));
        }
    }

    public record TradeStyle(
            String outerColor,
            String panelColor,
            String listColor,
            String detailsColor,
            String accentColor,
            String buttonColor,
            String buttonHoverColor,
            String buttonDisabledColor,
            String textColor,
            String mutedTextColor,
            String priceColor,
            String rewardColor
    ) {
        public static final TradeStyle DEFAULT = new TradeStyle(
                "#22000000",
                "#C0100714",
                "#A0160A20",
                "#B0180C24",
                "#B78CFF",
                "#5C2B123A",
                "#87411B58",
                "#55222222",
                "#F2E7FF",
                "#B8A7C8",
                "#FFD36B",
                "#A9FFCF"
        );

        public TradeStyle {
            outerColor = clean(outerColor, 16);
            panelColor = clean(panelColor, 16);
            listColor = clean(listColor, 16);
            detailsColor = clean(detailsColor, 16);
            accentColor = clean(accentColor, 16);
            buttonColor = clean(buttonColor, 16);
            buttonHoverColor = clean(buttonHoverColor, 16);
            buttonDisabledColor = clean(buttonDisabledColor, 16);
            textColor = clean(textColor, 16);
            mutedTextColor = clean(mutedTextColor, 16);
            priceColor = clean(priceColor, 16);
            rewardColor = clean(rewardColor, 16);
        }
    }
}
