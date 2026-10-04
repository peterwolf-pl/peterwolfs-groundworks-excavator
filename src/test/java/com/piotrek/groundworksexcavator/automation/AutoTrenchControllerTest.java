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
