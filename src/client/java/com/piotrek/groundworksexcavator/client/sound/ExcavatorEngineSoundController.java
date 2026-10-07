package com.piotrek.groundworksexcavator.client.sound;

import com.piotrek.groundworksexcavator.GroundworksExcavatorMod;
import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
import com.piotrek.groundworksexcavator.vehicle.EngineSoundProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Crossfades an idle diesel loop and a heavier load loop for each operating excavator. */
public final class ExcavatorEngineSoundController {

    private static final Map<Integer, EnginePair> ACTIVE = new HashMap<>();
    private static ClientLevel activeLevel;

    private ExcavatorEngineSoundController() {}

    public static void clientTick(Minecraft client) {
        if (activeLevel != client.level) {
            ACTIVE.values().forEach(EnginePair::stopNow);
            ACTIVE.clear();
            activeLevel = client.level;
        }
        if (client.level == null) return;

        Iterator<EnginePair> iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            EnginePair pair = iterator.next();
            if (pair.stopped()) {
                pair.stopNow();
                iterator.remove();
            }
        }

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof GroundworksExcavatorEntity excavator)
                    || !excavator.isOperating()
                    || ACTIVE.containsKey(excavator.getId())) {
                continue;
            }
            EnginePair pair = new EnginePair(excavator);
            ACTIVE.put(excavator.getId(), pair);
            client.getSoundManager().play(pair.idle);
            client.getSoundManager().play(pair.load);
        }
    }

    private record EnginePair(EngineLoop idle, EngineLoop load) {
        private EnginePair(GroundworksExcavatorEntity excavator) {
            this(
                    new EngineLoop(excavator, GroundworksExcavatorMod.ENGINE_LOOP, false),
                    new EngineLoop(excavator, GroundworksExcavatorMod.ENGINE_LOAD, true)
            );
        }

        private boolean stopped() {
            return idle.isStopped() || load.isStopped();
        }

        private void stopNow() {
            idle.stopNow();
            load.stopNow();
        }
    }

    private static final class EngineLoop extends AbstractTickableSoundInstance {

        private final GroundworksExcavatorEntity excavator;
        private final boolean loadLayer;

        private EngineLoop(GroundworksExcavatorEntity excavator, SoundEvent sound, boolean loadLayer) {
            super(sound, SoundSource.NEUTRAL, RandomSource.create());
            this.excavator = excavator;
            this.loadLayer = loadLayer;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            updateSound();
        }

        @Override
        public void tick() {
            if (excavator.isRemoved() || !excavator.isOperating()) {
                stop();
                return;
            }
            updateSound();
        }

        private void updateSound() {
            this.x = excavator.getX();
            this.y = excavator.getY() + 1.0D;
            this.z = excavator.getZ();
            EngineSoundProfile.Mix mix = EngineSoundProfile.forMachineLoad(
                    excavator.getMachineLoad(),
                    excavator.getTrackLeftSpeed(),
                    excavator.getTrackRightSpeed()
            );
            this.volume = loadLayer ? mix.loadVolume() : mix.volume();
            this.pitch = loadLayer ? mix.loadPitch() : mix.pitch();
            // The driver sits on the source. Outside, the same gain dies within a few blocks.
            if (excavator.isAutoTrenchActive() && !excavator.hasPassenger(Minecraft.getInstance().player)) {
                this.volume = Math.max(this.volume, loadLayer ? 0.55F : 0.78F);
            }
        }

        private void stopNow() {
            stop();
        }
    }
}
