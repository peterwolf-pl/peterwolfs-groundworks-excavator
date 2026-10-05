package com.piotrek.groundworksexcavator.excavation;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import com.piotrek.groundworksexcavator.integration.groundworks.GroundworksExcavationAdapter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Rejects arm candidates that force excavator steel through granular terrain.
 * Teeth are handled separately so a correctly aligned digging stroke still works.
 */
public final class ArmTerrainContactController {

    private static final double MIN_LATERAL_DRAG = 0.02D;
    private static final double LATERAL_TO_FORWARD_RATIO = 0.5D;

    private ArmTerrainContactController() {}

    public record JointAngles(float cabin, float boom, float stick, float bucket) {}

    public static JointAngles constrain(
            ServerLevel level,
            Vec3 basePos,
            float baseYaw,
            float basePitch,
            float baseRoll,
            int bucketType,
            JointAngles current,
            JointAngles requested
    ) {
        JointAngles accepted = current;
        if (requested.cabin() != accepted.cabin()) {
            accepted = acceptCandidate(level, basePos, baseYaw, basePitch, baseRoll,
                    bucketType, accepted,
                    new JointAngles(requested.cabin(), accepted.boom(), accepted.stick(), accepted.bucket()));
        }
        if (requested.boom() != accepted.boom()) {
            accepted = acceptCandidate(level, basePos, baseYaw, basePitch, baseRoll,
                    bucketType, accepted,
                    new JointAngles(accepted.cabin(), requested.boom(), accepted.stick(), accepted.bucket()));
        }
        if (requested.stick() != accepted.stick()) {
            accepted = acceptCandidate(level, basePos, baseYaw, basePitch, baseRoll,
                    bucketType, accepted,
                    new JointAngles(accepted.cabin(), accepted.boom(), requested.stick(), accepted.bucket()));
        }
        if (requested.bucket() != accepted.bucket()) {
            accepted = acceptCandidate(level, basePos, baseYaw, basePitch, baseRoll,
                    bucketType, accepted,
                    new JointAngles(accepted.cabin(), accepted.boom(), accepted.stick(), requested.bucket()));
        }
        return accepted;
    }

    public static boolean allowsMachineMotion(
            ServerLevel level,
            Vec3 currentBase,
            float currentYaw,
            float currentPitch,
            float currentRoll,
            Vec3 candidateBase,
            float candidateYaw,
            float candidatePitch,
            float candidateRoll,
            int bucketType,
            JointAngles angles
    ) {
        BucketPose currentPose = pose(
                currentBase, currentYaw, currentPitch, currentRoll, bucketType, angles);
        BucketPose candidatePose = pose(
                candidateBase, candidateYaw, candidatePitch, candidateRoll, bucketType, angles);

        Vec3 movement = candidatePose.cuttingEdge().subtract(currentPose.cuttingEdge());

        // Reverse / extraction motion (wyjazd gąsienicami w tył) jest ZAWSZE dozwolony!
        // Pozwala to operatorowi na bezproblemowe wycofanie koparki z wykopu.
        boolean movingBackward = movement.dot(currentPose.forwardCutting()) < -0.001D;
        if (movingBackward) {
            return true;
        }

        if (isBlockedLateralDrag(
                areTeethEmbedded(level, currentPose),
                movement,
                currentPose.forwardCutting())) {
            return false;
        }
        return allowsArmTransition(
                level,
                armSamples(currentBase, currentYaw, currentPitch, currentRoll, angles),
                armSamples(candidateBase, candidateYaw, candidatePitch, candidateRoll, angles));
    }

    public static boolean areTeethEmbedded(ServerLevel level, BucketPose pose) {
        for (Vec3 point : pose.teethPoints()) {
            if (GroundworksExcavationAdapter.containsMaterialAt(level, point)) {
                return true;
            }
        }
        return false;
    }

    static boolean allowsPenetrationChange(int currentSamples, int candidateSamples) {
        if (candidateSamples == 0) return true;
        // Dozwolone utrzymanie lub zmniejszenie penetracji podczas wyrywania/ruchu w gruncie
        return candidateSamples <= currentSamples;
    }

    public static boolean isBlockedLateralDrag(
            boolean teethEmbedded, Vec3 movement, Vec3 cuttingDirection) {
        if (!teethEmbedded) return false;

        double moveLength = Math.hypot(movement.x, movement.z);
        double cuttingLength = Math.hypot(cuttingDirection.x, cuttingDirection.z);
        if (moveLength < MIN_LATERAL_DRAG) return false;
        if (cuttingLength < 1.0E-6D) return true;

        double moveX = movement.x / moveLength;
        double moveZ = movement.z / moveLength;
        double cuttingX = cuttingDirection.x / cuttingLength;
        double cuttingZ = cuttingDirection.z / cuttingLength;
        double lateral = Math.abs(moveX * cuttingZ - moveZ * cuttingX) * moveLength;
        double forward = Math.abs(moveX * cuttingX + moveZ * cuttingZ) * moveLength;
        return lateral > MIN_LATERAL_DRAG
                && lateral > forward * LATERAL_TO_FORWARD_RATIO;
    }

    private static JointAngles acceptCandidate(
            ServerLevel level,
            Vec3 basePos,
            float baseYaw,
            float basePitch,
            float baseRoll,
            int bucketType,
            JointAngles current,
            JointAngles candidate
    ) {
        // 1. Obrót samej łyżki nigdy nie przesuwa wysięgnika ani przedramienia - zawsze dozwolony
        if (current.cabin() == candidate.cabin() && current.boom() == candidate.boom() && current.stick() == candidate.stick()) {
            return candidate;
        }

        // 2. Podnoszenie wysięgnika w górę (Arrow Up) jest ZAWSZE dozwolone!
        // Operator musi mieć pełną moc hydrauliczną, by unieść ramię w górę i wyciągnąć łyżkę z gruntu.
        if (candidate.boom() > current.boom() && current.cabin() == candidate.cabin()) {
            return candidate;
        }

        BucketPose currentPose = pose(
                basePos, baseYaw, basePitch, baseRoll, bucketType, current);
        BucketPose candidatePose = pose(
                basePos, baseYaw, basePitch, baseRoll, bucketType, candidate);

        Vec3 toothMovement = candidatePose.cuttingEdge().subtract(currentPose.cuttingEdge());
        if (requiresEmbeddedToothLateralCheck(current, candidate)
                && isBlockedLateralDrag(
                        areTeethEmbedded(level, currentPose),
                        toothMovement,
                        currentPose.forwardCutting())) {
            return current;
        }

        return allowsArmTransition(
                level,
                armSamples(basePos, baseYaw, basePitch, baseRoll, current),
                armSamples(basePos, baseYaw, basePitch, baseRoll, candidate))
                ? candidate
                : current;
    }

    static boolean requiresEmbeddedToothLateralCheck(
            JointAngles current, JointAngles candidate) {
        return current.cabin() != candidate.cabin();
    }

    private static boolean allowsArmTransition(
            ServerLevel level, List<Vec3> currentSamples, List<Vec3> candidateSamples) {
        int currentPenetration = 0;
        int candidatePenetration = 0;
        boolean introducedNewPenetration = false;
        for (int index = 0; index < currentSamples.size(); index++) {
            boolean currentInside = GroundworksExcavationAdapter.containsMaterialAt(
                    level, currentSamples.get(index));
            boolean candidateInside = GroundworksExcavationAdapter.containsMaterialAt(
                    level, candidateSamples.get(index));
            if (currentInside) currentPenetration++;
            if (candidateInside) candidatePenetration++;
            if (!currentInside && candidateInside) introducedNewPenetration = true;
        }
        return !introducedNewPenetration
                && allowsPenetrationChange(currentPenetration, candidatePenetration);
    }

    private static List<Vec3> armSamples(
            Vec3 basePos,
            float baseYaw,
            float basePitch,
            float baseRoll,
            JointAngles angles
    ) {
        return ArmKinematics.computeArmCollisionSamples(
                basePos, baseYaw, basePitch, baseRoll,
                angles.cabin(), angles.boom(), angles.stick());
    }

    private static BucketPose pose(
            Vec3 basePos,
            float baseYaw,
            float basePitch,
            float baseRoll,
            int bucketType,
            JointAngles angles
    ) {
        return ArmKinematics.computeBucketPose(
                basePos, baseYaw, basePitch, baseRoll,
                angles.cabin(), angles.boom(), angles.stick(), angles.bucket(), bucketType);
    }
}
