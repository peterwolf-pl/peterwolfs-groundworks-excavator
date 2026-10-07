package com.piotrek.groundworksexcavator.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Two roof floodlights. They face the cab front and light the ground in front
 * of the boom. They do not spin.
 */
public final class WorkLights {

    public static final int LEVEL = 15;

    /** Model pixels on upper_body. Left and right of the cab roof front lip. */
    public static final float[] LEFT = {-15.0F, -28.6F, 14.5F};
    public static final float[] RIGHT = {-5.0F, -28.6F, 14.5F};

    private static final int[] RANGES = {4, 8};

    private WorkLights() {}

    public static List<BlockPos> lightPositions(Vec3 leftLamp, Vec3 rightLamp, Vec3 feet, Vec3 facing) {
        Vec3 forward = horizontal(facing);
        Vec3 side = new Vec3(forward.z, 0.0D, -forward.x);
        List<BlockPos> positions = new ArrayList<>(8);
        addBeam(positions, leftLamp, feet, forward, side.scale(-0.55D));
        addBeam(positions, rightLamp, feet, forward, side.scale(0.55D));
        return positions;
    }

    private static void addBeam(List<BlockPos> positions, Vec3 lamp, Vec3 feet, Vec3 forward, Vec3 side) {
        for (int range : RANGES) {
            double x = lamp.x + forward.x * range + side.x * range;
            double z = lamp.z + forward.z * range + side.z * range;
            add(positions, BlockPos.containing(x, feet.y + 1.0D, z));
            add(positions, BlockPos.containing(x, feet.y + 2.0D, z));
        }
    }

    private static Vec3 horizontal(Vec3 facing) {
        Vec3 flat = new Vec3(facing.x, 0.0D, facing.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }
        return flat.normalize();
    }

    private static void add(List<BlockPos> positions, BlockPos pos) {
        if (!positions.contains(pos)) {
            positions.add(pos);
        }
    }
}
