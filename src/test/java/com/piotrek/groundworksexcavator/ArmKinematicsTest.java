package com.piotrek.groundworksexcavator;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ArmKinematicsTest {

    @Test
    @DisplayName("Forward kinematics computes valid cutting edge and teeth points")
    void testForwardKinematicsGeometry() {
        Vec3 base = new Vec3(0.0D, 10.0D, 0.0D);
        BucketPose pose = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F, 15.0F, -35.0F, -10.0F
        );

        assertNotNull(pose);
        assertNotNull(pose.pivot());
        assertNotNull(pose.cuttingEdge());
        assertNotNull(pose.lip());
        assertNotNull(pose.forwardCutting());
        assertNotNull(pose.scoopNormal());

        // Teeth array must have exactly TEETH_COUNT points
        assertEquals(ArmKinematics.TEETH_COUNT, pose.teethPoints().size());

        // Teeth points must span approximately BUCKET_WIDTH
        Vec3 t0 = pose.teethPoints().get(0);
        Vec3 tLast = pose.teethPoints().get(pose.teethPoints().size() - 1);
        double span = t0.distanceTo(tLast);
        assertEquals(ArmKinematics.BUCKET_WIDTH, span, 0.01D, "Teeth width must match bucket width");
    }

    @Test
    @DisplayName("Digging reach: arm at maximum downward extension reaches > 3 blocks below ground")
    void testDiggingDepthReach() {
        Vec3 base = new Vec3(0.0D, 10.0D, 0.0D); // Base at Y=10
        // Maximum down configuration
        BucketPose pose = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F,
                ArmKinematics.BOOM_MIN, // -28 deg
                -20.0F,                 // extended down
                -45.0F                  // teeth pointing down
        );

        double diggingDepthBelowBase = base.y - pose.cuttingEdge().y;
        assertTrue(diggingDepthBelowBase >= 3.0D,
                "Excavator must dig at least 3 blocks below track level. Actual: " + diggingDepthBelowBase);
    }

    @Test
    @DisplayName("Horizontal reach: arm extended forward reaches > 5 blocks reach")
    void testForwardReach() {
        Vec3 base = new Vec3(0.0D, 10.0D, 0.0D);
        BucketPose pose = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F,
                5.0F, // boom slightly up
                10.0F, // stick stretched out
                0.0F   // bucket open
        );

        double horizontalDist = Math.hypot(pose.cuttingEdge().x - base.x, pose.cuttingEdge().z - base.z);
        assertTrue(horizontalDist >= 5.0D,
                "Excavator forward reach must be >= 5 blocks. Actual: " + horizontalDist);
    }

    @Test
    @DisplayName("Turntable rotation: rotating upper body 90 degrees rotates cutting edge to the side")
    void testTurntableRotation() {
        Vec3 base = new Vec3(0.0D, 10.0D, 0.0D);
        BucketPose forwardPose = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F
        );
        BucketPose turned90Pose = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 90.0F, 0.0F, 0.0F, 0.0F
        );

        // Facing south (+Z): forward pose should have large Z and near-zero X
        assertTrue(forwardPose.cuttingEdge().z > 4.0D);

        // Turned 90 deg (facing West, -X): cutting edge X should be negative and large
        assertTrue(turned90Pose.cuttingEdge().x < -4.0D);
    }

    @Test
    @DisplayName("Driver seat position rotates with upper body turntable")
    void testDriverSeatTurntableOffset() {
        Vec3 base = new Vec3(0.0D, 10.0D, 0.0D);
        Vec3 seat0 = ArmKinematics.getDriverSeatWorldPosition(base, 0.0F, 0.0F);
        Vec3 seat180 = ArmKinematics.getDriverSeatWorldPosition(base, 0.0F, 180.0F);

        // Cab is on the left side of upper deck (X < 0 when facing +Z)
        assertTrue(seat0.x < base.x, "Seat should be on left side");

        // When rotated 180 degrees, cab flips to the other side (X > 0)
        assertTrue(seat180.x > base.x, "Seat must rotate with upper body");
    }

    @Test
    @DisplayName("Dump tilt detection: level bucket does not dump, tilted bucket triggers dump")
    void testDumpTiltAngle() {
        Vec3 base = new Vec3(0.0D, 10.0D, 0.0D);

        // Curled / held bucket (pointing horizontal or up: total pitch = 20 - 10 + 10 = +20 deg)
        BucketPose levelPose = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F, 20.0F, -10.0F, 10.0F
        );
        assertTrue(levelPose.dumpTiltDegrees() < ArmKinematics.DUMP_THRESHOLD_DEG,
                "Curled bucket should not trigger dump. Tilt: " + levelPose.dumpTiltDegrees());

        // Dumped bucket: teeth pointing downward (total pitch = 0 - 20 - 30 = -50 deg)
        BucketPose dumpPose = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -20.0F, -30.0F
        );
        assertTrue(dumpPose.dumpTiltDegrees() >= ArmKinematics.DUMP_THRESHOLD_DEG,
                "Downward tilted bucket must trigger dump. Tilt: " + dumpPose.dumpTiltDegrees());
    }
}
