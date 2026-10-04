package com.piotrek.groundworksexcavator.excavation;

import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes swept cutting volume and candidate terrain positions between two simulation ticks.
 *
 * <p>Prevents fake instant digging: terrain is only excavated along the path traced
 * by the cutting edge when moving in a cutting direction.
 */
public final class SweptBucketVolume {

    public static final double MIN_DIG_SPEED = 0.008D; // minimum movement (meters/tick) to trigger cutting
    public static final double MAX_VALID_SPEED = 2.5D; // ignore teleportation / respawn discontinuities
    public static final int SWEEP_SUBDIVISIONS = 4;

    public record SweptResult(
            boolean valid,
            List<BlockPos> hitPositions,
            Vec3 hitLocation,
            double movementDistance
    ) {
        public static final SweptResult EMPTY = new SweptResult(false, List.of(), Vec3.ZERO, 0.0D);
    }

    private SweptBucketVolume() {}

    /**
     * Compute swept candidate positions between previous and current bucket poses.
     */
    public static SweptResult compute(BucketPose previous, BucketPose current) {
        if (previous == null || current == null) {
            return SweptResult.EMPTY;
        }

        Vec3 prevCenter = previous.cuttingEdge();
        Vec3 currCenter = current.cuttingEdge();
        Vec3 motion = currCenter.subtract(prevCenter);
        double dist = motion.length();

        // 1. Must be actively moving
        if (dist < MIN_DIG_SPEED || dist > MAX_VALID_SPEED) {
            return SweptResult.EMPTY;
        }

        // 2. Motion direction check: cutting edge must lead into the motion
        Vec3 motionDir = motion.normalize();
        // The bucket cuts when moving in the forward cutting direction or curling
        double cuttingAlignment = motionDir.dot(current.forwardCutting());
        if (cuttingAlignment < -0.35D) {
            // Moving backwards with rear of bucket: not a cutting action
            return SweptResult.EMPTY;
        }

        // 3. Sweep interpolation across cutting teeth
        Set<BlockPos> uniquePositions = new LinkedHashSet<>();
        List<Vec3> prevTeeth = previous.teethPoints();
        List<Vec3> currTeeth = current.teethPoints();
        int teethCount = Math.min(prevTeeth.size(), currTeeth.size());

        for (int step = 0; step <= SWEEP_SUBDIVISIONS; step++) {
            double alpha = (double) step / SWEEP_SUBDIVISIONS;
            for (int t = 0; t < teethCount; t++) {
                Vec3 p0 = prevTeeth.get(t);
                Vec3 p1 = currTeeth.get(t);
                Vec3 pt = p0.lerp(p1, alpha);
                uniquePositions.add(BlockPos.containing(pt.x, pt.y, pt.z));
            }
        }

        return new SweptResult(
                !uniquePositions.isEmpty(),
                new ArrayList<>(uniquePositions),
                currCenter,
                dist
        );
    }
}
