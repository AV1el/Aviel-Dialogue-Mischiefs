package net.aviel.dialogue.npc.trade;

import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NpcTradeDefinitionTest {
    private static final String SHOP = """
            {
              "title": "Blacksmith Shop",
              "subtitle": "Steel for coin.",
              "offers": [
                {
                  "id": "iron_sword",
                  "title": "Iron Sword",
                  "cost": [ { "item": "minecraft:iron_ingot", "count": 3 } ],
                  "result": [ { "item": "minecraft:iron_sword", "count": 1 } ]
                },
                {
                  "title": "Secret Blade",
                  "requires_flags": ["blacksmith_ore_done"],
                  "cost": [ { "item": "minecraft:diamond", "count": 2 } ],
                  "result": [ { "item": "minecraft:diamond_sword" } ]
                }
              ]
            }
            """;

    @Test
    void parsesOffersWithCostsAndConditions() {
        NpcTradeDefinition definition = NpcTradeDefinition.fromJson(SHOP);

        assertEquals("Blacksmith Shop", definition.title());
        assertEquals("Steel for coin.", definition.subtitle());
        assertEquals(2, definition.offers().size());

        NpcTradeDefinition.Offer first = definition.offers().get(0);
        assertEquals("iron_sword", first.id());
        assertEquals(1, first.cost().size());
        assertEquals("minecraft:iron_ingot", first.cost().get(0).item());
        assertEquals(3, first.cost().get(0).count());

        NpcTradeDefinition.Offer second = definition.offers().get(1);
        assertEquals("offer_1", second.id());
        assertEquals(java.util.List.of("blacksmith_ore_done"), second.requiresFlags());
    }

    @Test
    void findsOfferByIdBeforeIndex() {
        NpcTradeDefinition definition = NpcTradeDefinition.fromJson(SHOP);
        assertEquals("iron_sword", definition.offerByIdOrIndex("iron_sword", 1).id());
        assertEquals("offer_1", definition.offerByIdOrIndex("", 1).id());
        assertNull(definition.offerByIdOrIndex("", 99));
    }

    @Test
    void multipliedCountScalesWithAmount() {
        NpcTradeDefinition.TradeItem cost = NpcTradeDefinition.fromJson(SHOP).offers().get(0).cost().get(0);
        assertEquals(9, cost.multipliedCount(3));
        assertNotNull(cost.item());
    }

    @Test
    void rejectsShopWithoutOffers() {
        assertThrows(JsonSyntaxException.class, () -> NpcTradeDefinition.fromJson("{ \"title\": \"Empty\" }"));
        assertThrows(JsonSyntaxException.class, () -> NpcTradeDefinition.fromJson("not json"));
    }
}
