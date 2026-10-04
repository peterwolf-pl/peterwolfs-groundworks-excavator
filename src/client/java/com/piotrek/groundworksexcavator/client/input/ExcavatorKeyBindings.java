package com.piotrek.groundworksexcavator.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.piotrek.groundworksexcavator.GroundworksExcavatorMod;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Keybindings for hydraulic excavator operation following ISO excavator ergonomics.
 */
public final class ExcavatorKeyBindings {

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(GroundworksExcavatorMod.id("controls"));

    public static KeyMapping KEY_TOGGLE_MODE;
    public static KeyMapping KEY_TOGGLE_BUCKET;
    public static KeyMapping KEY_BOOM_UP;
    public static KeyMapping KEY_BOOM_DOWN;
    public static KeyMapping KEY_BUCKET_CURL;
    public static KeyMapping KEY_BUCKET_DUMP;
    public static KeyMapping KEY_CAB_LEFT;
    public static KeyMapping KEY_CAB_RIGHT;
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

        // Bucket Variant Switch (Key Z)
        KEY_TOGGLE_BUCKET = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pw_groundworks_excavator.toggle_bucket",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_Z,
                CATEGORY
        ));

        // Right Hand: Boom Up/Down (Arrow Up / Down)
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

        // Right Hand: Bucket Curl/Dump (Arrow Left / Right, or T / G)
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

        // Cab Rotation (A / D, or Arrow Left / Right)
        KEY_CAB_LEFT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pw_groundworks_excavator.cab_left",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_A,
                CATEGORY
        ));

        KEY_CAB_RIGHT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pw_groundworks_excavator.cab_right",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_D,
                CATEGORY
        ));

        // Dipper Stick Extension (W / S, or R / F)
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
