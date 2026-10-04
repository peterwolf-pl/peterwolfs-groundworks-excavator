package com.piotrek.groundworksexcavator.network;

import com.piotrek.groundworksexcavator.GroundworksExcavatorMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Compact client operator intent packet.
 *
 * <p>Transmits normalized control inputs (-1.0 .. +1.0) for tracks, cab rotation,
 * and hydraulic arm joints. All resulting motion and terrain modifications remain
 * strictly server-authoritative.
 */
public record ExcavatorInputPayload(
        float throttle,
        float steer,
        float upperYawInput,
        float boomInput,
        float stickInput,
        float bucketInput
) implements CustomPacketPayload {

    public static final Type<ExcavatorInputPayload> TYPE = new Type<>(GroundworksExcavatorMod.id("excavator_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExcavatorInputPayload> CODEC = new StreamCodec<>() {
        @Override
        public ExcavatorInputPayload decode(RegistryFriendlyByteBuf buffer) {
            return new ExcavatorInputPayload(
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ExcavatorInputPayload payload) {
            buffer.writeFloat(payload.throttle);
            buffer.writeFloat(payload.steer);
            buffer.writeFloat(payload.upperYawInput);
            buffer.writeFloat(payload.boomInput);
            buffer.writeFloat(payload.stickInput);
            buffer.writeFloat(payload.bucketInput);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
