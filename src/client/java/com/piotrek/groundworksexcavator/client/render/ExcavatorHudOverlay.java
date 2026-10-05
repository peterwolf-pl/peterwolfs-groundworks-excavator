package com.piotrek.groundworksexcavator.client.render;

import com.piotrek.groundworksexcavator.GroundworksExcavatorMod;
import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import com.piotrek.groundworksexcavator.automation.AutoTrenchController;
import com.piotrek.groundworksexcavator.client.input.ExcavatorInputHandler;
import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * In-cab telemetry and instrument HUD displayed while operating the excavator.
 *
 * <p>Features:
 * <ul>
 *   <li>Compact primary HUD: Bucket fill level, hydraulic load/overload bar, mode and control hints</li>
 *   <li>Detailed Debug Angle HUD: Activated via quick double-tap 'H' with full readouts
 *       for boom, stick, bucket, turntable slewing, chassis pitch/roll, and cutting teeth kinematics.</li>
 * </ul>
 */
public class ExcavatorHudOverlay implements HudElement {

    public static final Identifier ID = GroundworksExcavatorMod.id("hud_overlay");

    public static void register() {
        HudElementRegistry.addLast(ID, new ExcavatorHudOverlay());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        if (!(client.player.getVehicle() instanceof GroundworksExcavatorEntity excavator)) {
            return;
        }

        Font font = client.font;
        int x = 8;
        int y = 8;
        int barWidth = 140;
        int width = barWidth + 66; // Compact width: 206px
        int height = 64;           // Compact height: 64px

        // Semi-transparent compact background
        extractor.fill(x - 3, y - 3, x + width, y + height, 0x88000000);
        extractor.outline(x - 3, y - 3, width + 3, height + 3, 0xFFFFAA00);

        // Header: Mode [X] & Status
        boolean drive = ExcavatorInputHandler.isDriveMode();
        String modeStr = drive ? "§aJAZDA" : "§bRAMIĘ";
        String status = excavator.isDigging() ? "§aKOPANIE" :
                excavator.isDumping() ? "§eWYSYP" : "§7GOTOWA";
        extractor.text(font, "§6§lKoparka §7[X]: " + modeStr, x, y, 0xFFFFFFFF, true);
        extractor.text(font, status, x + width - font.width(status) - 2, y, 0xFFFFFFFF, true);

        // ── 1. BUCKET FILL BAR ──
        String matName = excavator.getBucketMaterialId() > 0 ? excavator.getBucketMaterial().name().toUpperCase() : "PUSTO";
        int units = excavator.getStoredUnits();
        int cap = excavator.getBucketCapacity();
        float fillRatio = (float) units / (float) Math.max(1, cap);
        boolean isLarge = excavator.getBucketType() == 1;
        String typeLabel = isLarge ? "§e512u" : "§b256u";

        int bar1Y = y + 12;
        extractor.text(font, String.format("Łyżka %s: §f%s", typeLabel, matName), x, bar1Y, 0xFFCCCCCC, true);
        int bar1BoxY = bar1Y + 9;
        extractor.fill(x, bar1BoxY, x + barWidth, bar1BoxY + 4, 0xFF2A2A2A);
        int fillWidth = Math.round(barWidth * fillRatio);
        if (fillWidth > 0) {
            extractor.fill(x, bar1BoxY, x + fillWidth, bar1BoxY + 4, 0xFF00AAFF);
        }
        extractor.text(font, String.format("%.0f%%", fillRatio * 100.0F), x + barWidth + 4, bar1BoxY - 2, 0xFFAAAAAA, true);

        // ── 2. HYDRAULIC LOAD & OVERLOAD BAR ──
        float load = excavator.getMachineLoad();
        int bar2Y = bar1BoxY + 7;
        String loadLabel = load > 0.85F ? "§c§lOPÓR / PRZECIĄŻENIE" :
                load > 0.50F ? "§eObciążenie" : "§7Obciążenie";
        extractor.text(font, loadLabel, x, bar2Y, 0xFFCCCCCC, true);

        int bar2BoxY = bar2Y + 9;
        extractor.fill(x, bar2BoxY, x + barWidth, bar2BoxY + 4, 0xFF2A2A2A);
        int loadWidth = Math.round(barWidth * Math.min(1.0F, load));
        if (loadWidth > 0) {
            int loadColor = load > 0.80F ? 0xFFFF2222 : (load > 0.45F ? 0xFFFFAA00 : 0xFF22DD55);
            extractor.fill(x, bar2BoxY, x + loadWidth, bar2BoxY + 4, loadColor);
        }
        extractor.text(font, String.format("%.0f%%", load * 100.0F), x + barWidth + 4, bar2BoxY - 2,
                load > 0.80F ? 0xFFFF4444 : 0xFFAAAAAA, true);

        // Compact control hints footer (with 2x H debug toggle hint)
        String hint = drive
                ? "§8[W/S] Gąsienice | [Z] Łyżka | [2x H] Kąty"
                : "§8[W/S] Ramię | [A/D] Obrót | [2x H] Kąty";
        extractor.text(font, hint, x, y + height - 9, 0xFF888888, true);

        // ── 3. DETAILED DEBUG ANGLE & ROTATION HUD (TOGGLED VIA 2x H) ──
        if (ExcavatorInputHandler.isDebugHudVisible()) {
            renderDebugAngleHud(extractor, font, excavator, x, y + height + 6);
        }
    }

    private static void renderDebugAngleHud(
            GuiGraphicsExtractor extractor,
            Font font,
            GroundworksExcavatorEntity excavator,
            int x,
            int debugY
    ) {
        int debugWidth = 236;
        boolean autoActive = excavator.isAutoTrenchActive();
        int debugHeight = autoActive ? 138 : 124;

        // Background panel with cyan outline
        extractor.fill(x - 3, debugY - 3, x + debugWidth, debugY + debugHeight, 0x90000000);
        extractor.outline(x - 3, debugY - 3, debugWidth + 3, debugHeight + 3, 0xFF00AAFF);

        // Header
        extractor.text(font, "§b§lTELEMETRIA KĄTÓW & OBROTU", x, debugY, 0xFFFFFFFF, true);
        String closeHint = "§8[2x H]";
        extractor.text(font, closeHint, x + debugWidth - font.width(closeHint) - 2, debugY, 0xFF888888, true);

        // Horizontal divider
        extractor.fill(x, debugY + 11, x + debugWidth - 3, debugY + 12, 0x44FFFFFF);

        // ── BOOM (Wysięgnik główny) ──
        float boom = excavator.getBoomAngle();
        float boomFrac = fraction(boom, ArmKinematics.BOOM_MIN, ArmKinematics.BOOM_MAX);
        int curY = debugY + 15;
        extractor.text(font, String.format("§6Wysięgnik: §f%+.1f° §7[%+.0f°..%+.0f°]",
                boom, ArmKinematics.BOOM_MIN, ArmKinematics.BOOM_MAX), x, curY, 0xFFFFFFFF, true);
        drawMiniBar(extractor, x + 160, curY + 2, 65, 4, boomFrac, 0xFF00DDCC);

        // ── STICK (Przedramię) ──
        float stick = excavator.getStickAngle();
        float stickFrac = fraction(stick, ArmKinematics.STICK_MIN, ArmKinematics.STICK_MAX);
        curY = debugY + 27;
        extractor.text(font, String.format("§6Przedramię: §f%+.1f° §7[%+.0f°..%+.0f°]",
                stick, ArmKinematics.STICK_MIN, ArmKinematics.STICK_MAX), x, curY, 0xFFFFFFFF, true);
        drawMiniBar(extractor, x + 160, curY + 2, 65, 4, stickFrac, 0xFF44DD66);

        // ── BUCKET (Łyżka) ──
        float bucket = excavator.getBucketAngle();
        float bucketFrac = fraction(bucket, ArmKinematics.BUCKET_MIN, ArmKinematics.BUCKET_MAX);
        BucketPose pose = getOrCreatePose(excavator);
        float dumpTilt = pose.dumpTiltDegrees();

        curY = debugY + 39;
        extractor.text(font, String.format("§6Łyżka: §f%+.1f° §7[%+.0f°..%+.0f°]",
                bucket, ArmKinematics.BUCKET_MIN, ArmKinematics.BUCKET_MAX), x, curY, 0xFFFFFFFF, true);
        int bucketBarColor = dumpTilt >= ArmKinematics.DUMP_THRESHOLD_DEG ? 0xFFFF8800 : 0xFFFFAA00;
        drawMiniBar(extractor, x + 160, curY + 2, 65, 4, bucketFrac, bucketBarColor);

        curY = debugY + 49;
        String dumpStatus = dumpTilt >= ArmKinematics.DUMP_THRESHOLD_DEG ? "§eWysypuje" : "§7Zamknięta";
        extractor.text(font, String.format("§7Nachylenie zrzutu: §f%+.1f° §7(próg: 30°) §7[%s§7]",
                dumpTilt, dumpStatus), x + 4, curY, 0xFFCCCCCC, true);

        // Horizontal divider
        extractor.fill(x, debugY + 61, x + debugWidth - 3, debugY + 62, 0x44FFFFFF);

        // ── TURNTABLE & CHASSIS ROTATION (Obrót) ──
        float cabYaw = excavator.getUpperYaw();
        float baseYaw = excavator.getYRot();
        curY = debugY + 64;
        extractor.text(font, String.format("§6Wieżyczka: §f%+.1f° §7(3°/t) | §6Podwozie: §f%+.1f°",
                cabYaw, baseYaw), x, curY, 0xFFFFFFFF, true);

        curY = debugY + 75;
        extractor.text(font, String.format("§7Pochylenie pojazdu Pitch/Roll: §f%+.1f° §7/ §f%+.1f°",
                excavator.getVehiclePitch(), excavator.getVehicleRoll()), x, curY, 0xFFAAAAAA, true);

        // Horizontal divider
        extractor.fill(x, debugY + 86, x + debugWidth - 3, debugY + 87, 0x44FFFFFF);

        // ── TEETH COORDINATES & REACH ──
        Vec3 edge = pose.cuttingEdge();
        double reach = Math.hypot(edge.x - excavator.getX(), edge.z - excavator.getZ());
        double depth = excavator.getY() - edge.y;

        curY = debugY + 89;
        extractor.text(font, String.format("§6Zęby: §fX:%.1f Y:%.1f Z:%.1f",
                edge.x, edge.y, edge.z), x, curY, 0xFFDDDDDD, true);

        curY = debugY + 100;
        extractor.text(font, String.format("§7Zasięg poziomy: §f%.2fm §7| Głębokość: §f%+.2fm",
                reach, -depth), x, curY, 0xFFAAAAAA, true);

        // ── AUTO-TRENCH STATUS (If active) ──
        if (autoActive) {
            extractor.fill(x, debugY + 111, x + debugWidth - 3, debugY + 112, 0x44FFFFFF);
            curY = debugY + 115;
            String passName = excavator.isAutoTrenchInLeftPass() ? "§bPOSZERZENIE L" : "§eŚRODEK";
            extractor.text(font, String.format("§eAuto-Trench: §a%s §7[%s§7] §7(max: §f%.1fm§7)",
                    excavator.getAutoTrenchPhase(), passName, excavator.getAutoTrenchMaxDepth()), x, curY, 0xFFFFFFFF, true);
            curY = debugY + 125;
            int maxForPass = excavator.isAutoTrenchInLeftPass()
                    ? excavator.getAutoTrenchLeftExpansionCycles()
                    : excavator.getAutoTrenchMaxCutsPerStation();
            extractor.text(font, String.format("§7Sekcje: §f%d §7| Cykl: §f%d/%d §7| Poszerzenie: §f%.1f bl.",
                    excavator.getAutoTrenchCompletedSections(),
                    excavator.getAutoTrenchCutsAtStation() + 1,
                    maxForPass,
                    excavator.getAutoTrenchLeftExpansionBlocks()), x, curY, 0xFFCCCCCC, true);
        }
    }

    private static BucketPose getOrCreatePose(GroundworksExcavatorEntity excavator) {
        BucketPose pose = excavator.getCurrentBucketPose();
        if (pose != null) return pose;
        return ArmKinematics.computeBucketPose(
                excavator.position(),
                excavator.getYRot(),
                excavator.getVehiclePitch(),
                excavator.getVehicleRoll(),
                excavator.getUpperYaw(),
                excavator.getBoomAngle(),
                excavator.getStickAngle(),
                excavator.getBucketAngle(),
                excavator.getBucketType()
        );
    }

    private static float fraction(float val, float min, float max) {
        if (max <= min) return 0.0F;
        return Math.clamp((val - min) / (max - min), 0.0F, 1.0F);
    }

    private static void drawMiniBar(
            GuiGraphicsExtractor extractor,
            int barX,
            int barY,
            int barW,
            int barH,
            float ratio,
            int fillColor
    ) {
        extractor.fill(barX, barY, barX + barW, barY + barH, 0xFF2A2A2A);
        int fillW = Math.round(barW * Math.clamp(ratio, 0.0F, 1.0F));
        if (fillW > 0) {
            extractor.fill(barX, barY, barX + fillW, barY + barH, fillColor);
        }
    }
}
