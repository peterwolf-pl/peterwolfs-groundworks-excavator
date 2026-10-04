package com.piotrek.groundworksexcavator.automation;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutoTrenchControllerTest {

    @Test
    void plannedStrokeCutsOneBlockDeepWithOneBlockWideBucket() {
        Vec3 base = new Vec3(0.0D, 65.0D, 0.0D);
        var approach = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F,
                0.0F,
                AutoTrenchController.APPROACH_BOOM,
                AutoTrenchController.APPROACH_STICK,
                AutoTrenchController.APPROACH_BUCKET,
                1);
        var penetration = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F,
                0.0F,
                AutoTrenchController.PENETRATE_BOOM,
                AutoTrenchController.PENETRATE_STICK,
                AutoTrenchController.PENETRATE_BUCKET,
                1);
        var cut = ArmKinematics.computeBucketPose(
                base, 0.0F, 0.0F, 0.0F,
                0.0F,
                AutoTrenchController.PENETRATE_BOOM,
                AutoTrenchController.PENETRATE_STICK,
                AutoTrenchController.CUT_BUCKET,
                1);

        assertEquals(base.y, approach.cuttingEdge().y, 0.15D);
        assertTrue(penetration.cuttingEdge().y < base.y - 0.5D,
                "The teeth must penetrate before the inward pull to avoid surface-skimming the sand away");
        assertEquals(approach.cuttingEdge().z, penetration.cuttingEdge().z, 0.1D,
                "Penetration must be almost vertical instead of pushing the sand forward");
        assertEquals(base.y - 1.0D, cut.cuttingEdge().y, 0.15D);
        assertTrue(cut.cuttingEdge().y < penetration.cuttingEdge().y,
                "The powered bucket stroke must deepen the trench before curling the load closed");
        assertTrue(approach.dumpTiltDegrees() < ArmKinematics.DUMP_THRESHOLD_DEG,
                "The approach pose must not dump newly collected material");
        assertTrue(approach.teethPoints().getFirst().distanceTo(approach.teethPoints().getLast()) > 1.0D,
                "The large test bucket must cover approximately one block of trench width");
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
                true, AutoTrenchController.RIGHT_DUMP_YAW, AutoTrenchController.SAFE_BOOM,
                AutoTrenchController.SAFE_STICK, AutoTrenchController.DUMP_BUCKET,
                0, Vec3.ZERO, 0.0F));

        // After first cut dumps, it must perform the second cut (pass 1) at the SAME station, NOT reverse yet!
        assertEquals(AutoTrenchController.Phase.POSITION_FOR_CUT, controller.phase());
        assertEquals(1, controller.cutsAtCurrentStation());

        // Now simulate finishing the second cut dump
        controller.setPhaseForTest(AutoTrenchController.Phase.DUMP_RIGHT);
        controller.tick(snapshot(
                true, AutoTrenchController.RIGHT_DUMP_YAW, AutoTrenchController.SAFE_BOOM,
                AutoTrenchController.SAFE_STICK, AutoTrenchController.DUMP_BUCKET,
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

        // After relief duration, it must continue the cycle (lift & swing right with collected material)
        for (int i = 0; i < AutoTrenchController.STALL_RELIEF_TICKS; i++) {
            controller.tick(snapshot(
                    true, 0.0F, 10.0F, -70.0F, 10.0F,
                    64, Vec3.ZERO, 0.0F));
        }
        assertEquals(AutoTrenchController.Phase.LIFT_AND_SWING_RIGHT, controller.phase());
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
