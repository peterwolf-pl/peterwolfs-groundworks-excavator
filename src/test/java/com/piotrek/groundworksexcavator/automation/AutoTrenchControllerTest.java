package com.piotrek.groundworksexcavator.automation;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutoTrenchControllerTest {

    @Test
    @DisplayName("Initial cut is shallow (~0.4m) and deepening cut reaches ~1.0m trench depth")
    void plannedStrokeAdaptsDepthToTrench() {
        Vec3 base = new Vec3(0.0D, 65.0D, 0.0D);

        // Initial pass (cutIndex = 0): shallow cut (~0.38m below ground, NOT 1 full block)
        float initialBoom = AutoTrenchController.calculateCutBoom(0.0F, 0);
        assertEquals(AutoTrenchController.INITIAL_PENETRATE_BOOM, initialBoom, 0.2F);

        var initialPenetration = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F,
                initialBoom,
                AutoTrenchController.PENETRATE_STICK,
                AutoTrenchController.PENETRATE_BUCKET,
                1);
        double initialDepth = base.y - initialPenetration.cuttingEdge().y;
        assertTrue(initialDepth >= 0.30D && initialDepth <= 0.50D,
                "Initial cut must be shallow (~0.35 - 0.45m), not a whole block. Actual: " + initialDepth);

        // Second pass (cutIndex = 1): deepening cut (~0.90m - 1.0m below ground)
        float deepBoom = AutoTrenchController.calculateCutBoom(0.38F, 1);
        var deepPenetration = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F,
                deepBoom,
                AutoTrenchController.PENETRATE_STICK,
                AutoTrenchController.PENETRATE_BUCKET,
                1);
        double deepDepth = base.y - deepPenetration.cuttingEdge().y;
        assertTrue(deepDepth >= 0.80D && deepDepth <= 1.05D,
                "Deepening cut must reach trench target depth ~1.0m. Actual: " + deepDepth);

        // Approach pose must cover ~1 block width and not dump
        var approach = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F,
                AutoTrenchController.APPROACH_BOOM,
                AutoTrenchController.APPROACH_STICK,
                AutoTrenchController.APPROACH_BUCKET,
                1);
        // Held pose must securely retain material without dumping
        var held = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F,
                AutoTrenchController.APPROACH_BOOM,
                AutoTrenchController.APPROACH_STICK,
                AutoTrenchController.HELD_BUCKET,
                1);
        assertTrue(held.dumpTiltDegrees() < ArmKinematics.DUMP_THRESHOLD_DEG,
                "Curled bucket must not dump collected material");
        assertTrue(approach.teethPoints().getFirst().distanceTo(approach.teethPoints().getLast()) > 1.0D,
                "The large test bucket must cover approximately one block of trench width");
    }

    @Test
    @DisplayName("Dumping extends forearm (stick) more and opens bucket all the way")
    void dumpPoseExtendsForearmAndOpensBucketFully() {
        Vec3 base = new Vec3(0.0D, 65.0D, 0.0D);

        // Forearm must be extended out (DUMP_STICK much higher than tucked SAFE_STICK)
        assertTrue(AutoTrenchController.DUMP_STICK > AutoTrenchController.SAFE_STICK,
                "Dumping must extend forearm outward. Dump stick: "
                        + AutoTrenchController.DUMP_STICK + " vs safe: " + AutoTrenchController.SAFE_STICK);

        // Bucket must be open to the maximum articulation angle
        assertEquals(ArmKinematics.BUCKET_MAX, AutoTrenchController.DUMP_BUCKET,
                "Dumping must open the bucket all the way to BUCKET_MAX");

        // The dump pose must tilt past the dumping threshold so material flows out
        var dumpPose = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F,
                AutoTrenchController.RIGHT_DUMP_YAW,
                AutoTrenchController.DUMP_BOOM,
                AutoTrenchController.DUMP_STICK,
                AutoTrenchController.DUMP_BUCKET,
                1);
        assertTrue(dumpPose.dumpTiltDegrees() >= ArmKinematics.DUMP_THRESHOLD_DEG,
                "Dump tilt must trigger dumping flow. Actual: " + dumpPose.dumpTiltDegrees());
    }

    @Test
    @DisplayName("Resistance during rotation raises the whole arm (boom) higher")
    void rotationResistanceRaisesBoomHigher() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.start();
        controller.setPhaseForTest(AutoTrenchController.Phase.LIFT_AND_SWING_RIGHT);

        // Simulate obstacle stopping the turntable swing: cabinBlocked = true
        AutoTrenchController.Controls controls = null;
        for (int i = 0; i < 4; i++) {
            controls = controller.tick(new AutoTrenchController.Snapshot(
                    true, 45.0F, AutoTrenchController.SAFE_BOOM,
                    AutoTrenchController.SAFE_STICK, AutoTrenchController.HELD_BUCKET,
                    128, Vec3.ZERO, 0.0F, true, 0.0F));
        }

        // Resistance during rotation must boost boom height
        assertTrue(controller.swingObstacleBoomBoost() > 0.0F,
                "Swing obstacle must increase swingObstacleBoomBoost");
        assertNotNull(controls);
        assertTrue(controls.boom() > 0.0F,
                "Controller must command boom up (+1.0) to lift arm over the obstacle");
    }

    @Test
    @DisplayName("Digging stroke pulls forearm (crowd) and curls bucket to scoop")
    void diggingStrokePullsForearmAndCurlsBucket() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.start();
        controller.setPhaseForTest(AutoTrenchController.Phase.CUT_AND_CURL);

        // Machine is at approach stick (-45 deg), cut requires pulling inward to CUT_STICK (-75 deg)
        AutoTrenchController.Controls controls = controller.tick(snapshot(
                true, 0.0F, AutoTrenchController.INITIAL_PENETRATE_BOOM,
                AutoTrenchController.APPROACH_STICK, AutoTrenchController.APPROACH_BUCKET,
                0, Vec3.ZERO, 0.0F));

        // Pulling forearm inward: stick input < 0 (S key / stick in)
        assertTrue(controls.stick() < 0.0F,
                "Controller must pull forearm inward (stick crowd). Actual: " + controls.stick());
        // Curling bucket inward: bucket input < 0 (curl)
        assertTrue(controls.bucket() < 0.0F,
                "Controller must curl bucket inward to scoop. Actual: " + controls.bucket());
    }

    @Test
    void dismountStopsAutomationImmediately() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.start();

        AutoTrenchController.Controls controls = controller.tick(snapshot(
                false, 0.0F, 15.0F, -35.0F, -10.0F, 0, Vec3.ZERO, 0.0F));

        assertFalse(controller.isActive());
        assertEquals(AutoTrenchController.Controls.STOPPED, controls);
    }

    @Test
    void loadedBucketIsMovedToTheRightDumpBeforeDigging() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.start();

        AutoTrenchController.Controls controls = controller.tick(snapshot(
                true, 0.0F, 15.0F, -35.0F, -10.0F, 128, Vec3.ZERO, 0.0F));

        assertEquals(AutoTrenchController.Phase.LIFT_AND_SWING_RIGHT, controller.phase());
        assertTrue(controls.cabYaw() > 0.0F, "The upper body must turn toward the machine's right side");
        assertTrue(controls.boom() > 0.0F, "The loaded bucket must be raised before dumping");
    }

    @Test
    void reversePhaseStopsAfterExactlyOneBlockAndStartsNextCut() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.startForTest(AutoTrenchController.Phase.RESET_AND_REVERSE, Vec3.ZERO);

        AutoTrenchController.Controls moving = controller.tick(snapshot(
                true, 0.0F, AutoTrenchController.SAFE_BOOM,
                AutoTrenchController.SAFE_STICK, AutoTrenchController.HELD_BUCKET,
                0, new Vec3(0.0D, 0.0D, -0.75D), 0.0F));
        assertTrue(moving.throttle() < 0.0F);

        AutoTrenchController.Controls stopped = controller.tick(snapshot(
                true, 0.0F, AutoTrenchController.SAFE_BOOM,
                AutoTrenchController.SAFE_STICK, AutoTrenchController.HELD_BUCKET,
                0, new Vec3(0.0D, 0.0D, -1.01D), 0.0F));
        assertEquals(0.0F, stopped.throttle());
        assertEquals(AutoTrenchController.Phase.POSITION_FOR_CUT, controller.phase());
        assertEquals(1, controller.completedSections());
    }

    @Test
    void performsTwoCutsAtStationBeforeReversing() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.start();

        // Simulate dumping first cut load at right dump pose
        controller.startForTest(AutoTrenchController.Phase.DUMP_RIGHT, null);
        controller.tick(snapshot(
                true, AutoTrenchController.RIGHT_DUMP_YAW, AutoTrenchController.DUMP_BOOM,
                AutoTrenchController.DUMP_STICK, AutoTrenchController.DUMP_BUCKET,
                0, Vec3.ZERO, 0.0F));

        // After first cut dumps, it must perform the second cut (pass 1) at the SAME station, NOT reverse yet!
        assertEquals(AutoTrenchController.Phase.POSITION_FOR_CUT, controller.phase());
        assertEquals(1, controller.cutsAtCurrentStation());

        // Now simulate finishing the second cut dump
        controller.setPhaseForTest(AutoTrenchController.Phase.DUMP_RIGHT);
        controller.tick(snapshot(
                true, AutoTrenchController.RIGHT_DUMP_YAW, AutoTrenchController.DUMP_BOOM,
                AutoTrenchController.DUMP_STICK, AutoTrenchController.DUMP_BUCKET,
                0, Vec3.ZERO, 0.0F));

        // After the second cut dumps, NOW it must reverse 1 block!
        assertEquals(AutoTrenchController.Phase.RESET_AND_REVERSE, controller.phase());
        assertEquals(0, controller.cutsAtCurrentStation());
    }

    @Test
    void stallDetectionTriggersStickOutAndBoomUpReliefThenContinues() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.start();
        controller.setPhaseForTest(AutoTrenchController.Phase.CUT_AND_CURL);

        // Feed stagnant joint angles for several ticks to simulate arm hitting maximum terrain resistance
        AutoTrenchController.Controls relief = null;
        for (int i = 0; i < 9; i++) {
            relief = controller.tick(snapshot(
                    true, 0.0F, 5.0F, -80.0F, 10.0F,
                    64, Vec3.ZERO, 0.0F));
        }

        // Stalled state must trigger relief: boom up (Arrow Up > 0) and stick out (W key > 0)
        assertEquals(AutoTrenchController.Phase.RELIEVE_STALL, controller.phase());
        assertNotNull(relief);
        assertTrue(relief.boom() > 0.0F, "Relief must command boom up (Arrow Up)");
        assertTrue(relief.stick() > 0.0F, "Relief must command stick out (W key)");

        // After relief duration, it must attempt to scoop again to get a full bucket!
        for (int i = 0; i < AutoTrenchController.STALL_RELIEF_TICKS; i++) {
            controller.tick(snapshot(
                    true, 0.0F, 10.0F, -70.0F, 10.0F,
                    64, Vec3.ZERO, 0.0F));
        }
        assertEquals(AutoTrenchController.Phase.CUT_AND_CURL, controller.phase(),
                "After minimal relief, controller must attempt to scoop a full bucket again");
    }

    private static AutoTrenchController.Snapshot snapshot(
            boolean occupied,
            float cabin,
            float boom,
            float stick,
            float bucket,
            int storedUnits,
            Vec3 position,
            float baseYaw
    ) {
        return new AutoTrenchController.Snapshot(
                occupied, cabin, boom, stick, bucket, storedUnits, position, baseYaw);
    }
}
