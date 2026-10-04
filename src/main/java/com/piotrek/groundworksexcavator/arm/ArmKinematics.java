package com.piotrek.groundworksexcavator.arm;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Closed-form hierarchical forward kinematics for the excavator arm.
 *
 * <p>Hierarchy:
 * <pre>
 * Base (pos, yaw, pitch, roll)
 *   └─ Turntable (upperYaw)
 *       ├─ Driver Seat (local cab offset)
 *       └─ Boom Pivot (boomAngle)
 *           └─ Stick Pivot (stickAngle)
 *               └─ Bucket Pivot (bucketAngle)
 *                   ├─ Cutting Edge (teeth line segment)
 *                   ├─ Bucket Lip (deposition point)
 *                   └─ Scoop Normal (dump angle calculation)
 * </pre>
 */
public final class ArmKinematics {

    // ── Mechanical Dimensions (blocks / meters) ───────────────────────
    public static final double TURNTABLE_HEIGHT = 0.75D;
    public static final Vec3 BOOM_MOUNT_OFFSET = new Vec3(0.35D, 0.45D, 0.30D);
    public static final Vec3 CAB_SEAT_OFFSET = new Vec3(-0.75D, 0.85D, 0.35D);
    public static final Vec3 BEACON_ROOF_OFFSET = new Vec3(-0.625D, 1.85D, 0.75D);

    public static final double BOOM_LENGTH = 3.6D;
    public static final double STICK_LENGTH = 2.4D;
    public static final double BUCKET_LENGTH = 1.2D;
    public static final double BUCKET_WIDTH = 0.8D;
    public static final int TEETH_COUNT = 5;

    // ── Joint Limits (degrees) ────────────────────────────────────────
    public static final float BOOM_MIN = -28.0F;
    public static final float BOOM_MAX = 52.0F;

    public static final float STICK_MIN = -95.0F;
    public static final float STICK_MAX = 30.0F;

    // BUCKET_MIN calibrated to -60° so fully closed position curls tight under stick with 1.5px clearance (no clipping)
    // BUCKET_MAX calibrated to +65° so fully open position inverts completely vertically into dump
    public static final float BUCKET_MIN = -60.0F;
    public static final float BUCKET_MAX = 65.0F;

    public static final float BUCKET_MOUNT_OFFSET_DEG = 45.0F;

    // ── Joint Speeds (degrees per tick) ───────────────────────────────
    public static final float CAB_TURN_SPEED = 3.0F;
    public static final float BOOM_SPEED = 2.4F;
    public static final float STICK_SPEED = 2.8F;
    public static final float BUCKET_SPEED = 3.8F;

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
     * Compute the full forward kinematics for the excavator arm.
     */
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
        float totalYaw = baseYaw + upperYaw;
        double yawRad = Math.toRadians(totalYaw);

        // Unit vectors for upper body turntable orientation
        Vec3 upperHeading = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        Vec3 upperRight = new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
        Vec3 upperUp = new Vec3(0.0D, 1.0D, 0.0D);

        // Adjust for base pitch and roll if non-zero
        if (Math.abs(basePitch) > 0.01F || Math.abs(baseRoll) > 0.01F) {
            double pitchRad = Math.toRadians(basePitch);
            double rollRad = Math.toRadians(baseRoll);
            upperHeading = new Vec3(
                    upperHeading.x,
                    -Math.sin(pitchRad),
                    upperHeading.z * Math.cos(pitchRad)
            ).normalize();
            upperUp = new Vec3(
                    Math.sin(rollRad),
                    Math.cos(pitchRad) * Math.cos(rollRad),
                    0.0D
            ).normalize();
            upperRight = upperHeading.cross(upperUp).normalize();
        }

        // Turntable center in world space
        Vec3 turntableCenter = basePos.add(0.0D, TURNTABLE_HEIGHT, 0.0D);

        // Boom base mount point
        Vec3 boomBase = turntableCenter
                .add(upperRight.scale(BOOM_MOUNT_OFFSET.x))
                .add(upperUp.scale(BOOM_MOUNT_OFFSET.y))
                .add(upperHeading.scale(BOOM_MOUNT_OFFSET.z));

        // 1. Boom joint
        double boomRad = Math.toRadians(boomAngle);
        Vec3 boomDir = upperHeading.scale(Math.cos(boomRad)).add(upperUp.scale(Math.sin(boomRad)));
        Vec3 stickPivot = boomBase.add(boomDir.scale(BOOM_LENGTH));

        // 2. Stick joint (relative to boom)
        float totalStickPitch = boomAngle + stickAngle;
        double stickRad = Math.toRadians(totalStickPitch);
        Vec3 stickDir = upperHeading.scale(Math.cos(stickRad)).add(upperUp.scale(Math.sin(stickRad)));
        Vec3 bucketPivot = stickPivot.add(stickDir.scale(STICK_LENGTH));

        // 3. Bucket joint (relative to stick)
        // Includes the 50° mounting offset so fully open position (BUCKET_MAX) inverts the scoop completely
        float totalBucketPitch = totalStickPitch - (bucketAngle + BUCKET_MOUNT_OFFSET_DEG);
        double bucketRad = Math.toRadians(totalBucketPitch);
        Vec3 bucketDir = upperHeading.scale(Math.cos(bucketRad)).add(upperUp.scale(Math.sin(bucketRad)));
        Vec3 cuttingEdge = bucketPivot.add(bucketDir.scale(BUCKET_LENGTH));

        // Bucket lip (exit point for dumped material)
        Vec3 lip = cuttingEdge.add(bucketDir.scale(0.12D)).add(upperUp.scale(-0.08D));

        // Scoop opening normal (perpendicular to bucket direction)
        Vec3 scoopNormal = upperHeading.scale(-Math.sin(bucketRad)).add(upperUp.scale(Math.cos(bucketRad)));

        // Dump tilt angle: how much the bucket cutting edge / lip points downwards
        // A value >= 30 deg (i.e. bucketDir.y <= -0.5) allows granular material to slide out
        float dumpTilt = (float) Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, -bucketDir.y))));

        // Discrete sample points along cutting edge teeth
        List<Vec3> teeth = new ArrayList<>(TEETH_COUNT);
        double halfWidth = BUCKET_WIDTH * 0.5D;
        double step = BUCKET_WIDTH / (TEETH_COUNT - 1);
        for (int i = 0; i < TEETH_COUNT; i++) {
            double offset = -halfWidth + (i * step);
            teeth.add(cuttingEdge.add(upperRight.scale(offset)));
        }

        return new BucketPose(
                bucketPivot,
                cuttingEdge,
                lip,
                bucketDir,
                scoopNormal,
                totalBucketPitch,
                dumpTilt,
                teeth
        );
    }

    /**
     * Compute world position of the driver seat inside the rotating cab.
     */
    public static Vec3 getDriverSeatWorldPosition(Vec3 basePos, float baseYaw, float upperYaw) {
        float totalYaw = baseYaw + upperYaw;
        double yawRad = Math.toRadians(totalYaw);
        Vec3 upperHeading = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        Vec3 upperRight = new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
        Vec3 upperUp = new Vec3(0.0D, 1.0D, 0.0D);

        Vec3 turntableCenter = basePos.add(0.0D, TURNTABLE_HEIGHT, 0.0D);
        return turntableCenter
                .add(upperRight.scale(CAB_SEAT_OFFSET.x))
                .add(upperUp.scale(CAB_SEAT_OFFSET.y))
                .add(upperHeading.scale(CAB_SEAT_OFFSET.z));
    }

    /**
     * Compute world position of the warning beacon on top of the cab roof.
     */
    public static Vec3 getBeaconWorldPosition(Vec3 basePos, float baseYaw, float upperYaw) {
        float totalYaw = baseYaw + upperYaw;
        double yawRad = Math.toRadians(totalYaw);
        Vec3 upperHeading = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        Vec3 upperRight = new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
        Vec3 upperUp = new Vec3(0.0D, 1.0D, 0.0D);

        Vec3 turntableCenter = basePos.add(0.0D, TURNTABLE_HEIGHT, 0.0D);
        return turntableCenter
                .add(upperRight.scale(BEACON_ROOF_OFFSET.x))
                .add(upperUp.scale(BEACON_ROOF_OFFSET.y))
                .add(upperHeading.scale(BEACON_ROOF_OFFSET.z));
    }
}
