package com.piotrek.groundworksexcavator.vehicle;

/** Maps authoritative track speed to a stable diesel-engine sound mix. */
public final class EngineSoundProfile {

    private static final float IDLE_VOLUME = 0.55F;
    private static final float LOAD_VOLUME = 0.76F;
    private static final float IDLE_PITCH = 0.82F;
    private static final float LOAD_PITCH = 1.10F;

    private EngineSoundProfile() {}

    public record Mix(float volume, float pitch) {}

    public static Mix forTrackSpeeds(float leftSpeed, float rightSpeed) {
        float speed = Math.max(Math.abs(leftSpeed), Math.abs(rightSpeed));
        float load = Math.min(1.0F, speed / (float) TrackMovementController.MAX_SPEED);
        return new Mix(
                IDLE_VOLUME + (LOAD_VOLUME - IDLE_VOLUME) * load,
                IDLE_PITCH + (LOAD_PITCH - IDLE_PITCH) * load
        );
    }
}
