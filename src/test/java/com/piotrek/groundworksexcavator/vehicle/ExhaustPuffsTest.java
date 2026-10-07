package com.piotrek.groundworksexcavator.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExhaustPuffsTest {

    @Test
    void idleAndHeavyLoadEmitNoLightPuffs() {
        assertEquals(0, ExhaustPuffs.whitePuffCount(0.0F, 0));
        assertEquals(0, ExhaustPuffs.whitePuffCount(0.04F, 16));
        assertEquals(0, ExhaustPuffs.whitePuffCount(0.21F, 1));
        assertEquals(0, ExhaustPuffs.whitePuffCount(1.0F, 0));
    }

    @Test
    void fivePercentIsARareSinglePuff() {
        assertEquals(1, ExhaustPuffs.whitePuffCount(0.05F, 0));
        assertEquals(0, ExhaustPuffs.whitePuffCount(0.05F, 1));
        assertEquals(0, ExhaustPuffs.whitePuffCount(0.05F, 15));
        assertEquals(1, ExhaustPuffs.whitePuffCount(0.05F, 16));
    }

    @Test
    void twentyPercentEmitsManySmallPuffsEveryTick() {
        int atFive = ExhaustPuffs.whitePuffCount(0.05F, 0);
        int atTwenty = ExhaustPuffs.whitePuffCount(0.20F, 7);
        assertTrue(atTwenty > atFive);
        assertEquals(4, atTwenty);
        assertEquals(4, ExhaustPuffs.whitePuffCount(0.20F, 1));
    }
}
