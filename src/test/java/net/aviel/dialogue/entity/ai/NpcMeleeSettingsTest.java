package net.aviel.dialogue.entity.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcMeleeSettingsTest {
    @Test
    void oldProfilesKeepDefaultsAndNewSettingsRoundTrip() {
        assertEquals(NpcMeleeSettings.DEFAULT, NpcCombatProfile.fromJson(new JsonObject()).melee());
        var profile = NpcCombatProfile.fromJson(JsonParser.parseString("""
                {"attack_range":4.5,"knockback":1.2,"knockback_resistance":0.7}
                """).getAsJsonObject());
        assertEquals(new NpcMeleeSettings(4.5, 1.2, 0.7), profile.melee());
        assertEquals(profile, NpcCombatProfile.fromJson(profile.toJson()));
    }

    @Test
    void reachUsesClosestPointOnTargetHitboxIncludingHeight() {
        var settings = new NpcMeleeSettings(3, 0, 0);
        var target = new AABB(3, 0, -0.3, 3.6, 1.8, 0.3);
        assertTrue(settings.canReach(target, new Vec3(0, 1.62, 0)));
        assertFalse(settings.canReach(target, new Vec3(-0.01, 1.62, 0)));
        assertFalse(settings.canReach(target, new Vec3(0, 5, 0)));
    }

    @Test
    void invalidReachAndKnockbackAreRejected() {
        for (double invalid : new double[]{0, 6.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new NpcMeleeSettings(invalid, 0, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> new NpcMeleeSettings(3, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new NpcMeleeSettings(3, 5.1, 0));
        assertThrows(IllegalArgumentException.class, () -> new NpcMeleeSettings(3, 0, 1.1));
    }
}
