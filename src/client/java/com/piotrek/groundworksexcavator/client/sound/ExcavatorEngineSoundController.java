package com.piotrek.groundworksexcavator.client.sound;

import com.piotrek.groundworksexcavator.GroundworksExcavatorMod;
import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
import com.piotrek.groundworksexcavator.vehicle.EngineSoundProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Starts and tracks one positional diesel loop for each operating excavator. */
public final class ExcavatorEngineSoundController {

    private static final Map<Integer, EngineLoop> ACTIVE = new HashMap<>();
    private static ClientLevel activeLevel;

    private ExcavatorEngineSoundController() {}

    public static void clientTick(Minecraft client) {
        if (activeLevel != client.level) {
            ACTIVE.values().forEach(EngineLoop::stopNow);
            ACTIVE.clear();
            activeLevel = client.level;
        }
        if (client.level == null) return;

        Iterator<EngineLoop> iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isStopped()) iterator.remove();
        }

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof GroundworksExcavatorEntity excavator)
                    || !excavator.isOperating()
                    || ACTIVE.containsKey(excavator.getId())) {
                continue;
            }
            EngineLoop sound = new EngineLoop(excavator);
            ACTIVE.put(excavator.getId(), sound);
            client.getSoundManager().play(sound);
        }
    }

    private static final class EngineLoop extends AbstractTickableSoundInstance {

        private final GroundworksExcavatorEntity excavator;

        private EngineLoop(GroundworksExcavatorEntity excavator) {
            super(GroundworksExcavatorMod.ENGINE_LOOP, SoundSource.NEUTRAL, RandomSource.create());
            this.excavator = excavator;
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
            EngineSoundProfile.Mix mix = EngineSoundProfile.forTrackSpeeds(
                    excavator.getTrackLeftSpeed(), excavator.getTrackRightSpeed());
            this.volume = mix.volume();
            this.pitch = mix.pitch();
        }

        private void stopNow() {
            stop();
        }
    }
}
