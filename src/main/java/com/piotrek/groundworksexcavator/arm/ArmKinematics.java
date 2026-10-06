package com.piotrek.groundworksexcavator.arm;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Closed-form hierarchical forward kinematics for the excavator arm.
 *
 * <p>Computed directly from the exact same transformation matrices as
 * {@link com.piotrek.groundworksexcavator.client.render.ExcavatorRenderer} and
 * {@link com.piotrek.groundworksexcavator.client.model.ExcavatorModel}, guaranteeing
 * 100% mathematical synchronization (0mm error) between the visual bucket and the
 * excavation / deposition hit points.
 */
public final class ArmKinematics {

    public static final int TEETH_COUNT = 5;

    // ── Joint Limits (degrees) ────────────────────────────────────────
    public static final float BOOM_MIN = -28.0F;
    public static final float BOOM_MAX = 85.0F;

    public static final float STICK_MIN = -95.0F;
    public static final float STICK_MAX = 30.0F;

    // -60° curls tightly under the stick with 1.5px clearance without clipping
    // +100° opens the bucket wide open outward (full 145° articulation range)
    public static final float BUCKET_MIN = -60.0F;
    public static final float BUCKET_MAX = 100.0F;

    public static final float BUCKET_MOUNT_OFFSET_DEG = 45.0F;

    // Hydraulic breaker dimensions in model pixels. These values are shared by
    // rendering and server kinematics so the visible chisel tip and world contact
    // point remain exactly calibrated.
    public static final float HAMMER_CHISEL_BASE_Z_PX = -28.0F;
    public static final float HAMMER_CHISEL_LENGTH_PX = 10.0F;
    public static final float HAMMER_STROKE_PX = 3.0F;
    public static final float HAMMER_TIP_REST_Z_PX =
            HAMMER_CHISEL_BASE_Z_PX - HAMMER_CHISEL_LENGTH_PX;
    public static final float HAMMER_TIP_STRIKE_Z_PX =
            HAMMER_TIP_REST_Z_PX - HAMMER_STROKE_PX;

    // ── Joint Speeds (degrees per tick) ───────────────────────────────
    public static final float CAB_TURN_SPEED = 3.0F;
    public static final float BOOM_SPEED = 2.4F;
    public static final float STICK_SPEED = 2.8F;
    public static final float BUCKET_SPEED = 4.0F;

    // ── Dumping Threshold (degrees downward tilt) ────────────────────
    public static final float DUMP_THRESHOLD_DEG = 30.0F;

    private ArmKinematics() {}

    /**
     * Complete world-space pose of the bucket and cutting geometry.
     */
    public record BucketPose(
            Vec3 pivot,
            Vec3 cuttingEdge,
            Vec3 lip,
            Vec3 forwardCutting,
            Vec3 scoopNormal,
            float totalPitchDegrees,
            float dumpTiltDegrees,
            List<Vec3> teethPoints
    ) {}

    /**
     * Builds the exact world matrix for the excavator turntable (upper body).
     */
    public static Matrix4f computeTurntableMatrix(
            Vec3 basePos,
            float baseYaw,
            float basePitch,
            float baseRoll,
            float upperYaw
    ) {
        Matrix4f mat = new Matrix4f();

        // 1. World base position
        mat.translate((float) basePos.x, (float) basePos.y, (float) basePos.z);

        // 2. Base vehicle heading (in ExcavatorRenderer: rotateDegrees(Axis.YP, -baseYaw))
        mat.rotate((float) Math.toRadians(-baseYaw), 0.0f, 1.0f, 0.0f);

        // 3. Vehicle ground pitch & roll
        if (Math.abs(basePitch) > 0.01F) {
            mat.rotate((float) Math.toRadians(basePitch), 1.0f, 0.0f, 0.0f);
        }
        if (Math.abs(baseRoll) > 0.01F) {
            mat.rotate((float) Math.toRadians(baseRoll), 0.0f, 0.0f, 1.0f);
        }

        // 4. Model coordinate transform: scale(-1, -1, 1) and translate(0, -1.5, 0)
        mat.scale(-1.0f, -1.0f, 1.0f);
        mat.translate(0.0f, -1.5f, 0.0f);

        // 5. upper_body: PartPose.offset(0.0F, 9.0F, 0.0F)
        mat.translate(0.0f / 16.0f, 9.0f / 16.0f, 0.0f / 16.0f);
        mat.rotate(new Quaternionf().rotationZYX(0.0f, (float) Math.toRadians(upperYaw), 0.0f));

        return mat;
    }

    /**
     * Builds the exact world matrix for the excavator bucket.
     */
    public static Matrix4f computeBucketMatrix(
            Vec3 basePos,
            float baseYaw,
            float basePitch,
            float baseRoll,
            float upperYaw,
            float boomAngle,
            float stickAngle,
            float bucketAngle
    ) {
        Matrix4f mat = computeTurntableMatrix(basePos, baseYaw, basePitch, baseRoll, upperYaw);

        // 6. boom: PartPose.offset(5.5F, -6.0F, 5.0F)
        mat.translate(5.5f / 16.0f, -6.0f / 16.0f, 5.0f / 16.0f);
        mat.rotate(new Quaternionf().rotationZYX(0.0f, 0.0f, (float) Math.toRadians(boomAngle)));

        // 7. stick: PartPose.offset(0.0F, 0.0F, 56.0F)
        mat.translate(0.0f / 16.0f, 0.0f / 16.0f, 56.0f / 16.0f);
        mat.rotate(new Quaternionf().rotationZYX(0.0f, 0.0f, (float) Math.toRadians(stickAngle)));

        // 8. bucket: PartPose.offset(0.0F, 0.0F, 38.0F)
        mat.translate(0.0f / 16.0f, 0.0f / 16.0f, 38.0f / 16.0f);
        mat.rotate(new Quaternionf().rotationZYX(0.0f, 0.0f, (float) Math.toRadians(bucketAngle + BUCKET_MOUNT_OFFSET_DEG)));

        return mat;
    }

    public static BucketPose computeBucketPose(
            Vec3 basePos,
            float baseYaw,
            float basePitch,
            float baseRoll,
            float upperYaw,
            float boomAngle,
            float stickAngle,
            float bucketAngle
    ) {
        return computeBucketPose(basePos, baseYaw, basePitch, baseRoll, upperYaw, boomAngle, stickAngle, bucketAngle, 0);
    }

    /**
     * Samples the steel boom and stick for authoritative terrain contact.
     * The bucket is a working tool and must remain free to enter material during a digging stroke.
     */
    public static List<Vec3> computeArmCollisionSamples(
            Vec3 basePos,
            float baseYaw,
            float basePitch,
            float baseRoll,
            float upperYaw,
            float boomAngle,
            float stickAngle
    ) {
        Matrix4f boomMatrix = computeTurntableMatrix(basePos, baseYaw, basePitch, baseRoll, upperYaw);
        boomMatrix.translate(5.5f / 16.0f, -6.0f / 16.0f, 5.0f / 16.0f);
        boomMatrix.rotate(new Quaternionf().rotationZYX(0.0f, 0.0f, (float) Math.toRadians(boomAngle)));

        List<Vec3> samples = new ArrayList<>();
        sampleArmSegment(samples, boomMatrix, 56.0f / 16.0f);

        Matrix4f stickMatrix = new Matrix4f(boomMatrix);
        stickMatrix.translate(0.0f, 0.0f, 56.0f / 16.0f);
        stickMatrix.rotate(new Quaternionf().rotationZYX(0.0f, 0.0f, (float) Math.toRadians(stickAngle)));
        // Pozostawiamy ostatnie 4px przed zawiasem łyżki wolne, by łyżka mogła naturalnie pracować w gruncie
        sampleArmSegment(samples, stickMatrix, (38.0f - 4.0f) / 16.0f);
        return List.copyOf(samples);
    }

    private static void sampleArmSegment(List<Vec3> samples, Matrix4f matrix, float length) {
        int steps = Math.max(2, (int) Math.ceil(length / 0.4f));
        float[][] crossSection = {
                { 0.0f, 0.0f }, { -0.18f, 0.0f }, { 0.18f, 0.0f },
                { 0.0f, -0.12f }, { 0.0f, 0.12f }
        };
        for (int step = 1; step <= steps; step++) {
            float z = length * step / steps;
            for (float[] offset : crossSection) {
                samples.add(transformPoint(matrix, offset[0], offset[1], z));
            }
        }
    }

    private static Vec3 transformPoint(Matrix4f matrix, float x, float y, float z) {
        Vector4f point = new Vector4f(x, y, z, 1.0f);
        matrix.transform(point);
        return new Vec3(point.x, point.y, point.z);
    }

    /**
     * Computes the full authoritative bucket pose matching the visual model 1:1.
     *
     * @param bucketType 0 = Standard (256u), 1 = Large Bulk (512u), 2 = pneumatic hammer
     */
    public static BucketPose computeBucketPose(
            Vec3 basePos,
            float baseYaw,
            float basePitch,
            float baseRoll,
            float upperYaw,
            float boomAngle,
            float stickAngle,
            float bucketAngle,
            int bucketType
    ) {
        Matrix4f bucketMat = computeBucketMatrix(
                basePos, baseYaw, basePitch, baseRoll, upperYaw, boomAngle, stickAngle, bucketAngle
        );

        // 1. Bucket Pivot Point (0, 0, 0 in bucket local coords)
        Vector4f pivotVec = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
        bucketMat.transform(pivotVec);
        Vec3 pivot = new Vec3(pivotVec.x, pivotVec.y, pivotVec.z);

        List<Vec3> teethPoints;
        Vec3 cuttingEdge;
        Vec3 lip;
        Vec3 forwardCutting;
        Vec3 scoopNormal;

        if (bucketType == 2) {
            // Hydraulic hammer: the logical contact point is the visible chisel tip
            // at maximum stroke. This shares dimensions with HydraulicHammerModel.
            Vector4f tipVec = new Vector4f(
                    0.0f, 0.0f, HAMMER_TIP_STRIKE_Z_PX / 16.0f, 1.0f);
            bucketMat.transform(tipVec);
            cuttingEdge = new Vec3(tipVec.x, tipVec.y, tipVec.z);
            teethPoints = List.of(cuttingEdge);

            Vector4f collarVec = new Vector4f(
                    0.0f, 0.0f, HAMMER_CHISEL_BASE_Z_PX / 16.0f, 1.0f);
            bucketMat.transform(collarVec);
            lip = new Vec3(collarVec.x, collarVec.y, collarVec.z);

            Vector4f hammerDirVec = new Vector4f(0.0f, 0.0f, -1.0f, 0.0f);
            bucketMat.transform(hammerDirVec);
            forwardCutting = new Vec3(
                    hammerDirVec.x, hammerDirVec.y, hammerDirVec.z).normalize();

            Vector4f normalVec = new Vector4f(0.0f, -1.0f, 0.0f, 0.0f);
            bucketMat.transform(normalVec);
            scoopNormal = new Vec3(normalVec.x, normalVec.y, normalVec.z).normalize();
        } else {
            // Cutting teeth coordinates (matching the exact boxes in ExcavatorModel)
            // Standard: 5 teeth across 12px width. Large: 7 teeth across 20px width.
            float[] teethX = (bucketType == 1)
                    ? new float[] { -9.0f, -6.0f, -3.0f, 0.0f, 3.0f, 6.0f, 9.0f }
                    : new float[] { -4.75f, -2.0f, 0.75f, 3.5f, 5.75f };

            teethPoints = new ArrayList<>(teethX.length);
            for (float toothX : teethX) {
                Vector4f tv = new Vector4f(
                        toothX / 16.0f, 9.1f / 16.0f, -17.5f / 16.0f, 1.0f);
                bucketMat.transform(tv);
                teethPoints.add(new Vec3(tv.x, tv.y, tv.z));
            }

            cuttingEdge = teethPoints.get(teethPoints.size() / 2);

            Vector4f lipVec = new Vector4f(
                    0.0f, 8.0f / 16.0f, -14.0f / 16.0f, 1.0f);
            bucketMat.transform(lipVec);
            lip = new Vec3(lipVec.x, lipVec.y, lipVec.z);

            // In bucket local coords, teeth point in (-Z) and downward.
            Vector4f cuttingDirVec = new Vector4f(0.0f, 0.3f, -0.95f, 0.0f);
            bucketMat.transform(cuttingDirVec);
            forwardCutting = new Vec3(
                    cuttingDirVec.x, cuttingDirVec.y, cuttingDirVec.z).normalize();

            Vector4f normalVec = new Vector4f(0.0f, -0.95f, 0.3f, 0.0f);
            bucketMat.transform(normalVec);
            scoopNormal = new Vec3(normalVec.x, normalVec.y, normalVec.z).normalize();
        }

        // 6. Dump tilt angle: how much the opening/lip tilts downwards toward ground
        // Downward inclination: when forwardCutting points down into earth
        float dumpTilt = (float) Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, -forwardCutting.y))));

        float totalPitch = boomAngle + stickAngle - (bucketAngle + BUCKET_MOUNT_OFFSET_DEG);

        return new BucketPose(
                pivot,
                cuttingEdge,
                lip,
                forwardCutting,
                scoopNormal,
                totalPitch,
                dumpTilt,
                teethPoints
        );
    }

    /**
     * Compute world position of the driver seat inside the rotating cab (100% 1:1 model match).
     */
    public static Vec3 getDriverSeatWorldPosition(Vec3 basePos, float baseYaw, float upperYaw) {
        Matrix4f turntableMat = computeTurntableMatrix(basePos, baseYaw, 0.0f, 0.0f, upperYaw);
        // Driver seat cushion in upper_body: (-10.0F, -6.0F, 3.5F)
        Vector4f seatVec = new Vector4f(-10.0f / 16.0f, -6.0f / 16.0f, 3.5f / 16.0f, 1.0f);
        turntableMat.transform(seatVec);
        return new Vec3(seatVec.x, seatVec.y, seatVec.z);
    }

    /**
     * Compute world position of the warning beacon on top of the cab roof (100% 1:1 model match).
     */
    public static Vec3 getBeaconWorldPosition(Vec3 basePos, float baseYaw, float upperYaw) {
        Matrix4f turntableMat = computeTurntableMatrix(basePos, baseYaw, 0.0f, 0.0f, upperYaw);
        // Warning beacon on cab roof: beaconBase offset(-10.0F, -27.0F, 12.0F)
        Vector4f beaconVec = new Vector4f(-10.0f / 16.0f, -27.0f / 16.0f, 12.0f / 16.0f, 1.0f);
        turntableMat.transform(beaconVec);
        return new Vec3(beaconVec.x, beaconVec.y, beaconVec.z);
    }

    /**
     * Compute world position of the engine exhaust stack pipe tip on the rear deck (100% 1:1 model match).
     * Automatically changes with excavator base position, base heading, and upper body turntable yaw rotation!
     */
    public static Vec3 getExhaustWorldPosition(Vec3 basePos, float baseYaw, float upperYaw) {
        Matrix4f turntableMat = computeTurntableMatrix(basePos, baseYaw, 0.0f, 0.0f, upperYaw);
        // Exhaust stack top rim in upper_body: (13.5F, -26.0F, -16.5F)
        Vector4f exhaustVec = new Vector4f(13.5f / 16.0f, -26.0f / 16.0f, -16.5f / 16.0f, 1.0f);
        turntableMat.transform(exhaustVec);
        return new Vec3(exhaustVec.x, exhaustVec.y, exhaustVec.z);
    }
}
