package com.piotrek.groundworksexcavator.excavation;

import com.piotrek.groundworks.api.material.GranularMaterial;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HydraulicHammerControllerTest {

    @Test
    @DisplayName("Hammer only accepts cobblestone granular material")
    void acceptsOnlyCobblestone() {
        GranularMaterial cobble = new GranularMaterial(
                4, "cobblestone", () -> null, 2200f, 42f, 0.35f, 0.2f);
        GranularMaterial gravel = new GranularMaterial(
                3, "gravel", () -> null, 1800f, 38f, 0.2f, 0.4f);

        assertTrue(HydraulicHammerController.acceptsMaterial(cobble));
        assertFalse(HydraulicHammerController.acceptsMaterial(gravel));
    }

    @Test
    @DisplayName("Debris is deposited opposite the hammer penetration direction")
    void debrisDirectionIsOppositeImpact() {
        assertEquals(Direction.WEST,
                HydraulicHammerController.outwardDirection(new Vec3(1.0D, 0.0D, 0.0D)));
        assertEquals(Direction.UP,
                HydraulicHammerController.outwardDirection(new Vec3(0.0D, -1.0D, 0.0D)));
    }
}
