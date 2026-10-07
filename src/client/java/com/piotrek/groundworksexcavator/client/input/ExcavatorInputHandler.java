package com.piotrek.groundworksexcavator.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
import com.piotrek.groundworksexcavator.network.ExcavatorInputPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Gathers operator keyboard inputs each client tick and transmits control packets to the server.
 *
 * <p>Supports:
 * <ul>
 *   <li><b>Prawa ręka (Strzałki & T/G)</b>: Wysięgnik (Up/Down) & Łyżka (Left/Right lub T/G)</li>
 *   <li><b>Lewa ręka (WASD) - Tryb Ramienia</b>: Przedramię (W/S) & Obrót wieżyczki (A/D)</li>
 *   <li><b>Lewa ręka (WASD) - Tryb Jazdy</b>: Gąsienice = Przód/Tył (W/S) & Skręt (A/D)</li>
 *   <li><b>Klawisz X</b>: Przełącznik trybu (Jazda / Ramię)</li>
 * </ul>
 */
public final class ExcavatorInputHandler {

    private static boolean isDriveMode = true;
    private static int currentBucketType = 0; // 0 = Standard, 1 = Large, 2 = Pneumatic hammer
    private static boolean debugHudVisible = false;
    private static boolean hammerLatched = false;
    private static long lastCTapTime = 0L;
    private static boolean cKeyDownLastTick = false;
    private static long lastHTapTime = 0L;
    private static boolean hKeyDownLastTick = false;

    private static int lastMode;
    private static int lastBucketType;
    private static float lastThrottle;
    private static float lastSteer;
    private static float lastCabYaw;
    private static float lastBoom;
    private static float lastStick;
    private static float lastBucket;
    private static boolean lastHammerActive;
    private static boolean lastHornActive;
    private static int keepaliveTicks;

    private ExcavatorInputHandler() {}

    public static boolean isDriveMode() {
        return isDriveMode;
    }

    public static boolean isDebugHudVisible() {
        return debugHudVisible;
    }

    public static void setDebugHudVisible(boolean visible) {
        debugHudVisible = visible;
    }

    public static void clientTick(Minecraft client) {
        if (client.player == null) {
            return;
        }

        if (client.player.getVehicle() instanceof GroundworksExcavatorEntity excavator) {

            // 1. Check Mode Toggle (Key X)
            while (ExcavatorKeyBindings.KEY_TOGGLE_MODE != null && ExcavatorKeyBindings.KEY_TOGGLE_MODE.consumeClick()) {
                isDriveMode = !isDriveMode;
                client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, isDriveMode ? 1.2F : 0.9F);
                client.player.sendSystemMessage(
                        Component.literal("§6[Koparka] Tryb: " + (isDriveMode ? "§a§lJAZDA (Drive)" : "§b§lRAMIĘ (Excavator Arm)"))
                );
            }

            // 1b. Cycle attachment (Key Z): standard bucket -> large bucket -> hammer.
            while (ExcavatorKeyBindings.KEY_TOGGLE_BUCKET != null && ExcavatorKeyBindings.KEY_TOGGLE_BUCKET.consumeClick()) {
                currentBucketType = (currentBucketType + 1) % 3;
                if (currentBucketType != GroundworksExcavatorEntity.BUCKET_HAMMER) {
                    hammerLatched = false;
                }
                client.player.playSound(SoundEvents.ANVIL_USE, 0.7F,
                        currentBucketType == GroundworksExcavatorEntity.BUCKET_HAMMER ? 0.65F
                                : currentBucketType == GroundworksExcavatorEntity.BUCKET_LARGE ? 0.85F : 1.15F);
                String msg = switch (currentBucketType) {
                    case GroundworksExcavatorEntity.BUCKET_LARGE ->
                            "§6[Koparka] Osprzęt: §e§lDUŻA ŁYŻKA (512u / 1.0 m³)";
                    case GroundworksExcavatorEntity.BUCKET_HAMMER ->
                            "§6[Koparka] Osprzęt: §c§lMŁOT PNEUMATYCZNY §7[C / 2x C]";
                    default ->
                            "§6[Koparka] Osprzęt: §b§lŁYŻKA STANDARDOWA (256u / 0.5 m³)";
                };
                client.player.sendSystemMessage(Component.literal(msg));
            }

            boolean inGame = client.mouseHandler != null && client.mouseHandler.isMouseGrabbed();

            // 1c. Key C is context-sensitive:
            // - digging bucket installed: horn
            // - hydraulic hammer installed: existing hammer control
            boolean cRawDown = (ExcavatorKeyBindings.KEY_HAMMER != null
                    && ExcavatorKeyBindings.KEY_HAMMER.isDown())
                    || (inGame && InputConstants.isKeyDown(InputConstants.KEY_C));

            boolean hornActive = currentBucketType != GroundworksExcavatorEntity.BUCKET_HAMMER
                    && cRawDown;

            // Hammer activation. Hold C for momentary work. A quick double-tap
            // toggles continuous operation until the next double-tap or attachment change.
            boolean cDown = currentBucketType == GroundworksExcavatorEntity.BUCKET_HAMMER
                    && cRawDown;
            boolean cJustPressed = cDown && !cKeyDownLastTick;
            cKeyDownLastTick = cDown;

            if (cJustPressed) {
                long now = System.currentTimeMillis();
                if (now - lastCTapTime <= 400L) {
                    hammerLatched = !hammerLatched;
                    lastCTapTime = 0L;
                    client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.9F,
                            hammerLatched ? 0.75F : 1.25F);
                    client.player.sendSystemMessage(Component.literal(
                            hammerLatched
                                    ? "§6[Koparka] Młot: §a§lPRACA CIĄGŁA"
                                    : "§6[Koparka] Młot: §c§lPRACA CIĄGŁA WYŁĄCZONA"
                    ));
                } else {
                    lastCTapTime = now;
                }
            }
            boolean hammerActive = currentBucketType == GroundworksExcavatorEntity.BUCKET_HAMMER
                    && (cDown || hammerLatched);

            // 1d. Check Debug HUD Toggle (Quick Double-tap H)
            boolean hClick = ExcavatorKeyBindings.KEY_DEBUG_HUD != null && ExcavatorKeyBindings.KEY_DEBUG_HUD.consumeClick();
            boolean hDownDirect = inGame && InputConstants.isKeyDown(InputConstants.KEY_H);
            boolean hJustPressed = hClick || (hDownDirect && !hKeyDownLastTick);
            hKeyDownLastTick = hDownDirect;

            if (hJustPressed) {
                long now = System.currentTimeMillis();
                if (now - lastHTapTime <= 400L) {
                    debugHudVisible = !debugHudVisible;
                    lastHTapTime = 0L;
                    client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.9F, debugHudVisible ? 1.4F : 0.8F);
                    client.player.sendSystemMessage(Component.literal(
                            debugHudVisible
                                    ? "§6[Koparka] HUD debugowy kątów i obrotu: §a§lWŁĄCZONY"
                                    : "§6[Koparka] HUD debugowy kątów i obrotu: §c§lWYŁĄCZONY"
                    ));
                } else {
                    lastHTapTime = now;
                }
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

            // 3. Boom Controls: Up / Down Arrow (plus KeyMapping)
            boolean boomUp = (ExcavatorKeyBindings.KEY_BOOM_UP != null && ExcavatorKeyBindings.KEY_BOOM_UP.isDown())
                    || (inGame && InputConstants.isKeyDown(InputConstants.KEY_UP));
            boolean boomDown = (ExcavatorKeyBindings.KEY_BOOM_DOWN != null && ExcavatorKeyBindings.KEY_BOOM_DOWN.isDown())
                    || (inGame && InputConstants.isKeyDown(InputConstants.KEY_DOWN));

            // 4. Bucket Controls: Left/Right Arrow OR T/G OR KeyMapping (100% fail-safe)
            boolean bucketCurl = (ExcavatorKeyBindings.KEY_BUCKET_CURL != null && ExcavatorKeyBindings.KEY_BUCKET_CURL.isDown())
                    || (inGame && (InputConstants.isKeyDown(InputConstants.KEY_LEFT) || InputConstants.isKeyDown(InputConstants.KEY_T)));

            boolean bucketDump = (ExcavatorKeyBindings.KEY_BUCKET_DUMP != null && ExcavatorKeyBindings.KEY_BUCKET_DUMP.isDown())
                    || (inGame && (InputConstants.isKeyDown(InputConstants.KEY_RIGHT) || InputConstants.isKeyDown(InputConstants.KEY_G)));

            // 5. Stick Shortcuts (R / F)
            boolean stickOutKey = (ExcavatorKeyBindings.KEY_STICK_OUT != null && ExcavatorKeyBindings.KEY_STICK_OUT.isDown())
                    || (inGame && InputConstants.isKeyDown(InputConstants.KEY_R));
            boolean stickInKey = (ExcavatorKeyBindings.KEY_STICK_IN != null && ExcavatorKeyBindings.KEY_STICK_IN.isDown())
                    || (inGame && InputConstants.isKeyDown(InputConstants.KEY_F));

            float throttle = 0.0F;
            float steer = 0.0F;
            float cabYaw = 0.0F;
            float boom = 0.0F;
            float stick = 0.0F;
            float bucket = 0.0F;

            int currentMode = isDriveMode ? GroundworksExcavatorEntity.MODE_DRIVE : GroundworksExcavatorEntity.MODE_EXCAVATOR;

            // ── BUCKET (Łyżka) — always active on Left/Right Arrow and T/G in both modes ──
            // Left Arrow / T: Przyciągnięcie do mnie (Curl inward / nabranie z towarem)
            if (bucketCurl) bucket -= 1.0F;

            // Right Arrow / G: Odpuszczenie od gracza (Dump outward / w pełni odwrócona, wysyp towaru)
            if (bucketDump) bucket += 1.0F;

            // ── BOOM (Wysięgnik główny) — always active on Up/Down Arrow ──
            if (boomUp) boom += 1.0F;
            if (boomDown) boom -= 1.0F;

            // ── LEFT HAND (WASD) — context dependent based on Mode X ──
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
                    || currentBucketType != lastBucketType
                    || throttle != lastThrottle
                    || steer != lastSteer
                    || cabYaw != lastCabYaw
                    || boom != lastBoom
                    || stick != lastStick
                    || bucket != lastBucket
                    || hammerActive != lastHammerActive
                    || hornActive != lastHornActive;

            if (changed || --keepaliveTicks <= 0) {
                ClientPlayNetworking.send(new ExcavatorInputPayload(
                        currentMode, currentBucketType, throttle, steer, cabYaw, boom, stick, bucket,
                        hammerActive, hornActive
                ));

                lastMode = currentMode;
                lastBucketType = currentBucketType;
                lastThrottle = throttle;
                lastSteer = steer;
                lastCabYaw = cabYaw;
                lastBoom = boom;
                lastStick = stick;
                lastBucket = bucket;
                lastHammerActive = hammerActive;
                lastHornActive = hornActive;
                keepaliveTicks = 5;
            }
        } else {
            lastThrottle = 0.0F;
            lastSteer = 0.0F;
            lastCabYaw = 0.0F;
            lastBoom = 0.0F;
            lastStick = 0.0F;
            lastBucket = 0.0F;
            lastHammerActive = false;
            lastHornActive = false;
            hammerLatched = false;
            cKeyDownLastTick = false;
            lastCTapTime = 0L;
            keepaliveTicks = 0;
            lastHTapTime = 0L;
            hKeyDownLastTick = false;
        }
    }
}
