package com.piotrek.groundworksexcavator.client;

import com.piotrek.groundworksexcavator.GroundworksExcavatorMod;
import com.piotrek.groundworksexcavator.client.input.ExcavatorInputHandler;
import com.piotrek.groundworksexcavator.client.input.ExcavatorKeyBindings;
import com.piotrek.groundworksexcavator.client.model.ExcavatorModel;
import com.piotrek.groundworksexcavator.client.model.HydraulicHammerModel;
import com.piotrek.groundworksexcavator.client.render.ExcavatorHudOverlay;
import com.piotrek.groundworksexcavator.client.render.ExcavatorRenderer;
import com.piotrek.groundworksexcavator.client.sound.ExcavatorEngineSoundController;
import com.piotrek.groundworksexcavator.client.sound.ExcavatorHornSoundController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;

public class GroundworksExcavatorClient implements ClientModInitializer {

    public static final ModelLayerLocation EXCAVATOR_LAYER =
            new ModelLayerLocation(GroundworksExcavatorMod.id("excavator"), "main");
    public static final ModelLayerLocation HYDRAULIC_HAMMER_LAYER =
            new ModelLayerLocation(GroundworksExcavatorMod.id("hydraulic_hammer"), "main");

    @Override
    public void onInitializeClient() {
        // Register entity model layer
        ModelLayerRegistry.registerModelLayer(EXCAVATOR_LAYER, ExcavatorModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(
                HYDRAULIC_HAMMER_LAYER, HydraulicHammerModel::createBodyLayer);

        // Register entity renderer
        EntityRendererRegistry.register(GroundworksExcavatorMod.EXCAVATOR, ExcavatorRenderer::new);

        // Register keybindings
        ExcavatorKeyBindings.register();

        // Register input and positional engine sound tick listeners
        ClientTickEvents.END_CLIENT_TICK.register(ExcavatorInputHandler::clientTick);
        ClientTickEvents.END_CLIENT_TICK.register(ExcavatorEngineSoundController::clientTick);
        ClientTickEvents.END_CLIENT_TICK.register(ExcavatorHornSoundController::clientTick);

        // Register in-cab HUD
        ExcavatorHudOverlay.register();
    }
}
