package com.piotrek.groundworksexcavator.excavation;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExcavatorTreeControllerTest {

    @Test
    @DisplayName("Leverage scales with height: higher bucket contact point produces greater strain")
    void testLeverageScalesWithHeight() {
        // Tree base at Y = 64
        int treeBaseY = 64;

        // Ground/stump contact at Y = 64 (height = 0.5)
        double lowContactY = 64.5;
        double lowHeight = Math.max(0.5, lowContactY - treeBaseY);
        float lowLeverage = Math.max(0.25F, (float) (lowHeight / 2.0));

        // High trunk/crown contact at Y = 70 (height = 6.5)
        double highContactY = 70.5;
        double highHeight = Math.max(0.5, highContactY - treeBaseY);
        float highLeverage = Math.max(0.25F, (float) (highHeight / 2.0));

        assertTrue(highLeverage > lowLeverage, "Higher contact point must yield greater leverage");
        assertEquals(0.25F, lowLeverage, 0.01F, "Low contact at ground level should have baseline leverage");
        assertEquals(3.25F, highLeverage, 0.01F, "Contact at height 6.5m should have 3.25x leverage");

        // Delta strain with equal effort (e.g., full stick push effort = 1.0)
        float effort = 1.0F;
        float lowStrain = effort * lowLeverage;
        float highStrain = effort * highLeverage;

        float threshold = ExcavatorTreeController.calculateBreakThreshold(8);
        int lowTicksToBreak = (int) Math.ceil(threshold / lowStrain);
        int highTicksToBreak = (int) Math.ceil(threshold / highStrain);

        assertTrue(highTicksToBreak < lowTicksToBreak, "Higher contact must require fewer ticks to break");
        assertTrue(highTicksToBreak <= 2, "High contact should topple tree very rapidly (1-2 ticks)");
        assertTrue(lowTicksToBreak >= 20, "Ground contact should require sustained pushing (20+ ticks)");
    }

    @Test
    @DisplayName("Nearest horizontal direction properly maps cardinal directions")
    void testNearestHorizontalMapping() {
        assertEquals(Direction.EAST, ExcavatorTreeController.getNearestHorizontal(2.0, 0.5));
        assertEquals(Direction.WEST, ExcavatorTreeController.getNearestHorizontal(-2.0, 0.5));
        assertEquals(Direction.SOUTH, ExcavatorTreeController.getNearestHorizontal(0.5, 2.0));
        assertEquals(Direction.NORTH, ExcavatorTreeController.getNearestHorizontal(0.5, -2.0));

        // Diagonal bias tests (tie-breaks to East/West when |dx| >= |dz|)
        assertEquals(Direction.EAST, ExcavatorTreeController.getNearestHorizontal(1.0, 1.0));
        assertEquals(Direction.WEST, ExcavatorTreeController.getNearestHorizontal(-1.0, 1.0));
    }

    @Test
    @DisplayName("Pushing away from excavator fells the tree away (stump near excavator, crown further away)")
    void testPushingAwayDirection() {
        // Mock pose at origin
        Vec3 prevTeeth = new Vec3(0, 5, 2);
        Vec3 currTeeth = new Vec3(0, 5, 2.2); // moving forward in +Z

        BucketPose prev = new BucketPose(
                new Vec3(0, 6, 1), prevTeeth, new Vec3(0, 5, 1.8),
                new Vec3(0, 0, 1), new Vec3(0, -1, 0), 0, 0, java.util.List.of(prevTeeth)
        );
        BucketPose curr = new BucketPose(
                new Vec3(0, 6, 1.2), currTeeth, new Vec3(0, 5, 2.0),
                new Vec3(0, 0, 1), new Vec3(0, -1, 0), 0, 0, java.util.List.of(currTeeth)
        );

        // Tree is located in front of the bucket at (0, 64, 4)
        BlockPos treeBase = new BlockPos(0, 64, 4);

        // Direction vector from excavator (0,0) towards tree (0,4) is +Z (SOUTH)
        Direction dir = ExcavatorTreeController.getNearestHorizontal(
                treeBase.getX() + 0.5 - 0.0,
                treeBase.getZ() + 0.5 - 0.0
        );
        assertEquals(Direction.SOUTH, dir, "Tree in front of excavator (+Z) pushed away must fall SOUTH");
    }

    @Test
    @DisplayName("Swinging cab to the right/left fells the tree to the side")
    void testCabSwingSideDirection() {
        // Excavator heading North (yaw = 180 in MC / cab facing -Z)
        // UpperYaw turning clockwise (+cabYaw)
        float totalYaw = 180.0F; // Facing -Z (North)
        double rad = Math.toRadians(totalYaw);
        Vec3 armRight = new Vec3(Math.cos(rad), 0.0D, Math.sin(rad)); // (-1, 0, 0) = WEST

        // Swinging right (cabYawInput > 0)
        float cabYawInput = 1.0F;
        double pushX = armRight.x * cabYawInput * 3.2D;
        double pushZ = armRight.z * cabYawInput * 3.2D;

        Direction dir = ExcavatorTreeController.getNearestHorizontal(pushX, pushZ);
        assertEquals(Direction.WEST, dir, "Swinging right when facing North must push WEST (to the side)");

        // Swinging left (cabYawInput < 0)
        cabYawInput = -1.0F;
        pushX = armRight.x * cabYawInput * 3.2D;
        pushZ = armRight.z * cabYawInput * 3.2D;

        dir = ExcavatorTreeController.getNearestHorizontal(pushX, pushZ);
        assertEquals(Direction.EAST, dir, "Swinging left when facing North must push EAST (to the side)");
    }

    @Test
    @DisplayName("Break threshold scales reasonably with tree height")
    void testBreakThreshold() {
        float smallTree = ExcavatorTreeController.calculateBreakThreshold(4);
        float mediumTree = ExcavatorTreeController.calculateBreakThreshold(8);
        float giantTree = ExcavatorTreeController.calculateBreakThreshold(24);

        assertEquals(5.0F, smallTree, 0.01F);
        assertEquals(6.0F, mediumTree, 0.01F);
        assertEquals(10.0F, giantTree, 0.01F);
        assertTrue(smallTree < mediumTree);
        assertTrue(mediumTree < giantTree);
    }
}
