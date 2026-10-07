package com.piotrek.groundworksexcavator;

import com.piotrek.groundworksexcavator.vehicle.EngineSoundProfile;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineSoundProfileTest {

    @Test
    void trackLoadRaisesPitchAndVolume() {
        EngineSoundProfile.Mix idle = EngineSoundProfile.forTrackSpeeds(0.0F, 0.0F);
        EngineSoundProfile.Mix loaded = EngineSoundProfile.forTrackSpeeds(0.12F, -0.12F);

        assertTrue(loaded.pitch() > idle.pitch());
        assertTrue(loaded.volume() > idle.volume());
        assertTrue(loaded.loadVolume() > idle.loadVolume());
    }

    @Test
    void hydraulicLoadFadesInTheExhaustLayer() {
        EngineSoundProfile.Mix idle = EngineSoundProfile.forMachineLoad(0.0F, 0.0F, 0.0F);
        EngineSoundProfile.Mix stalled = EngineSoundProfile.forMachineLoad(1.0F, 0.0F, 0.0F);

        assertTrue(idle.loadVolume() < 0.01F);
        assertTrue(stalled.loadVolume() > 0.7F);
        assertTrue(stalled.loadPitch() < idle.loadPitch());
    }

    @Test
    void packagedLoopsAreVorbis() throws Exception {
        for (String name : new String[] {"engine_loop.ogg", "engine_load.ogg"}) {
            String path = "/assets/pw_groundworks_excavator/sounds/" + name;
            try (InputStream input = getClass().getResourceAsStream(path)) {
                assertNotNull(input, name);
                byte[] head = input.readNBytes(80);
                assertTrue(head.length > 16, name);
                assertArrayEquals(new byte[] {'O', 'g', 'g', 'S'}, new byte[] {head[0], head[1], head[2], head[3]});
                assertTrue(new String(head, java.nio.charset.StandardCharsets.ISO_8859_1).contains("vorbis"), name);
            }
        }
    }
}
