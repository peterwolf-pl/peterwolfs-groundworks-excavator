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

        // Teeth points must span across the bucket width
        Vec3 t0 = pose.teethPoints().get(0);
        Vec3 tLast = pose.teethPoints().get(pose.teethPoints().size() - 1);
        double span = t0.distanceTo(tLast);
        assertTrue(span > 0.5D && span < 0.9D, "Teeth width must span ~0.65m. Actual: " + span);

        // Test large bucket (7 teeth, wider span ~1.15m)
        BucketPose poseLarge = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F, 15.0F, -35.0F, -10.0F, 1
        );
        assertEquals(7, poseLarge.teethPoints().size(), "Large bucket should have 7 cutting teeth");
        Vec3 lt0 = poseLarge.teethPoints().get(0);
        Vec3 ltLast = poseLarge.teethPoints().get(poseLarge.teethPoints().size() - 1);
        double largeSpan = lt0.distanceTo(ltLast);
        assertTrue(largeSpan > 1.0D && largeSpan < 1.4D, "Large bucket teeth must span ~1.15m. Actual: " + largeSpan);
    }

    @Test
    @DisplayName("Arm collision samples cover boom and stick while leaving the working bucket free")
    void testArmCollisionSamples() {
        Vec3 base = new Vec3(3.0D, 10.0D, -2.0D);
        var samples = ArmKinematics.computeArmCollisionSamples(
                base, 15.0F, 0.0F, 0.0F, 25.0F, 10.0F, -30.0F);
        BucketPose pose = ArmKinematics.computeBucketPose(
                base, 15.0F, 0.0F, 0.0F, 25.0F, 10.0F, -30.0F, -15.0F, 0);

        assertEquals(75, samples.size(),
                "Only boom and stick steel should block motion; the working bucket must enter material");
        assertTrue(samples.stream().allMatch(point ->
                Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z)));
        for (Vec3 tooth : pose.teethPoints()) {
            assertTrue(samples.stream().noneMatch(sample -> sample.distanceToSqr(tooth) < 1.0E-8D),
                    "Cutting teeth must remain outside steel collision samples");
        }
    }

    @Test
    @DisplayName("Digging reach: arm at downward extension reaches deep below ground")
    void testDiggingDepthReach() {
        Vec3 base = new Vec3(0.0D, 10.0D, 0.0D); // Base at Y=10
        // Downward digging configuration
        BucketPose pose = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F,
                ArmKinematics.BOOM_MIN, // -28 deg
                -25.0F,                 // extended down
                15.0F                   // teeth pointing down
        );

        double diggingDepthBelowBase = base.y - pose.cuttingEdge().y;
        assertTrue(diggingDepthBelowBase >= 2.0D,
                "Excavator must dig deep below track level. Actual: " + diggingDepthBelowBase);
    }

    @Test
    @DisplayName("Horizontal reach: arm extended forward reaches > 5 blocks reach")
    void testForwardReach() {
        Vec3 base = new Vec3(0.0D, 10.0D, 0.0D);
        BucketPose pose = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F,
                5.0F,  // boom slightly up
                10.0F, // stick stretched out
                0.0F   // bucket neutral
        );

        double horizontalDist = Math.hypot(pose.cuttingEdge().x - base.x, pose.cuttingEdge().z - base.z);
        assertTrue(horizontalDist >= 5.0D,
                "Excavator forward reach must be >= 5 blocks. Actual: " + horizontalDist);
    }

    @Test
    @DisplayName("Turntable rotation: rotating upper body rotates cutting edge into correct world space")
    void testTurntableRotation() {
        Vec3 base = new Vec3(0.0D, 10.0D, 0.0D);
        BucketPose forwardPose = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F
        );
        BucketPose turned90Pose = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 90.0F, 0.0F, 0.0F, 0.0F
        );

        System.out.println("Forward: " + forwardPose.cuttingEdge());
        System.out.println("Turned 90: " + turned90Pose.cuttingEdge());
        // When facing south (Yaw 0): forward pose should have large positive Z
        assertTrue(forwardPose.cuttingEdge().z > 4.0D, "Cutting edge should extend in +Z forward");

        // When turned 90 deg right (West): cutting edge X should be negative and large
        assertTrue(turned90Pose.cuttingEdge().x < -4.0D, "Turned 90 deg right should extend into -X (West)");
    }

    @Test
    @DisplayName("Driver seat position rotates with upper body turntable")
    void testDriverSeatTurntableOffset() {
        Vec3 base = new Vec3(0.0D, 10.0D, 0.0D);
        Vec3 seat0 = ArmKinematics.getDriverSeatWorldPosition(base, 0.0F, 0.0F);
        Vec3 seat180 = ArmKinematics.getDriverSeatWorldPosition(base, 0.0F, 180.0F);

        // Cab is on the left side of upper deck (X > 0 when facing +Z)
        assertTrue(seat0.x > base.x, "Seat should be on left side (East when facing South)");

        // When rotated 180 degrees, cab flips to the other side (X < 0)
        assertTrue(seat180.x < base.x, "Seat must rotate with upper body");
    }

    @Test
    @DisplayName("Dump tilt detection: curled bucket does not dump, dumped bucket triggers dump")
    void testDumpTiltAngle() {
        Vec3 base = new Vec3(0.0D, 10.0D, 0.0D);

        // Curled / held bucket: bucketAngle = -50 deg (curled inward)
        BucketPose levelPose = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F, 20.0F, -10.0F, -50.0F
        );
        assertTrue(levelPose.dumpTiltDegrees() < ArmKinematics.DUMP_THRESHOLD_DEG,
                "Curled bucket should not trigger dump. Tilt: " + levelPose.dumpTiltDegrees());

        // Dumped bucket: bucketAngle = +35 deg (opened outward)
        BucketPose dumpPose = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -20.0F, 35.0F
        );
        assertTrue(dumpPose.dumpTiltDegrees() >= ArmKinematics.DUMP_THRESHOLD_DEG,
                "Downward tilted bucket must trigger dump. Tilt: " + dumpPose.dumpTiltDegrees());
    }

    @Test
    @DisplayName("Boom maximum elevation raises arm almost to vertical (> 80 degrees)")
    void testBoomMaximumElevationNearVertical() {
        Vec3 base = new Vec3(0.0D, 10.0D, 0.0D);

        // Boom raised to maximum limit, stick straight
        BucketPose highPose = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F,
                ArmKinematics.BOOM_MAX,
                0.0F,
                0.0F
        );

        assertTrue(ArmKinematics.BOOM_MAX >= 80.0F,
                "Maximum boom angle must be near vertical (>= 80 deg). Actual: " + ArmKinematics.BOOM_MAX);
        double heightAboveBase = highPose.pivot().y - base.y;
        assertTrue(heightAboveBase >= 5.0D,
                "Boom raised to near vertical must position bucket pivot high above base. Actual: " + heightAboveBase);
    }
}
