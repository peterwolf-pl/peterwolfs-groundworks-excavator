package com.piotrek.groundworksexcavator;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import com.piotrek.groundworksexcavator.excavation.SweptBucketVolume;
import com.piotrek.groundworksexcavator.excavation.SweptBucketVolume.SweptResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SweptVolumeTest {

    @Test
    @DisplayName("Stationary bucket does not generate swept excavation volume")
    void testStationaryBucket() {
        Vec3 base = new Vec3(0.0D, 10.0D, 0.0D);
        BucketPose pose = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F, 10.0F, -20.0F, 0.0F
        );

        SweptResult result = SweptBucketVolume.compute(pose, pose);
        assertFalse(result.valid(), "Stationary bucket must not dig");
        assertTrue(result.hitPositions().isEmpty());
    }

    @Test
    @DisplayName("Teleportation or huge displacement (> 2.5m) is rejected to prevent world gouges")
    void testTeleportationDiscontinuity() {
        Vec3 base1 = new Vec3(0.0D, 10.0D, 0.0D);
        Vec3 base2 = new Vec3(20.0D, 10.0D, 0.0D); // 20m jump

        BucketPose pose1 = ArmKinematics.computeBucketPose(base1, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        BucketPose pose2 = ArmKinematics.computeBucketPose(base2, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

        SweptResult result = SweptBucketVolume.compute(pose1, pose2);
        assertFalse(result.valid(), "Discontinuous jump must be rejected");
        assertTrue(result.hitPositions().isEmpty());
    }

    @Test
    @DisplayName("Forward cutting motion produces candidate swept voxel positions")
    void testForwardCuttingSweep() {
        Vec3 base = new Vec3(0.0D, 10.0D, 0.0D);
        // Previous pose: arm curled back
        BucketPose prev = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -30.0F, 0.0F
        );
        // Current pose: bucket curling forward into soil
        BucketPose curr = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -20.0F, -20.0F
        );

        SweptResult result = SweptBucketVolume.compute(prev, curr);
        assertTrue(result.valid(), "Active forward cutting stroke must be valid");
        assertFalse(result.hitPositions().isEmpty(), "Should produce candidate block positions");
        assertTrue(result.movementDistance() > 0.01D);
    }
}
