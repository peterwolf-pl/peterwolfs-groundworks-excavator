package com.piotrek.groundworksexcavator.vehicle;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BeaconLightTest {

    @Test
    void halfTurnReversesTheLampFacing() {
        Vec3 start = ArmKinematics.getBeaconLampFacing(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        Vec3 opposite = ArmKinematics.getBeaconLampFacing(0.0F, 0.0F, 0.0F, 0.0F, (float) Math.PI);
        assertTrue(start.x * opposite.x + start.z * opposite.z < 0.0D);
    }

}
