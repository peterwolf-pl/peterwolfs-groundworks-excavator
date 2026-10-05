package com.piotrek.groundworksexcavator.excavation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmTerrainContactControllerTest {

    @Test
    @DisplayName("Arm motion cannot create new penetration but allows preserving or decreasing during extraction")
    void penetrationMustNotIncreaseWhenArmTouchesMaterial() {
        assertTrue(ArmTerrainContactController.allowsPenetrationChange(0, 0));
        assertFalse(ArmTerrainContactController.allowsPenetrationChange(0, 1));
        assertTrue(ArmTerrainContactController.allowsPenetrationChange(3, 3));
        assertTrue(ArmTerrainContactController.allowsPenetrationChange(3, 2));
        assertTrue(ArmTerrainContactController.allowsPenetrationChange(1, 0));
        assertFalse(ArmTerrainContactController.allowsPenetrationChange(2, 3));
    }

    @Test
    @DisplayName("Embedded teeth reject sideways drag but permit forward extraction")
    void embeddedTeethBlockSidewaysMotion() {
        Vec3 cuttingDirection = new Vec3(0.0D, -0.2D, 1.0D).normalize();

        assertTrue(ArmTerrainContactController.isBlockedLateralDrag(
                true, new Vec3(0.1D, 0.0D, 0.0D), cuttingDirection));
        assertFalse(ArmTerrainContactController.isBlockedLateralDrag(
                true, new Vec3(0.0D, 0.05D, 0.1D), cuttingDirection));
        assertFalse(ArmTerrainContactController.isBlockedLateralDrag(
                false, new Vec3(0.1D, 0.0D, 0.0D), cuttingDirection));
        assertTrue(ArmTerrainContactController.isBlockedLateralDrag(
                true, new Vec3(0.1D, 0.0D, 0.0D), new Vec3(0.0D, -1.0D, 0.0D)));
    }

    @Test
    @DisplayName("Hydraulic digging joints stay free while embedded teeth resist cab swing")
    void diggingJointsDoNotTriggerLateralLock() {
        var current = new ArmTerrainContactController.JointAngles(0.0F, 0.0F, 0.0F, 0.0F);

        assertTrue(ArmTerrainContactController.requiresEmbeddedToothLateralCheck(
                current, new ArmTerrainContactController.JointAngles(5.0F, 0.0F, 0.0F, 0.0F)));
        assertFalse(ArmTerrainContactController.requiresEmbeddedToothLateralCheck(
                current, new ArmTerrainContactController.JointAngles(0.0F, -1.0F, 0.0F, 0.0F)));
        assertFalse(ArmTerrainContactController.requiresEmbeddedToothLateralCheck(
                current, new ArmTerrainContactController.JointAngles(0.0F, 0.0F, 1.0F, 0.0F)));
        assertFalse(ArmTerrainContactController.requiresEmbeddedToothLateralCheck(
                current, new ArmTerrainContactController.JointAngles(0.0F, 0.0F, 0.0F, 1.0F)));
    }

    @Test
    @DisplayName("Only shallow tooth contact is classified as a surface skim")
    void surfaceSkimUsesMicrovoxelDepthTolerance() {
        assertTrue(BucketExcavationController.isSurfaceSkim(0.05D));
        assertTrue(BucketExcavationController.isSurfaceSkim(-0.10D));
        assertFalse(BucketExcavationController.isSurfaceSkim(-0.20D));
        assertFalse(BucketExcavationController.isSurfaceSkim(0.25D));
    }

    @Test
    @DisplayName("Surface material is pushed one cell in the bucket travel direction")
    void surfacePushBuildsMoundAheadOfBucket() {
        BlockPos source = new BlockPos(10, 64, 10);

        assertEquals(
                new BlockPos(11, 64, 10),
                BucketExcavationController.surfacePushTarget(source, new Vec3(0.3D, 0.01D, 0.04D))
        );
        assertEquals(
                new BlockPos(9, 64, 11),
                BucketExcavationController.surfacePushTarget(source, new Vec3(-0.2D, 0.0D, 0.3D))
        );
    }
}
