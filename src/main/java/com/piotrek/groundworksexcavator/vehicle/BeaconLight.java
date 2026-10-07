package com.piotrek.groundworksexcavator.vehicle;

/**
 * Rotating roof beacon. The lamp mesh spins. It does not light the ground;
 * the work lamps do that.
 */
public final class BeaconLight {

    /** About two turns a second. A beacon, not a slow sign. */
    public static final float SPIN_PER_TICK = 0.63F;

    private BeaconLight() {}

    public static float spin(int tick, float partialTick) {
        return (tick + partialTick) * SPIN_PER_TICK;
    }
}
