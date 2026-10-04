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
 * Compact in-cab telemetry and instrument HUD displayed while operating the excavator.
 *
 * <p>Features:
 * <ul>
 *   <li>Compact frame sized exactly to the telemetry bars</li>
 *   <li>Primary bar: Bucket capacity fill progress (Cyan)</li>
 *   <li>Secondary bar: Machine hydraulic load / resistance (Green -> Orange -> Red overload)</li>
 *   <li>Mode, angles, and control hints</li>
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
        extractor.text(font, "§6§lKoparka §7[X]: " + modeStr, x, y, 0xFFFFFF, true);
        extractor.text(font, status, x + width - font.width(status) - 2, y, 0xFFFFFF, true);

        // ── 1. BUCKET FILL BAR ──
        String matName = excavator.getBucketMaterialId() > 0 ? excavator.getBucketMaterial().name().toUpperCase() : "PUSTO";
        int units = excavator.getStoredUnits();
        int cap = excavator.getBucketCapacity();
        float fillRatio = (float) units / (float) Math.max(1, cap);
        boolean isLarge = excavator.getBucketType() == 1;
        String typeLabel = isLarge ? "§e512u" : "§b256u";

        int bar1Y = y + 12;
        extractor.text(font, String.format("Łyżka %s: §f%s", typeLabel, matName), x, bar1Y, 0xCCCCCC, true);
        int bar1BoxY = bar1Y + 9;
        extractor.fill(x, bar1BoxY, x + barWidth, bar1BoxY + 4, 0xFF2A2A2A);
        int fillWidth = Math.round(barWidth * fillRatio);
        if (fillWidth > 0) {
            extractor.fill(x, bar1BoxY, x + fillWidth, bar1BoxY + 4, 0xFF00AAFF);
        }
        extractor.text(font, String.format("%.0f%%", fillRatio * 100.0F), x + barWidth + 4, bar1BoxY - 2, 0xAAAAAA, true);

        // ── 2. HYDRAULIC LOAD & OVERLOAD BAR ──
        float load = excavator.getMachineLoad();
        int bar2Y = bar1BoxY + 7;
        String loadLabel = load > 0.85F ? "§c§lOPÓR / PRZECIĄŻENIE" :
                load > 0.50F ? "§eObciążenie" : "§7Obciążenie";
        extractor.text(font, loadLabel, x, bar2Y, 0xCCCCCC, true);

        int bar2BoxY = bar2Y + 9;
        extractor.fill(x, bar2BoxY, x + barWidth, bar2BoxY + 4, 0xFF2A2A2A);
        int loadWidth = Math.round(barWidth * Math.min(1.0F, load));
        if (loadWidth > 0) {
            int loadColor = load > 0.80F ? 0xFFFF2222 : (load > 0.45F ? 0xFFFFAA00 : 0xFF22DD55);
            extractor.fill(x, bar2BoxY, x + loadWidth, bar2BoxY + 4, loadColor);
        }
        extractor.text(font, String.format("%.0f%%", load * 100.0F), x + barWidth + 4, bar2BoxY - 2,
                load > 0.80F ? 0xFFFF4444 : 0xAAAAAA, true);

        // Compact control hints footer
        String hint = drive ? "§8[W/S] Gąsienice | [Z] Łyżka" : "§8[W/S] Ramię | [A/D] Obrót | [Z] Łyżka";
        extractor.text(font, hint, x, y + height - 9, 0x888888, true);
    }
}
