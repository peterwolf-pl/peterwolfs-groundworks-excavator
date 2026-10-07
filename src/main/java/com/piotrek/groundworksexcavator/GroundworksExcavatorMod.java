package com.piotrek.groundworksexcavator;

import com.piotrek.groundworksexcavator.command.ExcavatorCommand;
import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
import com.piotrek.groundworksexcavator.item.ExcavatorItem;
import com.piotrek.groundworksexcavator.network.ExcavatorInputPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroundworksExcavatorMod implements ModInitializer {

    public static final String MOD_ID = "pw_groundworks_excavator";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    // ── Entity Registration ──────────────────────────────────────────
    public static final ResourceKey<EntityType<?>> EXCAVATOR_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, id("excavator"));

    public static final EntityType<GroundworksExcavatorEntity> EXCAVATOR = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            EXCAVATOR_KEY,
            EntityType.Builder.of(GroundworksExcavatorEntity::new, MobCategory.MISC)
                    .sized(2.8F, 2.2F)
                    .clientTrackingRange(10)
                    .build(EXCAVATOR_KEY)
    );

    // ── Sound Registration ──────────────────────────────────────────
    public static final SoundEvent ENGINE_LOOP = Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            id("engine_loop"),
            SoundEvent.createFixedRangeEvent(id("engine_loop"), 14.0F)
    );

    /** Heavier exhaust layer faded in by {@code EngineSoundProfile}. No subtitle, so it does not double the idle caption. */
    public static final SoundEvent ENGINE_LOAD = Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            id("engine_load"),
            SoundEvent.createFixedRangeEvent(id("engine_load"), 14.0F)
    );

    public static final SoundEvent TRUCK_HORN_SHORT = Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            id("truck_horn_short"),
            SoundEvent.createFixedRangeEvent(id("truck_horn_short"), 64.0F)
    );

    public static final SoundEvent TRUCK_HORN_LONG = Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            id("truck_horn_long"),
            SoundEvent.createFixedRangeEvent(id("truck_horn_long"), 72.0F)
    );

    /** Seamless disc-horn loop while the operator holds C. */
    public static final SoundEvent TRUCK_HORN_LOOP = Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            id("truck_horn_loop"),
            SoundEvent.createFixedRangeEvent(id("truck_horn_loop"), 64.0F)
    );

    // ── Item Registration ────────────────────────────────────────────
    public static final ResourceKey<Item> EXCAVATOR_ITEM_KEY =
            ResourceKey.create(Registries.ITEM, id("excavator"));

    public static final ExcavatorItem EXCAVATOR_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            EXCAVATOR_ITEM_KEY,
            new ExcavatorItem(new Item.Properties().setId(EXCAVATOR_ITEM_KEY).stacksTo(1))
    );

    public static final ResourceKey<CreativeModeTab> TOOLS_AND_UTILITIES_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.withDefaultNamespace("tools_and_utilities")
    );

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Peterwolf's Groundworks Excavator for MC 26.3...");

        // 1. Networking registration
        PayloadTypeRegistry.serverboundPlay().register(
                ExcavatorInputPayload.TYPE, ExcavatorInputPayload.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(
                ExcavatorInputPayload.TYPE, (payload, context) -> {
                    context.server().execute(() -> {
                        ServerPlayer player = context.player();
                        if (player.getVehicle() instanceof GroundworksExcavatorEntity excavator
                                && excavator.isDriver(player)) {
                            excavator.setControlMode(payload.mode());
                            excavator.setBucketType(payload.bucketType());
                            excavator.setControlInputs(
                                    payload.throttle(),
                                    payload.steer(),
                                    payload.upperYawInput(),
                                    payload.boomInput(),
                                    payload.stickInput(),
                                    payload.bucketInput()
                            );
                            excavator.setHammerInput(payload.hammerActive());
                            excavator.setHornInput(payload.hornActive());
                        }
                    });
                }
        );

        // 2. Command registration
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> ExcavatorCommand.register(dispatcher)
        );

        // 3. Creative Tab placement
        CreativeModeTabEvents.modifyOutputEvent(TOOLS_AND_UTILITIES_TAB).register(output -> {
            output.accept(EXCAVATOR_ITEM);
        });

        LOGGER.info("Peterwolf's Groundworks Excavator initialized successfully.");
    }
}
