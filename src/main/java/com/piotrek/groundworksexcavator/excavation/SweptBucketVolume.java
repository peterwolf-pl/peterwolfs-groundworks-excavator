package com.piotrek.groundworksexcavator.excavation;

import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes swept cutting volume and tooth-level contact points between two simulation ticks.
 */
public final class SweptBucketVolume {

    public static final double MIN_DIG_SPEED = 0.006D; // minimum movement to trigger cutting
    public static final double MAX_VALID_SPEED = 2.5D; // ignore teleportation / respawn discontinuities
    public static final int SWEEP_SUBDIVISIONS = 4;

    public record ToothContact(BlockPos pos, Vec3 worldPoint) {}

    public record SweptResult(
            boolean valid,
            List<ToothContact> contacts,
            Vec3 hitLocation,
            double movementDistance
    ) {
        public static final SweptResult EMPTY = new SweptResult(false, List.of(), Vec3.ZERO, 0.0D);

        public List<BlockPos> hitPositions() {
            List<BlockPos> list = new ArrayList<>(contacts.size());
            for (ToothContact c : contacts) {
                list.add(c.pos());
            }
            return list;
        }
    }

    private SweptBucketVolume() {}

    /**
     * Compute swept candidate tooth contact positions between previous and current bucket poses.
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

        // 2. Direction check
        Vec3 motionDir = motion.normalize();
        double cuttingAlignment = motionDir.dot(current.forwardCutting());
        if (cuttingAlignment < -0.55D) {
            // Moving directly away from cutting orientation
            return SweptResult.EMPTY;
        }

        // 3. Sweep interpolation across all cutting teeth
        Map<BlockPos, Vec3> uniqueContacts = new LinkedHashMap<>();
        List<Vec3> prevTeeth = previous.teethPoints();
        List<Vec3> currTeeth = current.teethPoints();
        int teethCount = Math.min(prevTeeth.size(), currTeeth.size());

        for (int step = 0; step <= SWEEP_SUBDIVISIONS; step++) {
            double alpha = (double) step / SWEEP_SUBDIVISIONS;
            for (int t = 0; t < teethCount; t++) {
                Vec3 p0 = prevTeeth.get(t);
                Vec3 p1 = currTeeth.get(t);
                Vec3 pt = p0.lerp(p1, alpha);
                BlockPos pos = BlockPos.containing(pt.x, pt.y, pt.z);
                uniqueContacts.putIfAbsent(pos, pt);
            }
        }

        List<ToothContact> contactList = new ArrayList<>(uniqueContacts.size());
        for (Map.Entry<BlockPos, Vec3> entry : uniqueContacts.entrySet()) {
            contactList.add(new ToothContact(entry.getKey(), entry.getValue()));
        }

        return new SweptResult(
                !contactList.isEmpty(),
                contactList,
                currCenter,
                dist
        );
    }
}
