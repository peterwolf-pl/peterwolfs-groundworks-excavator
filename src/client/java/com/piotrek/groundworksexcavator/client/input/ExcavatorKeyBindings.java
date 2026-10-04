package com.piotrek.groundworksexcavator.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.piotrek.groundworksexcavator.GroundworksExcavatorMod;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Keybindings for hydraulic excavator operation following standard ISO excavator two-hand ergonomics.
 *
 * <p>Layout:
 * <ul>
 *   <li><b>Prawa ręka (Arrow Keys)</b>: Prawy Joystick = Wysięgnik (Up/Down) & Łyżka (Left/Right)</li>
 *   <li><b>Lewa ręka (WASD)</b>: Lewy Joystick w trybie ramienia = Przedramię (W/S) & Obrót kabiny (A/D)</li>
 *   <li><b>Lewa ręka (WASD)</b>: Gąsienice w trybie jazdy = Przód/Tył (W/S) & Skręt (A/D)</li>
 *   <li><b>Klawisz X</b>: Przełącznik trybu (Jazda / Ramię)</li>
 * </ul>
 */
public final class ExcavatorKeyBindings {

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(GroundworksExcavatorMod.id("controls"));

    public static KeyMapping KEY_TOGGLE_MODE;
    public static KeyMapping KEY_BOOM_UP;
    public static KeyMapping KEY_BOOM_DOWN;
    public static KeyMapping KEY_BUCKET_CURL;
    public static KeyMapping KEY_BUCKET_DUMP;
    public static KeyMapping KEY_STICK_OUT;
    public static KeyMapping KEY_STICK_IN;

    private ExcavatorKeyBindings() {}

    public static void register() {
        // Mode Switch (Key X)
        KEY_TOGGLE_MODE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pw_groundworks_excavator.toggle_mode",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_X,
                CATEGORY
        ));

        // Right Hand Controls (Arrow keys = Boom & Bucket)
        KEY_BOOM_UP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pw_groundworks_excavator.boom_up",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_UP,
                CATEGORY
        ));

        KEY_BOOM_DOWN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pw_groundworks_excavator.boom_down",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_DOWN,
                CATEGORY
        ));

        KEY_BUCKET_CURL = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pw_groundworks_excavator.bucket_curl",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_LEFT,
                CATEGORY
        ));

        KEY_BUCKET_DUMP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pw_groundworks_excavator.bucket_dump",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_RIGHT,
                CATEGORY
        ));

        // Auxiliary Stick shortcuts (R / F)
        KEY_STICK_OUT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pw_groundworks_excavator.stick_out",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_R,
                CATEGORY
        ));

        KEY_STICK_IN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pw_groundworks_excavator.stick_in",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_F,
                CATEGORY
        ));
    }
}
