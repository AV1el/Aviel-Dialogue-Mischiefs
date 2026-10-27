package net.aviel.dialogue.entity.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NpcCombatProfileTest {
    @Test
    void defaultsAndRoundTripKeepCombatRules() {
        assertEquals(NpcCombatProfile.DEFAULT, NpcCombatProfile.fromJson(new JsonObject()));
        assertEquals(4, NpcCombatProfile.fromJson(new JsonObject()).attackSpeed());
        var json = JsonParser.parseString("""
                {"health":40,"damage":6,"armor":8,"speed":0.3,"radius":16,
                "policy":"defend_owner","targets":["zombie","minecraft:husk"],"movement_multiplier":0.8,"attack_speed":5}
                """).getAsJsonObject();
        var profile = NpcCombatProfile.fromJson(json);
        assertEquals(40, profile.health());
        assertEquals(5, profile.attackSpeed());
        assertEquals(NpcCombatProfile.Policy.DEFEND_OWNER, profile.policy());
        assertEquals("minecraft:zombie", profile.targets().getFirst());
        assertEquals(profile, NpcCombatProfile.fromJson(profile.toJson()));
        var updated = profile.toJson();
        updated.add("targeting", new NpcTargetingSettings(true, false, 60, 100).toJson());
        var custom = NpcCombatProfile.fromJson(updated);
        assertEquals(100, custom.targeting().lostSightTicks());
        assertEquals(custom, NpcCombatProfile.fromJson(custom.toJson()));
    }

    @Test
    void rejectsInvalidOrNonFiniteNumbersAndBadRules() {
        for (String value : new String[]{"0", "-1", "1025", "\"NaN\"", "\"Infinity\""}) {
            assertThrows(IllegalArgumentException.class, () -> NpcCombatProfile.fromJson(
                    JsonParser.parseString("{\"health\":" + value + "}").getAsJsonObject()));
        }
        assertThrows(IllegalArgumentException.class, () -> NpcCombatProfile.fromJson(
                JsonParser.parseString("{\"policy\":\"unknown\"}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> NpcCombatProfile.fromJson(
                JsonParser.parseString("{\"targets\":[\"bad target\"]}").getAsJsonObject()));
    }

    @Test
    void onlyOwnerCanControlRecruitedCompanion() {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        assertTrue(NpcCompanion.canControl(null, stranger));
        assertTrue(NpcCompanion.canControl(owner, owner));
        assertFalse(NpcCompanion.canControl(owner, stranger));
        assertFalse(NpcCompanion.canControl(owner, null));
    }
}
