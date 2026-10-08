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
import net.minecraft.client.resources.language.I18n;
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
        String modeStr = drive
                ? I18n.get("hud.pw_groundworks_excavator.mode_drive_short")
                : I18n.get("hud.pw_groundworks_excavator.mode_arm_short");
        boolean hammer = excavator.isHammerAttachment();
        String status = excavator.isHammering() ? I18n.get("hud.pw_groundworks_excavator.status_hammer") :
                excavator.isDigging() ? I18n.get("hud.pw_groundworks_excavator.status_digging") :
                excavator.isDumping() ? I18n.get("hud.pw_groundworks_excavator.status_dumping") :
                I18n.get("hud.pw_groundworks_excavator.status_ready");
        extractor.text(font, I18n.get("hud.pw_groundworks_excavator.header", modeStr), x, y, 0xFFFFFFFF, true);
        extractor.text(font, status, x + width - font.width(status) - 2, y, 0xFFFFFFFF, true);

        // ── 1. BUCKET FILL BAR ──
        String matName = excavator.getBucketMaterialId() > 0 ? excavator.getBucketMaterial().name().toUpperCase() : I18n.get("hud.pw_groundworks_excavator.empty");
        int units = excavator.getStoredUnits();
        int cap = excavator.getBucketCapacity();
        float fillRatio = (float) units / (float) Math.max(1, cap);
        boolean isLarge = excavator.getBucketType() == 1;
        String typeLabel = isLarge ? "§e512u" : "§b256u";

        int bar1Y = y + 12;
        int bar1BoxY = bar1Y + 9;
        if (hammer) {
            extractor.text(font, I18n.get("hud.pw_groundworks_excavator.tool_hammer"), x, bar1Y, 0xFFCCCCCC, true);
            extractor.fill(x, bar1BoxY, x + barWidth, bar1BoxY + 4, 0xFF2A2A2A);
            if (excavator.isHammering()) {
                extractor.fill(x, bar1BoxY, x + barWidth, bar1BoxY + 4, 0xFFFFAA00);
            }
            extractor.text(font, "[C] / [2x C]", x + barWidth + 4, bar1BoxY - 2, 0xFFAAAAAA, true);
        } else {
            extractor.text(font, I18n.get("hud.pw_groundworks_excavator.tool_bucket", typeLabel, matName), x, bar1Y, 0xFFCCCCCC, true);
            extractor.fill(x, bar1BoxY, x + barWidth, bar1BoxY + 4, 0xFF2A2A2A);
            int fillWidth = Math.round(barWidth * fillRatio);
            if (fillWidth > 0) {
                extractor.fill(x, bar1BoxY, x + fillWidth, bar1BoxY + 4, 0xFF00AAFF);
            }
            extractor.text(font, String.format("%.0f%%", fillRatio * 100.0F), x + barWidth + 4, bar1BoxY - 2, 0xFFAAAAAA, true);
        }

        // ── 2. HYDRAULIC LOAD & OVERLOAD BAR ──
        float load = excavator.getMachineLoad();
        int bar2Y = bar1BoxY + 7;
        String loadLabel = load > 0.85F ? I18n.get("hud.pw_groundworks_excavator.load_overload") :
                load > 0.50F ? I18n.get("hud.pw_groundworks_excavator.load_heavy") :
                I18n.get("hud.pw_groundworks_excavator.load_normal");
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
        String hint = hammer
                ? I18n.get("hud.pw_groundworks_excavator.hint_hammer")
                : drive
                ? I18n.get("hud.pw_groundworks_excavator.hint_drive")
                : I18n.get("hud.pw_groundworks_excavator.hint_arm");
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
        extractor.text(font, I18n.get("hud.pw_groundworks_excavator.debug_title"), x, debugY, 0xFFFFFFFF, true);
        String closeHint = "§8[2x H]";
        extractor.text(font, closeHint, x + debugWidth - font.width(closeHint) - 2, debugY, 0xFF888888, true);

        // Horizontal divider
        extractor.fill(x, debugY + 11, x + debugWidth - 3, debugY + 12, 0x44FFFFFF);

        // ── BOOM (Wysięgnik główny) ──
        float boom = excavator.getBoomAngle();
        float boomFrac = fraction(boom, ArmKinematics.BOOM_MIN, ArmKinematics.BOOM_MAX);
        int curY = debugY + 15;
        extractor.text(font, I18n.get("hud.pw_groundworks_excavator.debug_boom",
                boom, ArmKinematics.BOOM_MIN, ArmKinematics.BOOM_MAX), x, curY, 0xFFFFFFFF, true);
        drawMiniBar(extractor, x + 160, curY + 2, 65, 4, boomFrac, 0xFF00DDCC);

        // ── STICK (Przedramię) ──
        float stick = excavator.getStickAngle();
        float stickFrac = fraction(stick, ArmKinematics.STICK_MIN, ArmKinematics.STICK_MAX);
        curY = debugY + 27;
        extractor.text(font, I18n.get("hud.pw_groundworks_excavator.debug_stick",
                stick, ArmKinematics.STICK_MIN, ArmKinematics.STICK_MAX), x, curY, 0xFFFFFFFF, true);
        drawMiniBar(extractor, x + 160, curY + 2, 65, 4, stickFrac, 0xFF44DD66);

        // ── WORK TOOL ANGLE ──
        boolean hammer = excavator.isHammerAttachment();
        float bucket = excavator.getBucketAngle();
        float toolMin = ArmKinematics.getMinBucketAngle(excavator.getBucketType());
        float toolMax = ArmKinematics.getMaxBucketAngle(excavator.getBucketType());
        float bucketFrac = fraction(bucket, toolMin, toolMax);
        BucketPose pose = getOrCreatePose(excavator);
        float dumpTilt = pose.dumpTiltDegrees();

        curY = debugY + 39;
        String toolName = hammer
                ? I18n.get("hud.pw_groundworks_excavator.tool_name_hammer")
                : I18n.get("hud.pw_groundworks_excavator.tool_name_bucket");
        extractor.text(font, I18n.get("hud.pw_groundworks_excavator.debug_tool",
                toolName, bucket, toolMin, toolMax), x, curY, 0xFFFFFFFF, true);
        int bucketBarColor = dumpTilt >= ArmKinematics.DUMP_THRESHOLD_DEG ? 0xFFFF8800 : 0xFFFFAA00;
        drawMiniBar(extractor, x + 160, curY + 2, 65, 4, bucketFrac, bucketBarColor);

        curY = debugY + 49;
        String dumpStatus = hammer
                ? (excavator.isHammering() ? I18n.get("hud.pw_groundworks_excavator.status_hammer_active") : I18n.get("hud.pw_groundworks_excavator.status_hammer_ready"))
                : (dumpTilt >= ArmKinematics.DUMP_THRESHOLD_DEG ? I18n.get("hud.pw_groundworks_excavator.status_bucket_dumping") : I18n.get("hud.pw_groundworks_excavator.status_bucket_closed"));
        extractor.text(font, hammer
                        ? I18n.get("hud.pw_groundworks_excavator.debug_hammer_tip",
                                Math.abs(ArmKinematics.HAMMER_TIP_STRIKE_Z_PX) / 16.0F, dumpStatus)
                        : I18n.get("hud.pw_groundworks_excavator.debug_bucket_tilt",
                                dumpTilt, dumpStatus),
                x + 4, curY, 0xFFCCCCCC, true);

        // Horizontal divider
        extractor.fill(x, debugY + 61, x + debugWidth - 3, debugY + 62, 0x44FFFFFF);

        // ── TURNTABLE & CHASSIS ROTATION (Obrót) ──
        float cabYaw = excavator.getUpperYaw();
        float baseYaw = excavator.getYRot();
        curY = debugY + 64;
        extractor.text(font, I18n.get("hud.pw_groundworks_excavator.debug_turntable",
                cabYaw, baseYaw), x, curY, 0xFFFFFFFF, true);

        curY = debugY + 75;
        extractor.text(font, I18n.get("hud.pw_groundworks_excavator.debug_pitch_roll",
                excavator.getVehiclePitch(), excavator.getVehicleRoll()), x, curY, 0xFFAAAAAA, true);

        // Horizontal divider
        extractor.fill(x, debugY + 86, x + debugWidth - 3, debugY + 87, 0x44FFFFFF);

        // ── TOOL TIP COORDINATES & REACH ──
        Vec3 edge = pose.cuttingEdge();
        double reach = Math.hypot(edge.x - excavator.getX(), edge.z - excavator.getZ());
        double depth = excavator.getY() - edge.y;

        curY = debugY + 89;
        String pointName = hammer
                ? I18n.get("hud.pw_groundworks_excavator.point_hammer_tip")
                : I18n.get("hud.pw_groundworks_excavator.point_bucket_teeth");
        extractor.text(font, I18n.get("hud.pw_groundworks_excavator.debug_coords",
                pointName, edge.x, edge.y, edge.z), x, curY, 0xFFDDDDDD, true);

        curY = debugY + 100;
        extractor.text(font, I18n.get("hud.pw_groundworks_excavator.debug_reach_depth",
                reach, -depth), x, curY, 0xFFAAAAAA, true);

        // ── AUTO-TRENCH STATUS (If active) ──
        if (autoActive) {
            extractor.fill(x, debugY + 111, x + debugWidth - 3, debugY + 112, 0x44FFFFFF);
            curY = debugY + 115;
            String passName = excavator.isAutoTrenchInLeftPass()
                    ? I18n.get("hud.pw_groundworks_excavator.pass_left")
                    : I18n.get("hud.pw_groundworks_excavator.pass_center");
            extractor.text(font, I18n.get("hud.pw_groundworks_excavator.autotrench_header",
                    excavator.getAutoTrenchPhase(), passName, excavator.getAutoTrenchMaxDepth()), x, curY, 0xFFFFFFFF, true);
            curY = debugY + 125;
            int maxForPass = excavator.isAutoTrenchInLeftPass()
                    ? excavator.getAutoTrenchLeftExpansionCycles()
                    : excavator.getAutoTrenchMaxCutsPerStation();
            extractor.text(font, I18n.get("hud.pw_groundworks_excavator.autotrench_stats",
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
