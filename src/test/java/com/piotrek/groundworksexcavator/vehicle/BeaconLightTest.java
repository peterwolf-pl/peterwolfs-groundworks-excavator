package com.piotrek.groundworksexcavator.vehicle;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeaconLightTest {

    @Test
    void halfTurnReversesTheLampFacing() {
        Vec3 start = ArmKinematics.getBeaconLampFacing(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        Vec3 opposite = ArmKinematics.getBeaconLampFacing(0.0F, 0.0F, 0.0F, 0.0F, (float) Math.PI);
        assertTrue(start.x * opposite.x + start.z * opposite.z < 0.0D);
    }

    @Test
    void lightSitsAheadOfTheLampNotOnIt() {
        List<BlockPos> lights = BeaconLight.lightPositions(new Vec3(0.0D, 3.0D, 0.0D), Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D));
        assertEquals(6, lights.size());
        for (BlockPos pos : lights) {
            assertTrue(pos.getZ() >= BeaconLight.RANGES[0]);
        }
    }
}
