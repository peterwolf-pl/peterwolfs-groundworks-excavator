package com.piotrek.groundworksexcavator.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Rotating roof beacon. The lamp is the light source. Block light is placed
 * along the direction it faces and moves with it. No torch, no painted ray.
 */
public final class BeaconLight {

    /** About two turns a second. A beacon, not a slow sign. */
    public static final float SPIN_PER_TICK = 0.63F;

    /** How far the thrown light reaches, in blocks. */
    public static final int[] RANGES = {2, 5, 8};

    public static final int LEVEL = 15;

    /** Light steps per turn. The lamp mesh spins smoothly; the light jumps with the lamp. */
    public static final int STEPS = 8;

    private BeaconLight() {}

    public static float spin(int tick, float partialTick) {
        return (tick + partialTick) * SPIN_PER_TICK;
    }

    public static int step(float spin) {
        float turns = spin / ((float) Math.PI * 2.0F);
        return Math.floorMod(Math.round(turns * STEPS), STEPS);
    }

    public static List<BlockPos> lightPositions(Vec3 lamp, Vec3 feet, Vec3 facing) {
        Vec3 flat = new Vec3(facing.x, 0.0D, facing.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        flat = flat.normalize();
        List<BlockPos> positions = new ArrayList<>(RANGES.length * 2);
        for (int range : RANGES) {
            double x = lamp.x + flat.x * range;
            double z = lamp.z + flat.z * range;
            add(positions, BlockPos.containing(x, feet.y + 1.0D, z));
            add(positions, BlockPos.containing(x, lamp.y, z));
        }
        return positions;
    }

    private static void add(List<BlockPos> positions, BlockPos pos) {
        if (!positions.contains(pos)) {
            positions.add(pos);
        }
    }
}
