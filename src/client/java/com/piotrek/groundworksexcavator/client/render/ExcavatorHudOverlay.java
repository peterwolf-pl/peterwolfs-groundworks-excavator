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
        int width = 250;
        int height = 68;

        // Semi-transparent background
        extractor.fill(x - 4, y - 4, x + width, y + height, 0x88000000);
        extractor.outline(x - 4, y - 4, width + 4, height + 4, 0xFFFFAA00);

        // Header
        extractor.text(font, "§6§lPeterwolf's Groundworks Excavator", x, y, 0xFFFFFF, true);

        // Active Control Mode (Key X) & Status
        boolean drive = ExcavatorInputHandler.isDriveMode();
        String modeStr = drive ? "§a§lJAZDA (Drive)" : "§b§lRAMIĘ (Arm)";
        extractor.text(font, "Tryb [X]: " + modeStr, x, y + 11, 0xFFFFFF, true);

        String status = excavator.isDigging() ? "§a§lKOPANIE" :
                excavator.isDumping() ? "§e§lWYSYP" : "§7GOTOWA";
        extractor.text(font, "Status: " + status, x + 155, y + 11, 0xCCCCCC, true);

        // Material & Capacity
        String matName = excavator.getBucketMaterialId() > 0 ? excavator.getBucketMaterial().name().toUpperCase() : "PUSTO";
        int units = excavator.getStoredUnits();
        int cap = excavator.getBucketCapacity();
        double m3 = GroundworksExcavationAdapter.unitsToCubicMeters(units);
        extractor.text(font, String.format("Łyżka: §f%s §7(%d/%d = %.3f m³)", matName, units, cap, m3), x, y + 22, 0xCCCCCC, true);

        // Fill progress bar
        int barWidth = 150;
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
        extractor.text(font, String.format("Boom: §f%.0f°§7 | Stick: §f%.0f°§7 | Bucket: §f%.0f°§7 | Cab: §f%.0f°",
                excavator.getBoomAngle(), excavator.getStickAngle(), excavator.getBucketAngle(), excavator.getUpperYaw()),
                x, y + 43, 0xAAAAAA, true);

        // Dynamic Controls hint based on active mode
        String hint = drive
                ? "§8[X] Zmień tryb | [W/S] Przód/Tył | [A/D] Skręt | [R/F/T/G] Łyżka"
                : "§8[X] Zmień tryb | [W/S] Ramię Góra/Dół | [A/D] Obrót | [R/F/T/G] Łyżka";
        extractor.text(font, hint, x, y + 54, 0xAAAAAA, true);
    }
}
