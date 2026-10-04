package com.piotrek.groundworksexcavator.client.input;

import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
import com.piotrek.groundworksexcavator.network.ExcavatorInputPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Gathers operator keyboard inputs each client tick and transmits control packets to the server.
 *
 * <p>Supports two distinct operator contexts toggled with key 'X':
 * <ul>
 *   <li><b>DRIVE MODE</b>: WASD drives and differential-steers the tracks.</li>
 *   <li><b>ARM MODE</b>: WASD (and arrows) articulate the rotating cab and heavy boom.</li>
 * </ul>
 */
public final class ExcavatorInputHandler {

    private static boolean isDriveMode = true;

    private static int lastMode;
    private static float lastThrottle;
    private static float lastSteer;
    private static float lastCabYaw;
    private static float lastBoom;
    private static float lastStick;
    private static float lastBucket;
    private static int keepaliveTicks;

    private ExcavatorInputHandler() {}

    public static boolean isDriveMode() {
        return isDriveMode;
    }

    public static void clientTick(Minecraft client) {
        if (client.player == null) {
            return;
        }

        if (client.player.getVehicle() instanceof GroundworksExcavatorEntity excavator
                && excavator.isDriver(client.player)) {

            // 1. Check Mode Toggle (Key X)
            while (ExcavatorKeyBindings.KEY_TOGGLE_MODE.consumeClick()) {
                isDriveMode = !isDriveMode;
                client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, isDriveMode ? 1.2F : 0.9F);
                client.player.sendSystemMessage(
                        Component.literal("§6[Koparka] Tryb: " + (isDriveMode ? "§a§lJAZDA (Drive)" : "§b§lRAMIĘ (Excavator Arm)"))
                );
            }

            // 2. Read base key inputs (supporting both KeyMapping and PlayerInput)
            boolean keyForward = client.options.keyUp.isDown()
                    || (client.player.input != null && client.player.input.keyPresses.forward());
            boolean keyBackward = client.options.keyDown.isDown()
                    || (client.player.input != null && client.player.input.keyPresses.backward());
            boolean keyLeft = client.options.keyLeft.isDown()
                    || (client.player.input != null && client.player.input.keyPresses.left());
            boolean keyRight = client.options.keyRight.isDown()
                    || (client.player.input != null && client.player.input.keyPresses.right());

            boolean arrowLeft = ExcavatorKeyBindings.KEY_CAB_LEFT.isDown();
            boolean arrowRight = ExcavatorKeyBindings.KEY_CAB_RIGHT.isDown();
            boolean arrowUp = ExcavatorKeyBindings.KEY_BOOM_UP.isDown();
            boolean arrowDown = ExcavatorKeyBindings.KEY_BOOM_DOWN.isDown();

            boolean stickOut = ExcavatorKeyBindings.KEY_STICK_OUT.isDown();
            boolean stickIn = ExcavatorKeyBindings.KEY_STICK_IN.isDown();
            boolean bucketCurl = ExcavatorKeyBindings.KEY_BUCKET_CURL.isDown();
            boolean bucketDump = ExcavatorKeyBindings.KEY_BUCKET_DUMP.isDown();

            float throttle = 0.0F;
            float steer = 0.0F;
            float cabYaw = 0.0F;
            float boom = 0.0F;
            float stick = 0.0F;
            float bucket = 0.0F;

            int currentMode = isDriveMode ? GroundworksExcavatorEntity.MODE_DRIVE : GroundworksExcavatorEntity.MODE_EXCAVATOR;

            if (isDriveMode) {
                // ── DRIVE MODE: WASD controls the crawler tracks ──
                if (keyForward) throttle += 1.0F;
                if (keyBackward) throttle -= 1.0F;
                if (keyLeft) steer -= 1.0F;
                if (keyRight) steer += 1.0F;

                // Arrows can still adjust arm during transport
                if (arrowLeft) cabYaw -= 1.0F;
                if (arrowRight) cabYaw += 1.0F;
                if (arrowUp) boom += 1.0F;
                if (arrowDown) boom -= 1.0F;
            } else {
                // ── ARM MODE: Tracks stationary, WASD & Arrows articulate arm ──
                // A / D or Left/Right Arrow rotates turntable cab
                if (keyLeft || arrowLeft) cabYaw -= 1.0F;
                if (keyRight || arrowRight) cabYaw += 1.0F;

                // W / S or Up/Down Arrow raises & lowers boom
                if (keyForward || arrowUp) boom += 1.0F;
                if (keyBackward || arrowDown) boom -= 1.0F;
            }

            // Stick and Bucket controls are available in both modes
            if (stickOut) stick += 1.0F;
            if (stickIn) stick -= 1.0F;
            if (bucketCurl) bucket += 1.0F; // Curl inward
            if (bucketDump) bucket -= 1.0F; // Dump outward

            boolean changed = currentMode != lastMode
                    || throttle != lastThrottle
                    || steer != lastSteer
                    || cabYaw != lastCabYaw
                    || boom != lastBoom
                    || stick != lastStick
                    || bucket != lastBucket;

            if (changed || --keepaliveTicks <= 0) {
                ClientPlayNetworking.send(new ExcavatorInputPayload(
                        currentMode, throttle, steer, cabYaw, boom, stick, bucket
                ));

                lastMode = currentMode;
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
