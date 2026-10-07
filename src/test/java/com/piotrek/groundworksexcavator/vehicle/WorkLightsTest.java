package com.piotrek.groundworksexcavator.vehicle;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkLightsTest {

    @Test
    void lampsSitOnTheCabFrontAndAreSeparated() {
        Vec3 left = lamp(WorkLights.LEFT);
        Vec3 right = lamp(WorkLights.RIGHT);
        assertTrue(left.z > 0.4D, "left lamp must face the boom, not the counterweight");
        assertTrue(right.z > 0.4D);
        assertTrue(left.y > 0.4D);
        assertTrue(Math.abs(left.x - right.x) > 0.4D);
    }

    @Test
    void beamsLightTheGroundAheadAndDoNotMeetOnOneLine() {
        Vec3 left = new Vec3(-0.4D, 2.2D, 0.8D);
        Vec3 right = new Vec3(0.4D, 2.2D, 0.8D);
        List<BlockPos> lights = WorkLights.lightPositions(left, right, Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D));
        assertEquals(8, lights.size());
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        for (BlockPos pos : lights) {
            assertTrue(pos.getZ() >= 4, "light must sit in the work field, not on the roof");
            assertTrue(pos.getY() == 1 || pos.getY() == 2);
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
        }
        assertTrue(maxX - minX >= 2, "the two lamps must cover a strip, not one column");
    }

    private static Vec3 lamp(float[] offset) {
        return ArmKinematics.getUpperBodyPoint(
                Vec3.ZERO, 0.0F, 0.0F, 0.0F, 0.0F, offset[0], offset[1], offset[2]
        );
    }
}
