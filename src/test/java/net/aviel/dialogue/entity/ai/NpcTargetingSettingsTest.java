package net.aviel.dialogue.entity.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcTargetingSettingsTest {
    @Test
    void ownerThreatOverridesSelfDefenseButCooldownPreventsThrashing() {
        assertTrue(NpcTargetingSettings.shouldSwitch(0, 2, true));
        assertTrue(NpcTargetingSettings.shouldSwitch(2, 3, true));
        assertFalse(NpcTargetingSettings.shouldSwitch(3, 2, true));
        assertFalse(NpcTargetingSettings.shouldSwitch(2, 2, true));
        assertFalse(NpcTargetingSettings.shouldSwitch(0, 3, false));
    }

    @Test
    void roundTripsSettingsAndRejectsInvalidDelays() {
        var settings = new NpcTargetingSettings(false, true, 40, 80);
        assertEquals(settings, NpcTargetingSettings.fromJson(settings.toJson()));
        assertEquals(NpcTargetingSettings.DEFAULT, NpcTargetingSettings.fromJson(new JsonObject()));
        for (String value : new String[]{"-1", "201", "0.5", "\"NaN\""}) {
            assertThrows(IllegalArgumentException.class, () -> NpcTargetingSettings.fromJson(
                    JsonParser.parseString("{\"switch_cooldown\":" + value + "}").getAsJsonObject()));
        }
    }
}
