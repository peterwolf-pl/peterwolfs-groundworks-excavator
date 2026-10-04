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
 * <p>Implements two-handed ISO excavator controls:
 * <ul>
 *   <li><b>Prawa ręka (Strzałki)</b>: Prawy Joystick = Wysięgnik (Up/Down) & Łyżka (Left/Right)</li>
 *   <li><b>Lewa ręka (WASD) - Tryb Ramienia</b>: Lewy Joystick = Przedramię (W/S) & Obrót wieżyczki (A/D)</li>
 *   <li><b>Lewa ręka (WASD) - Tryb Jazdy</b>: Sterowanie gąsienicami = Jazda (W/S) & Skręt (A/D)</li>
 *   <li><b>Klawisz X</b>: Przełącznik trybu (Jazda / Ramię)</li>
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

            // 2. Read Left Hand inputs (WASD from KeyMapping or PlayerInput)
            boolean keyForward = client.options.keyUp.isDown()
                    || (client.player.input != null && client.player.input.keyPresses.forward());
            boolean keyBackward = client.options.keyDown.isDown()
                    || (client.player.input != null && client.player.input.keyPresses.backward());
            boolean keyLeft = client.options.keyLeft.isDown()
                    || (client.player.input != null && client.player.input.keyPresses.left());
            boolean keyRight = client.options.keyRight.isDown()
                    || (client.player.input != null && client.player.input.keyPresses.right());

            // 3. Read Right Hand inputs (Arrow keys: Up/Down for Boom, Left/Right for Bucket)
            boolean arrowUp = ExcavatorKeyBindings.KEY_BOOM_UP.isDown();
            boolean arrowDown = ExcavatorKeyBindings.KEY_BOOM_DOWN.isDown();
            boolean arrowLeft = ExcavatorKeyBindings.KEY_BUCKET_CURL.isDown();
            boolean arrowRight = ExcavatorKeyBindings.KEY_BUCKET_DUMP.isDown();

            // 4. Secondary stick shortcuts (R / F)
            boolean stickOutKey = ExcavatorKeyBindings.KEY_STICK_OUT.isDown();
            boolean stickInKey = ExcavatorKeyBindings.KEY_STICK_IN.isDown();

            float throttle = 0.0F;
            float steer = 0.0F;
            float cabYaw = 0.0F;
            float boom = 0.0F;
            float stick = 0.0F;
            float bucket = 0.0F;

            int currentMode = isDriveMode ? GroundworksExcavatorEntity.MODE_DRIVE : GroundworksExcavatorEntity.MODE_EXCAVATOR;

            // ── PRAWA RĘKA (Right Hand): Prawy Joystick (Zawsze aktywny pod strzałkami) ──
            // Strzałka w górę / w dół: Główne ramię / wysięgnik (Boom)
            if (arrowUp) boom += 1.0F;
            if (arrowDown) boom -= 1.0F;

            // Strzałka w lewo / w prawo: Łyżka (Bucket)
            if (arrowLeft) bucket += 1.0F;  // Lewo = Zwiń łyżkę / nabieranie (Curl In)
            if (arrowRight) bucket -= 1.0F; // Prawo = Otwórz łyżkę / wysyp (Dump Out)

            // ── LEWA RĘKA (Left Hand): WASD zależnie od trybu pod klawiszem X ──
            if (isDriveMode) {
                // TRYB JAZDY: WASD steruje gąsienicami
                if (keyForward) throttle += 1.0F;
                if (keyBackward) throttle -= 1.0F;
                if (keyLeft) steer -= 1.0F;
                if (keyRight) steer += 1.0F;
            } else {
                // TRYB RAMIENIA: WASD steruje lewym joystickiem (Przedramię + Obrót kabiny)
                // A / D = Obrót wieżyczki / kabiny (Swing Left / Right)
                if (keyLeft) cabYaw -= 1.0F;
                if (keyRight) cabYaw += 1.0F;

                // W / S = Przedramię (Stick Out / In)
                if (keyForward) stick += 1.0F;
                if (keyBackward) stick -= 1.0F;
            }

            // Pomocnicze klawisze przedramienia (R / F)
            if (stickOutKey) stick += 1.0F;
            if (stickInKey) stick -= 1.0F;

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
