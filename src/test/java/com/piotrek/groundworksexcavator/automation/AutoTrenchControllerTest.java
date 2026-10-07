package com.piotrek.groundworksexcavator.automation;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutoTrenchControllerTest {

    @Test
    @DisplayName("Initial cut enters firmly (~0.6m) and deepening cut reaches ~1.0m trench depth")
    void plannedStrokeAdaptsDepthToTrench() {
        Vec3 base = new Vec3(0.0D, 65.0D, 0.0D);

        // Initial pass (cutIndex = 0): firm cut (~0.60m below ground to scoop material reliably)
        float initialBoom = AutoTrenchController.calculateCutBoom(0.0F, 0);

        var initialPenetration = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F, 0.0F,
                initialBoom,
                AutoTrenchController.PENETRATE_STICK,
                AutoTrenchController.PENETRATE_BUCKET,
                1);
        double initialDepth = base.y - initialPenetration.cuttingEdge().y;
        assertTrue(initialDepth >= 0.50D && initialDepth <= 0.75D,
                "Initial cut must enter firmly (~0.55 - 0.70m) to scoop material reliably. Actual: " + initialDepth);

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

        // Forearm must be extended out (DUMP_STICK much higher than tucked stick)
        assertTrue(AutoTrenchController.DUMP_STICK > AutoTrenchController.TUCKED_STICK,
                "Dumping must extend forearm outward. Dump stick: "
                        + AutoTrenchController.DUMP_STICK + " vs tucked: " + AutoTrenchController.TUCKED_STICK);

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
    @DisplayName("Forearm stays extended and boom stays high until rotation over work area finishes")
    void forearmStaysExtendedAndBoomStaysHighUntilRotationOverWorkAreaFinishes() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.start();

        // 1. Simulate finishing dumping at right dump pose
        controller.startForTest(AutoTrenchController.Phase.DUMP_RIGHT, null);
        controller.tick(snapshot(
                true, AutoTrenchController.RIGHT_DUMP_YAW, AutoTrenchController.DUMP_BOOM,
                AutoTrenchController.DUMP_STICK, AutoTrenchController.DUMP_BUCKET,
                0, Vec3.ZERO, 0.0F));

        assertEquals(AutoTrenchController.Phase.POSITION_FOR_CUT, controller.phase());

        // 2. While cab is rotating back (e.g. at 45 deg, half way back):
        // Boom must be commanded UP (towards SAFE_BOOM = 48), NOT lowering to cut boom (~10 or 3)!
        // Forearm must remain extended (APPROACH_STICK = -45 deg), NOT retracting to -80!
        AutoTrenchController.Controls swingBackControls = controller.tick(snapshot(
                true, 45.0F, AutoTrenchController.DUMP_BOOM,
                AutoTrenchController.DUMP_STICK, AutoTrenchController.APPROACH_BUCKET,
                0, Vec3.ZERO, 0.0F));

        assertTrue(swingBackControls.boom() > 0.0F,
                "Boom must rise/stay high at SAFE_BOOM during swing back, not lower early! Boom input: "
                        + swingBackControls.boom());
        assertTrue(swingBackControls.stick() >= 0.0F,
                "Forearm must remain extended during swing back, not retract! Stick input: "
                        + swingBackControls.stick());
        assertTrue(swingBackControls.cabYaw() < 0.0F,
                "Cab must rotate back toward 0.0 deg (A key / swing left)");

        // 3. Arriving at 0 deg: rotation must settle before boom lowers
        controller.tick(snapshot(
                true, 0.0F, AutoTrenchController.SAFE_BOOM,
                AutoTrenchController.APPROACH_STICK, AutoTrenchController.APPROACH_BUCKET,
                0, Vec3.ZERO, 0.0F));
        controller.tick(snapshot(
                true, 0.0F, AutoTrenchController.SAFE_BOOM,
                AutoTrenchController.APPROACH_STICK, AutoTrenchController.APPROACH_BUCKET,
                0, Vec3.ZERO, 0.0F));

        // 4. Now that rotation over work area is complete, boom begins lowering to cut depth
        AutoTrenchController.Controls lowerControls = controller.tick(snapshot(
                true, 0.0F, AutoTrenchController.SAFE_BOOM,
                AutoTrenchController.APPROACH_STICK, AutoTrenchController.APPROACH_BUCKET,
                0, Vec3.ZERO, 0.0F));

        assertTrue(lowerControls.boom() < 0.0F,
                "Only after rotation over work area finishes should boom begin lowering. Boom input: "
                        + lowerControls.boom());
    }

    @Test
    @DisplayName("Bucket stays open during return rotation and lowering, only closes after reaching ground in cut")
    void bucketStaysOpenUntilLoweredToGroundAndOnlyClosesDuringCut() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.start();
        controller.startForTest(AutoTrenchController.Phase.DUMP_RIGHT, null);

        // 1. Finish dumping
        controller.tick(snapshot(
                true, AutoTrenchController.RIGHT_DUMP_YAW, AutoTrenchController.DUMP_BOOM,
                AutoTrenchController.DUMP_STICK, AutoTrenchController.DUMP_BUCKET,
                0, Vec3.ZERO, 0.0F));

        assertEquals(AutoTrenchController.Phase.POSITION_FOR_CUT, controller.phase());

        // 2. During swing back: bucket must stay OPEN (OPEN_BUCKET = 75), NOT curling to -50
        AutoTrenchController.Controls swingControls = controller.tick(snapshot(
                true, 45.0F, AutoTrenchController.SAFE_BOOM,
                AutoTrenchController.APPROACH_STICK, AutoTrenchController.OPEN_BUCKET,
                0, Vec3.ZERO, 0.0F));

        assertEquals(0.0F, swingControls.bucket(), 0.1F, "Bucket should stay at OPEN_BUCKET during swing back");

        // 3. Arrive at 0 deg, boom lowers towards ground with bucket STILL OPEN
        controller.tick(snapshot(
                true, 0.0F, AutoTrenchController.SAFE_BOOM,
                AutoTrenchController.APPROACH_STICK, AutoTrenchController.OPEN_BUCKET,
                0, Vec3.ZERO, 0.0F));
        controller.tick(snapshot(
                true, 0.0F, AutoTrenchController.SAFE_BOOM,
                AutoTrenchController.APPROACH_STICK, AutoTrenchController.OPEN_BUCKET,
                0, Vec3.ZERO, 0.0F));

        AutoTrenchController.Controls loweringControls = controller.tick(snapshot(
                true, 0.0F, 14.0F,
                AutoTrenchController.APPROACH_STICK, AutoTrenchController.OPEN_BUCKET,
                0, Vec3.ZERO, 0.0F));

        assertEquals(0.0F, loweringControls.bucket(), 0.1F, "Bucket must remain OPEN while lowering towards ground");

        // 4. In CUT_AND_CURL (after lowered to ground): bucket now closes/curls into the soil
        controller.setPhaseForTest(AutoTrenchController.Phase.CUT_AND_CURL);
        AutoTrenchController.Controls cutControls = controller.tick(snapshot(
                true, 0.0F, AutoTrenchController.INITIAL_PENETRATE_BOOM,
                AutoTrenchController.APPROACH_STICK, AutoTrenchController.OPEN_BUCKET,
                0, Vec3.ZERO, 0.0F));

        assertTrue(cutControls.bucket() < 0.0F,
                "Bucket must close/curl into the ground during cut stroke. Actual: " + cutControls.bucket());
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
    @DisplayName("AutoTrench permits bucket dumping only in DUMP_RIGHT")
    void autoTrenchGatesBucketDumpingToDedicatedPhase() {
        AutoTrenchController controller = new AutoTrenchController();

        assertTrue(controller.allowsBucketDumping(),
                "Manual operation must keep geometry-driven dumping enabled");

        controller.start();
        assertFalse(controller.allowsBucketDumping(),
                "Positioning and digging phases must not dump a partial load");

        controller.setPhaseForTest(AutoTrenchController.Phase.REOPEN_AND_RESET_ARM);
        assertFalse(controller.allowsBucketDumping(),
                "Low-fill retry must retain the partial load");

        controller.setPhaseForTest(AutoTrenchController.Phase.PENETRATE_FOR_CUT);
        assertFalse(controller.allowsBucketDumping(),
                "Retry penetration must not spill the bucket");

        controller.setPhaseForTest(AutoTrenchController.Phase.DUMP_RIGHT);
        assertTrue(controller.allowsBucketDumping(),
                "Dedicated dump phase must enable material flow");

        controller.stop();
        assertTrue(controller.allowsBucketDumping(),
                "Stopping automation must restore manual dumping");
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
    @DisplayName("When bucket is full, immediately close bucket in trench before lifting")
    void fullBucketImmediatelyClosesInTrenchBeforeLifting() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.start();
        controller.setPhaseForTest(AutoTrenchController.Phase.CUT_AND_CURL);

        // Bucket reaches full capacity (e.g. 500 units out of 512)
        AutoTrenchController.Controls controls = controller.tick(new AutoTrenchController.Snapshot(
                true, 0.0F, 10.0F, -60.0F, 0.0F,
                500, 512, Vec3.ZERO, 0.0F, false, 0.0F));

        // Must immediately transition to CLOSE_BUCKET_IN_TRENCH
        assertEquals(AutoTrenchController.Phase.CLOSE_BUCKET_IN_TRENCH, controller.phase());
        // Must curl bucket to hold material
        assertTrue(controls.bucket() < 0.0F, "Must curl/close bucket to end");

        // Bucket must reach the actual held angle and settle before the boom is allowed to lift.
        controller.tick(new AutoTrenchController.Snapshot(
                true, 0.0F, 10.0F, -60.0F, AutoTrenchController.HELD_BUCKET,
                500, 512, Vec3.ZERO, 0.0F, false, 0.0F));
        AutoTrenchController.Controls liftControls = controller.tick(new AutoTrenchController.Snapshot(
                true, 0.0F, 10.0F, -60.0F, AutoTrenchController.HELD_BUCKET,
                500, 512, Vec3.ZERO, 0.0F, false, 0.0F));

        assertEquals(AutoTrenchController.Phase.LIFT_AND_SWING_RIGHT, controller.phase());
        assertTrue(liftControls.boom() > 0.0F, "Boom must lift only after the bucket is fully closed and settled");
    }

    @Test
    @DisplayName("Partial load at a new cut is routed back into scoop retry instead of raised-arm deadlock")
    void partialLoadAtPositionRetriesInsteadOfDeadlockingInLift() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.start();

        AutoTrenchController.Controls controls = controller.tick(new AutoTrenchController.Snapshot(
                true, 0.0F, 24.0F, AutoTrenchController.CUT_STICK, AutoTrenchController.HELD_BUCKET,
                64, 512, Vec3.ZERO, 0.0F, false, 0.0F));

        assertEquals(AutoTrenchController.Phase.REOPEN_AND_RESET_ARM, controller.phase(),
                "A 12.5% bucket must retry digging instead of entering lift");
        assertEquals(0.0F, controls.cabYaw(), 0.05F, "Retry must stay over the work area");
        assertTrue(controls.stick() > 0.0F, "Retry must extend the stick for another bite");
    }

    @Test
    @DisplayName("Low-fill retry keeps bucket closed until arm returns to trench depth")
    void retryRetainsPartialLoadUntilPenetrationPose() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.start();
        controller.tick(new AutoTrenchController.Snapshot(
                true, 0.0F, 24.0F, -45.0F, AutoTrenchController.HELD_BUCKET,
                64, 512, Vec3.ZERO, 0.0F, false, 0.0F));

        // Simulate the actual adaptive reset pose reached with the load still secured.
        float retryCutBoom = AutoTrenchController.calculateCutBoom(
                0.0F, 0, AutoTrenchController.DEFAULT_MAX_TRENCH_DEPTH, 1);
        float retryResetBoom = retryCutBoom + 6.0F;
        for (int i = 0; i < 4; i++) {
            controller.tick(new AutoTrenchController.Snapshot(
                    true, 0.0F, retryResetBoom, AutoTrenchController.APPROACH_STICK,
                    AutoTrenchController.HELD_BUCKET, 64, 512,
                    Vec3.ZERO, 0.0F, false, 0.0F));
        }

        assertEquals(AutoTrenchController.Phase.PENETRATE_FOR_CUT, controller.phase());

        AutoTrenchController.Controls descending = controller.tick(new AutoTrenchController.Snapshot(
                true, 0.0F, retryCutBoom + 2.0F, AutoTrenchController.PENETRATE_STICK,
                AutoTrenchController.HELD_BUCKET, 64, 512,
                Vec3.ZERO, 0.0F, false, 0.0F));

        assertEquals(0.0F, descending.bucket(), 0.05F,
                "Bucket must stay closed while the retry arm is still above target cut depth");
    }

    @Test
    void loadedBucketIsMovedToTheRightDumpBeforeDigging() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.start();

        AutoTrenchController.Controls controls = controller.tick(snapshot(
                true, 0.0F, 24.0F, -35.0F, AutoTrenchController.HELD_BUCKET, 300, Vec3.ZERO, 0.0F));

        assertEquals(AutoTrenchController.Phase.LIFT_AND_SWING_RIGHT, controller.phase());
        assertTrue(controls.cabYaw() > 0.0F, "The upper body must turn toward the machine's right side");
        assertTrue(controls.boom() > 0.0F, "The loaded bucket must be raised before dumping");
    }

    @Test
    @DisplayName("Bucket closes fully before turntable starts swinging to dump location")
    void bucketClosesFullyBeforeTurntableSwingsToDump() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.start();
        controller.setPhaseForTest(AutoTrenchController.Phase.LIFT_AND_SWING_RIGHT);

        // Bucket is still partially open (e.g. 10 deg) while lifting boom (12 deg)
        AutoTrenchController.Controls controls = controller.tick(snapshot(
                true, 0.0F, 12.0F, -45.0F, 10.0F, 256, Vec3.ZERO, 0.0F));

        // Must NOT swing cab yet (cabYaw must be 0.0)
        assertEquals(0.0F, controls.cabYaw(), 0.05F, "Must not swing cab while bucket is still open!");
        // Must command bucket to close to HELD_BUCKET (-60.0)
        assertTrue(controls.bucket() < 0.0F, "Must command bucket to curl/close");
        // Boom must stay still until the bucket is actually closed.
        assertEquals(0.0F, controls.boom(), 0.05F, "Boom must not rise while the bucket is still closing");

        // Once the bucket is fully closed, lifting can begin, but swing still waits for ground clearance.
        AutoTrenchController.Controls liftControls = controller.tick(snapshot(
                true, 0.0F, 12.0F, -45.0F, AutoTrenchController.HELD_BUCKET, 300, Vec3.ZERO, 0.0F));
        assertTrue(liftControls.boom() > 0.0F, "Boom must lift after the bucket reaches HELD_BUCKET");
        assertEquals(0.0F, liftControls.cabYaw(), 0.05F, "Cab must not swing before the boom clears the ground");

        AutoTrenchController.Controls swingControls = controller.tick(snapshot(
                true, 0.0F, 24.0F, -45.0F, AutoTrenchController.HELD_BUCKET, 300, Vec3.ZERO, 0.0F));

        // Now it must swing right towards dump. Fill level is not revalidated in this phase.
        assertTrue(swingControls.cabYaw() > 0.0F, "Must swing cab right once bucket is closed and boom is clear");
    }

    @Test
    @DisplayName("Weak bucket loads retry toward 75% target before lifting")
    void weakBucketLoadRetriesBeforeSwingingToDump() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.start();
        controller.setPhaseForTest(AutoTrenchController.Phase.CLOSE_BUCKET_IN_TRENCH);

        // Case 1: Bucket has very little material (50 / 512). It must not lift.
        AutoTrenchController.Snapshot weakClosed = new AutoTrenchController.Snapshot(
                true, 0.0F, 10.0F, -75.0F, AutoTrenchController.HELD_BUCKET,
                50, 512, Vec3.ZERO, 0.0F, false, 0.0F);
        controller.tick(weakClosed);
        AutoTrenchController.Controls retryCmd = controller.tick(weakClosed);

        assertEquals(AutoTrenchController.Phase.REOPEN_AND_RESET_ARM, controller.phase(),
                "A weak bite must trigger another scoop instead of lifting");
        assertTrue(retryCmd.stick() > 0.0F, "Retry must extend the stick for another bite");
        assertTrue(retryCmd.bucket() <= 0.0F,
                "Retry reposition must keep the partial load secured instead of opening/dumping it above the trench");

        // Case 2: 260 / 512 is above 50%, but still below the preferred 75% target.
        controller.setPhaseForTest(AutoTrenchController.Phase.CLOSE_BUCKET_IN_TRENCH);
        AutoTrenchController.Snapshot mediumClosed = new AutoTrenchController.Snapshot(
                true, 0.0F, 10.0F, -75.0F, AutoTrenchController.HELD_BUCKET,
                260, 512, Vec3.ZERO, 0.0F, false, 0.0F);
        controller.tick(mediumClosed);
        controller.tick(mediumClosed);
        assertEquals(AutoTrenchController.Phase.REOPEN_AND_RESET_ARM, controller.phase(),
                "A medium load should still retry while retry budget remains");

        // Case 3: 400 / 512 exceeds the 75% target and may lift normally.
        controller.setPhaseForTest(AutoTrenchController.Phase.CLOSE_BUCKET_IN_TRENCH);
        AutoTrenchController.Snapshot goodClosed = new AutoTrenchController.Snapshot(
                true, 0.0F, 10.0F, -75.0F, AutoTrenchController.HELD_BUCKET,
                400, 512, Vec3.ZERO, 0.0F, false, 0.0F);
        controller.tick(goodClosed);
        controller.tick(goodClosed);
        assertEquals(AutoTrenchController.Phase.LIFT_AND_SWING_RIGHT, controller.phase(),
                "A load above the 75% target may proceed to lift and dump");
    }

    @Test
    @DisplayName("Command parameters X (depth) and Y (cycles) govern cut boom and station reverse cycle")
    void customParametersGovernDepthAndCycles() {
        AutoTrenchController controller = new AutoTrenchController();
        // Start with X = 2.0 blocks depth, Y = 3 cycles before reverse
        controller.start(2.0F, 3);
        assertEquals(2.0F, controller.maxDiggingDepth());
        assertEquals(3, controller.maxCutsPerStation());

        // Cuts at station must allow 3 cuts before reversing
        controller.startForTest(AutoTrenchController.Phase.DUMP_RIGHT, null);
        controller.start(2.0F, 3);
        controller.setPhaseForTest(AutoTrenchController.Phase.DUMP_RIGHT);

        // Dump 1 -> back to POSITION_FOR_CUT
        controller.tick(snapshot(
                true, AutoTrenchController.RIGHT_DUMP_YAW, AutoTrenchController.DUMP_BOOM,
                AutoTrenchController.DUMP_STICK, AutoTrenchController.DUMP_BUCKET,
                0, Vec3.ZERO, 0.0F));
        assertEquals(1, controller.cutsAtCurrentStation());
        assertEquals(AutoTrenchController.Phase.POSITION_FOR_CUT, controller.phase());

        // Dump 2 -> back to POSITION_FOR_CUT
        controller.setPhaseForTest(AutoTrenchController.Phase.DUMP_RIGHT);
        controller.tick(snapshot(
                true, AutoTrenchController.RIGHT_DUMP_YAW, AutoTrenchController.DUMP_BOOM,
                AutoTrenchController.DUMP_STICK, AutoTrenchController.DUMP_BUCKET,
                0, Vec3.ZERO, 0.0F));
        assertEquals(2, controller.cutsAtCurrentStation());
        assertEquals(AutoTrenchController.Phase.POSITION_FOR_CUT, controller.phase());

        // Dump 3 -> reaches Y=3 cycles, NOW it must reverse!
        controller.setPhaseForTest(AutoTrenchController.Phase.DUMP_RIGHT);
        controller.tick(snapshot(
                true, AutoTrenchController.RIGHT_DUMP_YAW, AutoTrenchController.DUMP_BOOM,
                AutoTrenchController.DUMP_STICK, AutoTrenchController.DUMP_BUCKET,
                0, Vec3.ZERO, 0.0F));
        assertEquals(0, controller.cutsAtCurrentStation());
        assertEquals(AutoTrenchController.Phase.RESET_AND_REVERSE, controller.phase());
    }

    @Test
    @DisplayName("Command parameters X Y Z Y2: digs center Y cycles, then swings left Z blocks for Y2 cycles before reversing")
    void leftExpansionCycleExecution() {
        AutoTrenchController controller = new AutoTrenchController();
        // /excavator autotrench start 2 5 1 3 (depth 2.0, center 5 cycles, expand left 1 block, left 3 cycles)
        controller.start(2.0F, 5, 1.0F, 3);
        assertEquals(2.0F, controller.maxDiggingDepth());
        assertEquals(5, controller.maxCutsPerStation());
        assertEquals(1.0F, controller.leftExpansionBlocks());
        assertEquals(3, controller.leftExpansionCycles());
        assertFalse(controller.isInLeftExpansionPass());
        assertEquals(0.0F, controller.currentWorkCabinYaw(), 0.01F);

        // Simulate 4 center cuts dumped
        for (int i = 0; i < 4; i++) {
            controller.setPhaseForTest(AutoTrenchController.Phase.DUMP_RIGHT);
            controller.tick(snapshot(
                    true, AutoTrenchController.RIGHT_DUMP_YAW, AutoTrenchController.DUMP_BOOM,
                    AutoTrenchController.DUMP_STICK, AutoTrenchController.DUMP_BUCKET,
                    0, Vec3.ZERO, 0.0F));
            assertFalse(controller.isInLeftExpansionPass());
            assertEquals(0.0F, controller.currentWorkCabinYaw(), 0.01F);
        }

        // 5th center cut dumped -> now switches to left expansion pass!
        controller.setPhaseForTest(AutoTrenchController.Phase.DUMP_RIGHT);
        controller.tick(snapshot(
                true, AutoTrenchController.RIGHT_DUMP_YAW, AutoTrenchController.DUMP_BOOM,
                AutoTrenchController.DUMP_STICK, AutoTrenchController.DUMP_BUCKET,
                0, Vec3.ZERO, 0.0F));

        assertTrue(controller.isInLeftExpansionPass(), "Must switch to left expansion pass after 5 center cuts");
        assertEquals(0, controller.cutsAtCurrentStation());
        assertEquals(AutoTrenchController.Phase.POSITION_FOR_CUT, controller.phase());
        // Yaw must be shifted to the left (negative degrees in model coordinates)
        assertTrue(controller.currentWorkCabinYaw() < -5.0F,
                "Work cabin yaw must rotate left for 1 block expansion. Actual: " + controller.currentWorkCabinYaw());

        // Perform 2 left expansion cuts
        for (int i = 0; i < 2; i++) {
            controller.setPhaseForTest(AutoTrenchController.Phase.DUMP_RIGHT);
            controller.tick(snapshot(
                    true, AutoTrenchController.RIGHT_DUMP_YAW, AutoTrenchController.DUMP_BOOM,
                    AutoTrenchController.DUMP_STICK, AutoTrenchController.DUMP_BUCKET,
                    0, Vec3.ZERO, 0.0F));
            assertTrue(controller.isInLeftExpansionPass());
        }

        // 3rd left expansion cut dumped (reaching Y2 = 3) -> NOW triggers reverse!
        controller.setPhaseForTest(AutoTrenchController.Phase.DUMP_RIGHT);
        controller.tick(snapshot(
                true, AutoTrenchController.RIGHT_DUMP_YAW, AutoTrenchController.DUMP_BOOM,
                AutoTrenchController.DUMP_STICK, AutoTrenchController.DUMP_BUCKET,
                0, Vec3.ZERO, 0.0F));

        assertFalse(controller.isInLeftExpansionPass(), "Left pass must reset after completion");
        assertEquals(AutoTrenchController.Phase.RESET_AND_REVERSE, controller.phase(),
                "Must initiate reverse after center cuts and left expansion cuts complete");
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
            controller.tick(new AutoTrenchController.Snapshot(
                    true, 0.0F, 10.0F, -70.0F, 10.0F,
                    400, 512, Vec3.ZERO, 0.0F, false, 0.0F));
        }
        assertEquals(AutoTrenchController.Phase.LIFT_AND_SWING_RIGHT, controller.phase(),
                "After relief with a well-loaded bucket, controller lifts and swings to dump");
    }

    @Test
    void dumpTruckModeChecksReceiverOnlyAtPlus50AfterRearSwing() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.startWithDumpTruck(1.0F, 2, 0.0F, 0);

        AutoTrenchController.Controls digging = controller.tick(snapshot(
                false, 0.0F, AutoTrenchController.SAFE_BOOM,
                AutoTrenchController.SAFE_STICK, AutoTrenchController.HELD_BUCKET,
                0, Vec3.ZERO, 0.0F), false);

        assertTrue(controller.isActive(), "Leaving the cab must not stop dump-truck AutoTrench");
        assertTrue(controller.isDumpTruckMode());
        assertNotEquals(AutoTrenchController.Controls.STOPPED, digging,
                "Missing truck must not prevent the excavator from starting the digging cycle");

        controller.setPhaseForTest(AutoTrenchController.Phase.LIFT_AND_SWING_RIGHT);
        AutoTrenchController.Controls rotating = controller.tick(snapshot(
                false, 0.0F, AutoTrenchController.REAR_TRANSIT_BOOM,
                AutoTrenchController.SAFE_STICK, AutoTrenchController.HELD_BUCKET,
                400, Vec3.ZERO, 0.0F), false);

        assertNotEquals(0.0F, rotating.cabYaw(),
                "At safe boom height the excavator must rotate 180 degrees even with no truck present");

        controller.tick(snapshot(
                false, AutoTrenchController.REAR_DUMP_YAW,
                AutoTrenchController.REAR_TRANSIT_BOOM,
                AutoTrenchController.SAFE_STICK, AutoTrenchController.HELD_BUCKET,
                400, Vec3.ZERO, 0.0F), false);

        assertEquals(AutoTrenchController.Phase.DUMP_RIGHT, controller.phase());

        AutoTrenchController.Controls lowering = controller.tick(snapshot(
                false, AutoTrenchController.REAR_DUMP_YAW,
                AutoTrenchController.REAR_TRANSIT_BOOM,
                AutoTrenchController.SAFE_STICK, AutoTrenchController.HELD_BUCKET,
                400, Vec3.ZERO, 0.0F), false);

        assertNotEquals(AutoTrenchController.Controls.STOPPED, lowering,
                "After the 180-degree swing the arm must move to the +50-degree check pose even with no truck");
        assertEquals(0.0F, lowering.bucket(), 0.0001F,
                "The bucket must remain closed while moving to the truck-check pose");

        AutoTrenchController.Controls waiting = controller.tick(snapshot(
                false, AutoTrenchController.REAR_DUMP_YAW,
                AutoTrenchController.REAR_DUMP_CHECK_BOOM,
                AutoTrenchController.DUMP_STICK, AutoTrenchController.HELD_BUCKET,
                400, Vec3.ZERO, 0.0F), false);

        assertEquals(AutoTrenchController.Controls.STOPPED, waiting,
                "At +50 degrees the excavator must pause with the bucket closed when no truck is under the lip");

        AutoTrenchController.Controls resumed = controller.tick(snapshot(
                false, AutoTrenchController.REAR_DUMP_YAW,
                AutoTrenchController.REAR_DUMP_CHECK_BOOM,
                AutoTrenchController.DUMP_STICK, AutoTrenchController.HELD_BUCKET,
                400, Vec3.ZERO, 0.0F), true);

        assertNotEquals(AutoTrenchController.Controls.STOPPED, resumed,
                "A truck under the +50-degree dump point must resume unloading");
        assertTrue(resumed.bucket() > 0.0F,
                "Once the truck is present the bucket must begin opening");

        AutoTrenchController.Controls continuingOpen = controller.tick(snapshot(
                false, AutoTrenchController.REAR_DUMP_YAW,
                AutoTrenchController.REAR_DUMP_CHECK_BOOM,
                AutoTrenchController.DUMP_STICK, -20.0F,
                320, Vec3.ZERO, 0.0F), true);

        assertTrue(continuingOpen.bucket() > 0.0F,
                "A partially opened bucket must keep opening instead of being commanded closed");
    }

    @Test
    void dumpTruckRearLiftNeverExceeds55Degrees() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.startWithDumpTruck(1.0F, 2, 0.0F, 0);
        controller.setPhaseForTest(AutoTrenchController.Phase.LIFT_AND_SWING_RIGHT);

        AutoTrenchController.Controls controls = AutoTrenchController.Controls.STOPPED;
        for (int i = 0; i < 10; i++) {
            controls = controller.tick(new AutoTrenchController.Snapshot(
                    false,
                    0.0F,
                    AutoTrenchController.REAR_MAX_TRANSIT_BOOM,
                    AutoTrenchController.SAFE_STICK,
                    AutoTrenchController.HELD_BUCKET,
                    400,
                    512,
                    Vec3.ZERO,
                    0.0F,
                    true,
                    0.0F
            ), true);
        }

        assertEquals(0.0F, controls.boom(), 0.0001F,
                "Obstacle-clearance boost must never command rear transit above +55 degrees");
    }

    @Test
    void dumpTruckModeRaisesHighBeforeStartingRearSwing() {
        AutoTrenchController controller = new AutoTrenchController();
        controller.startWithDumpTruck(1.0F, 2, 0.0F, 0);
        controller.setPhaseForTest(AutoTrenchController.Phase.LIFT_AND_SWING_RIGHT);

        AutoTrenchController.Controls low = controller.tick(snapshot(
                false, 0.0F, 22.0F,
                AutoTrenchController.SAFE_STICK, AutoTrenchController.HELD_BUCKET,
                400, Vec3.ZERO, 0.0F), true);

        assertEquals(0.0F, low.cabYaw(), 0.0001F,
                "Rear rotation must wait until the boom is raised high");

        AutoTrenchController.Controls high = controller.tick(snapshot(
                false, 0.0F, AutoTrenchController.REAR_TRANSIT_BOOM,
                AutoTrenchController.SAFE_STICK, AutoTrenchController.HELD_BUCKET,
                400, Vec3.ZERO, 0.0F), true);

        assertNotEquals(0.0F, high.cabYaw(),
                "At safe boom height the controller must begin the 180-degree rear swing");

        controller.tick(snapshot(
                false, AutoTrenchController.REAR_DUMP_YAW,
                AutoTrenchController.REAR_TRANSIT_BOOM,
                AutoTrenchController.SAFE_STICK, AutoTrenchController.HELD_BUCKET,
                400, Vec3.ZERO, 0.0F), true);

        assertEquals(AutoTrenchController.Phase.DUMP_RIGHT, controller.phase(),
                "At the rear high transit pose the next phase is dumping into the truck");
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
