package com.piotrek.groundworksexcavator.vehicle;

import net.minecraft.util.Mth;

/**
 * Driver sits facing the cab front. The cab yaw is added to the look each tick
 * so the rider turns with the cab. Only the head may leave that facing.
 */
public final class CabSeatFacing {

    /** Vanilla head limit relative to the body. */
    public static final float MAX_HEAD_OFFSET = 75.0F;

    private CabSeatFacing() {}

    public record Look(float bodyYaw, float headYaw, float trackedCabYaw) {}

    public static Look align(float cabYaw, float previousCabYaw, float lookYaw, boolean firstSeat) {
        if (firstSeat) {
            return new Look(cabYaw, cabYaw, cabYaw);
        }
        float turned = lookYaw + Mth.wrapDegrees(cabYaw - previousCabYaw);
        float offset = Mth.clamp(Mth.wrapDegrees(turned - cabYaw), -MAX_HEAD_OFFSET, MAX_HEAD_OFFSET);
        return new Look(cabYaw, cabYaw + offset, cabYaw);
    }

    /**
     * Move one visual step toward a synced yaw. A late packet must not be applied
     * in one refresh, or the rider's head jumps. A large gap is a teleport.
     */
    public static float stepToward(float current, float target, float maxStep, float snapDistance) {
        float delta = Mth.wrapDegrees(target - current);
        if (Math.abs(delta) > snapDistance) {
            return Mth.wrapDegrees(target);
        }
        return Mth.wrapDegrees(current + Mth.clamp(delta, -maxStep, maxStep));
    }
}
