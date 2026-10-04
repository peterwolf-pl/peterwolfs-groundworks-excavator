package com.piotrek.groundworksexcavator.client.render;

import com.piotrek.groundworksexcavator.GroundworksExcavatorMod;
import com.piotrek.groundworksexcavator.client.input.ExcavatorInputHandler;
import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
import com.piotrek.groundworksexcavator.integration.groundworks.GroundworksExcavationAdapter;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * In-cab telemetry and instrument HUD displayed while operating the excavator.
 *
 * <p>Reflects two-handed ISO controls:
 * Left hand (WASD) and Right hand (Arrows).
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
        int x = 10;
        int y = 10;
        int width = 270;
        int height = 72;

        // Semi-transparent background
        extractor.fill(x - 4, y - 4, x + width, y + height, 0x88000000);
        extractor.outline(x - 4, y - 4, width + 4, height + 4, 0xFFFFAA00);

        // Header
        extractor.text(font, "§6§lPeterwolf's Groundworks Excavator", x, y, 0xFFFFFF, true);

        // Active Control Mode (Key X) & Status
        boolean drive = ExcavatorInputHandler.isDriveMode();
        String modeStr = drive ? "§a§lJAZDA (Drive)" : "§b§lRAMIĘ (Excavator)";
        extractor.text(font, "Tryb [X]: " + modeStr, x, y + 11, 0xFFFFFF, true);

        String status = excavator.isDigging() ? "§a§lKOPANIE" :
                excavator.isDumping() ? "§e§lWYSYP" : "§7GOTOWA";
        extractor.text(font, "Status: " + status, x + 165, y + 11, 0xCCCCCC, true);

        // Material & Capacity + Bucket Type Indicator
        String matName = excavator.getBucketMaterialId() > 0 ? excavator.getBucketMaterial().name().toUpperCase() : "PUSTO";
        int units = excavator.getStoredUnits();
        int cap = excavator.getBucketCapacity();
        double m3 = GroundworksExcavationAdapter.unitsToCubicMeters(units);
        boolean isLarge = excavator.getBucketType() == 1;
        String typeLabel = isLarge ? "§e[DUŻA 512u]" : "§b[STD 256u]";
        extractor.text(font, String.format("Łyżka %s: §f%s §7(%d/%d = %.3f m³)", typeLabel, matName, units, cap, m3), x, y + 22, 0xCCCCCC, true);

        // Fill progress bar
        int barWidth = 160;
        int barHeight = 4;
        int barY = y + 33;
        float ratio = (float) units / (float) Math.max(1, cap);
        extractor.fill(x, barY, x + barWidth, barY + barHeight, 0xFF333333);
        int fillWidth = Math.round(barWidth * ratio);
        if (fillWidth > 0) {
            extractor.fill(x, barY, x + fillWidth, barY + barHeight, 0xFF00AAFF);
        }
        extractor.text(font, String.format("%.0f%%", ratio * 100.0F), x + barWidth + 6, barY - 2, 0xAAAAAA, true);

        // Arm Angles
        extractor.text(font, String.format("Boom: §f%.0f°§7 | Stick: §f%.0f°§7 | Łyżka: §f%.0f°§7 | Kabina: §f%.0f°",
                excavator.getBoomAngle(), excavator.getStickAngle(), excavator.getBucketAngle(), excavator.getUpperYaw()),
                x, y + 43, 0xAAAAAA, true);

        // Two-handed Controls Hint
        String handHint = drive
                ? "§fLewa [WASD]: §aGąsienice §7| §fPrawa [Strzałki]: §eRamię & Łyżka"
                : "§fLewa [WASD]: §bObrót & Przedramię §7| §fPrawa [Strzałki]: §eWysięgnik & Łyżka";
        extractor.text(font, handHint, x, y + 54, 0xDDDDDD, true);

        String keyHint = drive
                ? "§8[W/S] Przód/Tył | [A/D] Skręt | [Z] Zmień łyżkę | [↑/↓] Boom | [←/→] Łyżka"
                : "§8[W/S] Przedramię | [A/D] Obrót | [Z] Zmień łyżkę | [↑/↓] Boom | [←/→] Łyżka";
        extractor.text(font, keyHint, x, y + 63, 0x888888, true);
    }
}
