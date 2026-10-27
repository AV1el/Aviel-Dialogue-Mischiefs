package net.aviel.dialogue.npc.social;

import net.aviel.dialogue.npc.trade.NpcTradeDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpinionPricesTest {
    @Test
    void clientQuoteAndServerCostsUseIdenticalRounding() {
        String json = """
                {"trades":[{"price":[{"id":"minecraft:emerald","amount":9},"minecraft:diamond"],
                 "give":{"item":"minecraft:iron_sword","count":1},"requires_flags":["trusted"]}]}
                """;
        var base = NpcTradeDefinition.fromJson(json).offers().getFirst();
        var quoted = NpcTradeDefinition.fromJson(OpinionPrices.clientJson(json, 20)).offers().getFirst();
        assertEquals(OpinionPrices.costs(base.cost(), 20), quoted.cost());
        assertEquals(8, quoted.cost().getFirst().count());
        assertEquals(1, quoted.cost().get(1).count());
        assertEquals(base.result(), quoted.result());
        assertEquals(base.requiresFlags(), quoted.requiresFlags());
        assertEquals(24, quoted.cost().getFirst().multipliedCount(3));
        assertEquals(1, OpinionPrices.count(1, 90));
        assertEquals(json, OpinionPrices.clientJson(json, 0));
    }
}
