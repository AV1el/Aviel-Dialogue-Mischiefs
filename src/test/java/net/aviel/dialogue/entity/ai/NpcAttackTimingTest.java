package net.aviel.dialogue.entity.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcAttackTimingTest {
    @Test
    void followsWeaponAttackSpeedAndBoundsCooldown() {
        assertEquals(5, NpcAttackTiming.cooldownTicks(4));
        assertEquals(13, NpcAttackTiming.cooldownTicks(4 - 2.4));
        assertEquals(20, NpcAttackTiming.cooldownTicks(1));
        assertEquals(200, NpcAttackTiming.cooldownTicks(0));
        assertEquals(200, NpcAttackTiming.cooldownTicks(-1));
        assertEquals(1, NpcAttackTiming.cooldownTicks(100));
    }
}
