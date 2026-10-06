package com.piotrek.groundworksexcavator;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HydraulicHammerKinematicsTest {

    @Test
    @DisplayName("Hydraulic hammer logical tip matches the rendered maximum-stroke chisel tip")
    void hammerTipUsesExactModelStrokeLength() {
        BucketPose pose = ArmKinematics.computeBucketPose(
                Vec3.ZERO,
                0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F,
                2
        );

        double expectedLength = Math.abs(ArmKinematics.HAMMER_TIP_STRIKE_Z_PX) / 16.0D;
        assertEquals(expectedLength, pose.pivot().distanceTo(pose.cuttingEdge()), 1.0E-5D);
        assertEquals(1, pose.teethPoints().size());
        assertEquals(pose.cuttingEdge(), pose.teethPoints().getFirst());
        assertEquals(1.0D, pose.forwardCutting().length(), 1.0E-6D);
    }
}
