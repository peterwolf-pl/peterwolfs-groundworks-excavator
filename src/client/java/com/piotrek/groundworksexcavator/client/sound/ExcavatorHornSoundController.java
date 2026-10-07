package com.piotrek.groundworksexcavator.client.sound;

import com.piotrek.groundworksexcavator.GroundworksExcavatorMod;
import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
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

/** One disc-horn loop per excavator while the operator holds C. */
public final class ExcavatorHornSoundController {

    private static final Map<Integer, HornLoop> ACTIVE = new HashMap<>();
    private static ClientLevel activeLevel;

    private ExcavatorHornSoundController() {}

    public static void clientTick(Minecraft client) {
        if (activeLevel != client.level) {
            ACTIVE.values().forEach(HornLoop::stopNow);
            ACTIVE.clear();
            activeLevel = client.level;
        }
        if (client.level == null) {
            return;
        }

        Iterator<HornLoop> iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            HornLoop horn = iterator.next();
            if (horn.isStopped()) {
                iterator.remove();
            }
        }

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof GroundworksExcavatorEntity excavator)
                    || !excavator.isHornHeld()
                    || ACTIVE.containsKey(excavator.getId())) {
                continue;
            }
            HornLoop horn = new HornLoop(excavator);
            ACTIVE.put(excavator.getId(), horn);
            client.getSoundManager().play(horn);
        }
    }

    private static final class HornLoop extends AbstractTickableSoundInstance {

        private final GroundworksExcavatorEntity excavator;

        private HornLoop(GroundworksExcavatorEntity excavator) {
            super(GroundworksExcavatorMod.TRUCK_HORN_LOOP, SoundSource.NEUTRAL, RandomSource.create());
            this.excavator = excavator;
            this.looping = true;
            this.delay = 0;
            this.volume = 1.0F;
            this.pitch = 1.0F;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            follow();
        }

        @Override
        public void tick() {
            if (excavator.isRemoved() || !excavator.isHornHeld()) {
                stop();
                return;
            }
            follow();
        }

        private void follow() {
            this.x = excavator.getX();
            this.y = excavator.getY() + 1.2D;
            this.z = excavator.getZ();
        }

        private void stopNow() {
            stop();
        }
    }
}
