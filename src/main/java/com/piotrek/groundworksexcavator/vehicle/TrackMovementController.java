package com.piotrek.groundworksexcavator.vehicle;

import com.piotrek.groundworksexcavator.integration.groundworks.GroundworksExcavationAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Differential track movement physics and terrain-conforming suspension simulation.
 */
public class TrackMovementController {

    public static final double MAX_SPEED = 0.12D; // Realistic crawler speed (~2.4 m/s)
    public static final double ACCELERATION = 0.035D;
    public static final double BRAKING = 0.060D;
    public static final double TRACK_GAUGE = 2.1D; // Distance between tracks (meters)
    public static final double TRACK_LENGTH = 3.2D; // Contact length (meters)
    private static final float YAW_RESPONSE_SCALE = 0.55F;

    private float leftTrackSpeed = 0.0F;
    private float rightTrackSpeed = 0.0F;
    private float vehiclePitch = 0.0F;
    private float vehicleRoll = 0.0F;

    public record TrackState(
            float leftSpeed,
            float rightSpeed,
            Vec3 forwardDelta,
            float yawDeltaDegrees,
            float pitch,
            float roll
    ) {}

    /** Stops differential yaw immediately while preserving straight-line momentum. */
    public void lockDifferentialMotion() {
        float average = (leftTrackSpeed + rightTrackSpeed) * 0.5F;
        leftTrackSpeed = average;
        rightTrackSpeed = average;
    }

    /** Stops both tracks when their motion would drag embedded teeth sideways. */
    public void stopMotion() {
        leftTrackSpeed = 0.0F;
        rightTrackSpeed = 0.0F;
    }

    public TrackState tick(
            ServerLevel level,
            Vec3 currentPos,
            float currentYaw,
            float throttleInput,
            float steerInput,
            boolean onGround
    ) {
        // Differential target calculations
        double targetLeft;
        double targetRight;

        if (Math.abs(throttleInput) < 0.01F && Math.abs(steerInput) > 0.01F) {
            // In-place pivot turn
            targetLeft = -steerInput * (MAX_SPEED * 0.9D);
            targetRight = steerInput * (MAX_SPEED * 0.9D);
        } else if (Math.abs(throttleInput) > 0.01F) {
            // Driving forward / reverse with steering curve
            targetLeft = (throttleInput - steerInput * 0.65D) * MAX_SPEED;
            targetRight = (throttleInput + steerInput * 0.65D) * MAX_SPEED;
        } else {
            targetLeft = 0.0D;
            targetRight = 0.0D;
        }

        targetLeft = Mth.clamp(targetLeft, -MAX_SPEED, MAX_SPEED);
        targetRight = Mth.clamp(targetRight, -MAX_SPEED, MAX_SPEED);

        // Smooth acceleration and braking per track
        leftTrackSpeed = approach(leftTrackSpeed, (float) targetLeft);
        rightTrackSpeed = approach(rightTrackSpeed, (float) targetRight);

        // Compute forward speed and yaw angular velocity
        double avgForwardSpeed = (leftTrackSpeed + rightTrackSpeed) * 0.5D;
        // The raw differential-track geometry turns the excavator far too aggressively
        // at Minecraft tick scale. Keep track speeds and straight-line travel unchanged,
        // but damp body yaw so pivot turns feel heavy and machine-scale appropriate.
        float yawDelta = (float) Math.toDegrees(
                (rightTrackSpeed - leftTrackSpeed) / TRACK_GAUGE
        ) * YAW_RESPONSE_SCALE;

        if (!onGround) {
            avgForwardSpeed *= 0.5D;
            yawDelta *= 0.5F;
        }

        // Forward vector in world coordinates
        double yawRad = Math.toRadians(currentYaw);
        Vec3 forwardDelta = new Vec3(
                -Math.sin(yawRad) * avgForwardSpeed,
                0.0D,
                Math.cos(yawRad) * avgForwardSpeed
        );

        // Terrain pitch and roll sampling
        if (onGround && level != null) {
            sampleTerrainOrientation(level, currentPos, currentYaw);
        } else {
            vehiclePitch = Mth.lerp(0.1F, vehiclePitch, 0.0F);
            vehicleRoll = Mth.lerp(0.1F, vehicleRoll, 0.0F);
        }

        return new TrackState(
                leftTrackSpeed,
                rightTrackSpeed,
                forwardDelta,
                yawDelta,
                vehiclePitch,
                vehicleRoll
        );
    }

    private float approach(float current, float target) {
        float diff = target - current;
        if (Math.abs(diff) < 0.001F) {
            return target;
        }
        float rate = (Math.abs(target) < 0.001F) ? (float) BRAKING : (float) ACCELERATION;
        if (diff > 0.0F) {
            return Math.min(current + rate, target);
        } else {
            return Math.max(current - rate, target);
        }
    }

    private void sampleTerrainOrientation(ServerLevel level, Vec3 basePos, float yaw) {
        double yawRad = Math.toRadians(yaw);
        Vec3 heading = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        Vec3 right = new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad));

        double halfGauge = TRACK_GAUGE * 0.5D;
        double halfLength = TRACK_LENGTH * 0.5D;

        Vec3 fl = basePos.add(right.scale(-halfGauge)).add(heading.scale(halfLength));
        Vec3 fr = basePos.add(right.scale(halfGauge)).add(heading.scale(halfLength));
        Vec3 rl = basePos.add(right.scale(-halfGauge)).add(heading.scale(-halfLength));
        Vec3 rr = basePos.add(right.scale(halfGauge)).add(heading.scale(-halfLength));

        double yFL = sampleSurfaceHeight(level, fl);
        double yFR = sampleSurfaceHeight(level, fr);
        double yRL = sampleSurfaceHeight(level, rl);
        double yRR = sampleSurfaceHeight(level, rr);

        double frontAvgY = (yFL + yFR) * 0.5D;
        double rearAvgY = (yRL + yRR) * 0.5D;
        double leftAvgY = (yFL + yRL) * 0.5D;
        double rightAvgY = (yFR + yRR) * 0.5D;

        float targetPitch = (float) Math.toDegrees(Math.atan2(rearAvgY - frontAvgY, TRACK_LENGTH));
        float targetRoll = (float) Math.toDegrees(Math.atan2(rightAvgY - leftAvgY, TRACK_GAUGE));

        targetPitch = Mth.clamp(targetPitch, -30.0F, 30.0F);
        targetRoll = Mth.clamp(targetRoll, -25.0F, 25.0F);

        // Smooth filter
        vehiclePitch = Mth.lerp(0.15F, vehiclePitch, targetPitch);
        vehicleRoll = Mth.lerp(0.15F, vehicleRoll, targetRoll);
    }

    public static double sampleSurfaceHeight(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point.x, point.y, point.z);
        double surfaceY = GroundworksExcavationAdapter.getSurfaceWorldY(
                level, pos, point.x, point.z);
        if (Double.isFinite(surfaceY)) {
            return surfaceY;
        }

        // Check if block at pos is solid
        if (level.getBlockState(pos).isSolid()) {
            return pos.getY() + 1.0D;
        }

        // Check if block below is solid
        BlockPos below = pos.below();
        double belowSurfaceY = GroundworksExcavationAdapter.getSurfaceWorldY(
                level, below, point.x, point.z);
        if (Double.isFinite(belowSurfaceY)) {
            return belowSurfaceY;
        }
        if (level.getBlockState(below).isSolid()) {
            return below.getY() + 1.0D;
        }
        return point.y;
    }

    public float leftTrackSpeed() {
        return leftTrackSpeed;
    }

    public float rightTrackSpeed() {
        return rightTrackSpeed;
    }

    public void setTrackSpeeds(float left, float right) {
        this.leftTrackSpeed = left;
        this.rightTrackSpeed = right;
    }
}
