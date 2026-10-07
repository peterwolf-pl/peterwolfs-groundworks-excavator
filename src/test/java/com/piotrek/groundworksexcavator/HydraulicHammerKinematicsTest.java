package com.piotrek.groundworksexcavator;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    @DisplayName("Hammer limits prevent clipping into stick and allow wide outward tilt")
    void hammerArticulationRangeLimits() {
        assertEquals(12.0F, ArmKinematics.HAMMER_MIN, 1.0E-4F);
        assertEquals(160.0F, ArmKinematics.HAMMER_MAX, 1.0E-4F);

        assertEquals(ArmKinematics.HAMMER_MIN, ArmKinematics.getMinBucketAngle(2));
        assertEquals(ArmKinematics.HAMMER_MAX, ArmKinematics.getMaxBucketAngle(2));

        assertEquals(ArmKinematics.BUCKET_MIN, ArmKinematics.getMinBucketAngle(0));
        assertEquals(ArmKinematics.BUCKET_MAX, ArmKinematics.getMaxBucketAngle(0));
        assertEquals(ArmKinematics.BUCKET_MIN, ArmKinematics.getMinBucketAngle(1));
        assertEquals(ArmKinematics.BUCKET_MAX, ArmKinematics.getMaxBucketAngle(1));

        // When tilted to maximum outward angle (+160°), the chisel points forward and upward
        BucketPose maxOutwardPose = ArmKinematics.computeBucketPose(
                Vec3.ZERO,
                0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F,
                ArmKinematics.HAMMER_MAX,
                2
        );
        // Pivot is at world (0, y, z), chisel tip extends forward into positive Z
        assertTrue(maxOutwardPose.cuttingEdge().z > maxOutwardPose.pivot().z,
                "Chisel tip must extend forward in front of the bucket pivot at maximum outward tilt");
    }
}
