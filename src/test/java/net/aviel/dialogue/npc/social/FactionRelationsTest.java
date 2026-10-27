package net.aviel.dialogue.npc.social;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FactionRelationsTest {
    @Test
    void relationshipsAreSymmetricAndNeutralByDefault() {
        var factions = FactionRelations.parse(JsonParser.parseString("""
                {"factions":{"wardens":{"relations":{"bandits":"hostile","merchants":"allied"}},
                 "bandits":{},"merchants":{}}}
                """).getAsJsonObject());
        assertEquals(FactionRelations.Relation.HOSTILE, factions.relation("bandits", "wardens"));
        assertEquals(FactionRelations.Relation.ALLIED, factions.relation("merchants", "wardens"));
        assertEquals(FactionRelations.Relation.ALLIED, factions.relation("wardens", "wardens"));
        assertEquals(FactionRelations.Relation.NEUTRAL, factions.relation("bandits", "merchants"));
        assertEquals(FactionRelations.Relation.NEUTRAL, factions.relation("", ""));
    }

    @Test
    void rejectsConflictsAndUnknownFactions() {
        for (String json : new String[]{
                "{\"factions\":{\"a\":{\"relations\":{\"missing\":\"hostile\"}}}}",
                "{\"factions\":{\"a\":{\"relations\":{\"b\":\"hostile\"}},\"b\":{\"relations\":{\"a\":\"allied\"}}}}"}) {
            assertThrows(IllegalArgumentException.class, () -> FactionRelations.parse(JsonParser.parseString(json).getAsJsonObject()));
        }
    }
}
