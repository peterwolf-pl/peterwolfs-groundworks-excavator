package com.piotrek.groundworksexcavator.network;

import com.piotrek.groundworksexcavator.GroundworksExcavatorMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Compact client operator intent packet.
 *
 * <p>Transmits:
 * <ul>
 *   <li>control mode (0=Drive, 1=Arm)</li>
 *   <li>attachment type (0=Standard bucket, 1=Large bucket, 2=Hydraulic hammer)</li>
 *   <li>normalized drive inputs (-1.0 .. +1.0) for tracks, cab, boom, stick, attachment angle</li>
 *   <li>momentary or latched hydraulic hammer request</li>
 * </ul>
 */
public record ExcavatorInputPayload(
        int mode,
        int bucketType,
        float throttle,
        float steer,
        float upperYawInput,
        float boomInput,
        float stickInput,
        float bucketInput,
        boolean hammerActive
) implements CustomPacketPayload {

    public static final Type<ExcavatorInputPayload> TYPE = new Type<>(GroundworksExcavatorMod.id("excavator_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExcavatorInputPayload> CODEC = new StreamCodec<>() {
        @Override
        public ExcavatorInputPayload decode(RegistryFriendlyByteBuf buffer) {
            return new ExcavatorInputPayload(
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readBoolean()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ExcavatorInputPayload payload) {
            buffer.writeVarInt(payload.mode);
            buffer.writeVarInt(payload.bucketType);
            buffer.writeFloat(payload.throttle);
            buffer.writeFloat(payload.steer);
            buffer.writeFloat(payload.upperYawInput);
            buffer.writeFloat(payload.boomInput);
            buffer.writeFloat(payload.stickInput);
            buffer.writeFloat(payload.bucketInput);
            buffer.writeBoolean(payload.hammerActive);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
