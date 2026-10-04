package com.piotrek.groundworksexcavator.client.input;

import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
import com.piotrek.groundworksexcavator.network.ExcavatorInputPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Gathers operator keyboard inputs each client tick and transmits control packets to the server.
 */
public final class ExcavatorInputHandler {

    private static float lastThrottle;
    private static float lastSteer;
    private static float lastCabYaw;
    private static float lastBoom;
    private static float lastStick;
    private static float lastBucket;
    private static int keepaliveTicks;

    private ExcavatorInputHandler() {}

    public static void clientTick(Minecraft client) {
        if (client.player == null) {
            return;
        }

        if (client.player.getVehicle() instanceof GroundworksExcavatorEntity excavator
                && excavator.isDriver(client.player)) {

            float throttle = 0.0F;
            if (client.options.keyUp.isDown()) throttle += 1.0F;
            if (client.options.keyDown.isDown()) throttle -= 1.0F;

            float steer = 0.0F;
            if (client.options.keyLeft.isDown()) steer -= 1.0F;
            if (client.options.keyRight.isDown()) steer += 1.0F;

            float cabYaw = 0.0F;
            if (ExcavatorKeyBindings.KEY_CAB_LEFT.isDown()) cabYaw -= 1.0F;
            if (ExcavatorKeyBindings.KEY_CAB_RIGHT.isDown()) cabYaw += 1.0F;

            float boom = 0.0F;
            if (ExcavatorKeyBindings.KEY_BOOM_UP.isDown()) boom += 1.0F;
            if (ExcavatorKeyBindings.KEY_BOOM_DOWN.isDown()) boom -= 1.0F;

            float stick = 0.0F;
            if (ExcavatorKeyBindings.KEY_STICK_OUT.isDown()) stick += 1.0F;
            if (ExcavatorKeyBindings.KEY_STICK_IN.isDown()) stick -= 1.0F;

            float bucket = 0.0F;
            if (ExcavatorKeyBindings.KEY_BUCKET_CURL.isDown()) bucket += 1.0F; // Curl inward / hold
            if (ExcavatorKeyBindings.KEY_BUCKET_DUMP.isDown()) bucket -= 1.0F; // Curl outward / dump

            boolean changed = throttle != lastThrottle
                    || steer != lastSteer
                    || cabYaw != lastCabYaw
                    || boom != lastBoom
                    || stick != lastStick
                    || bucket != lastBucket;

            if (changed || --keepaliveTicks <= 0) {
                ClientPlayNetworking.send(new ExcavatorInputPayload(
                        throttle, steer, cabYaw, boom, stick, bucket
                ));

                lastThrottle = throttle;
                lastSteer = steer;
                lastCabYaw = cabYaw;
                lastBoom = boom;
                lastStick = stick;
                lastBucket = bucket;
                keepaliveTicks = 5;
            }
        } else {
            lastThrottle = 0.0F;
            lastSteer = 0.0F;
            lastCabYaw = 0.0F;
            lastBoom = 0.0F;
            lastStick = 0.0F;
            lastBucket = 0.0F;
            keepaliveTicks = 0;
        }
    }
}
