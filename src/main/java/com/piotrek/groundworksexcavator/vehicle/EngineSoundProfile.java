package com.piotrek.groundworksexcavator.vehicle;

/** Maps authoritative hydraulic/drive load to a stable diesel-engine sound mix. */
public final class EngineSoundProfile {

    private static final float IDLE_VOLUME = 0.50F;
    private static final float LOAD_VOLUME = 0.86F;
    private static final float IDLE_PITCH = 0.90F;
    private static final float LOAD_PITCH = 1.13F;

    private EngineSoundProfile() {}

    public record Mix(float volume, float pitch) {}

    public static Mix forMachineLoad(
            float machineLoad,
            float leftSpeed,
            float rightSpeed
    ) {
        float speed = Math.max(Math.abs(leftSpeed), Math.abs(rightSpeed));
        float driveLoad = Math.min(
                1.0F,
                speed / (float) TrackMovementController.MAX_SPEED
        );
        float hydraulicLoad = Math.clamp(machineLoad, 0.0F, 1.0F);
        float load = Math.max(hydraulicLoad, driveLoad * 0.78F);

        return new Mix(
                IDLE_VOLUME + (LOAD_VOLUME - IDLE_VOLUME) * load,
                IDLE_PITCH + (LOAD_PITCH - IDLE_PITCH) * load
        );
    }

    public static Mix forTrackSpeeds(float leftSpeed, float rightSpeed) {
        return forMachineLoad(0.0F, leftSpeed, rightSpeed);
    }
}
