package com.piotrek.groundworksexcavator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
import com.piotrek.groundworksexcavator.GroundworksExcavatorMod;
import com.piotrek.groundworksexcavator.client.GroundworksExcavatorClient;
import com.piotrek.groundworksexcavator.client.model.ExcavatorModel;
import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * 26.3 entity renderer for the tracked excavator.
 */
public class ExcavatorRenderer extends EntityRenderer<GroundworksExcavatorEntity, ExcavatorRenderState> {

    public static final Identifier TEXTURE = GroundworksExcavatorMod.id("textures/entity/excavator.png");

    private final ExcavatorModel model;

    public ExcavatorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new ExcavatorModel(context.bakeLayer(GroundworksExcavatorClient.EXCAVATOR_LAYER));
        this.shadowRadius = 1.6F;
    }

    @Override
    public ExcavatorRenderState createRenderState() {
        return new ExcavatorRenderState();
    }

    @Override
    public void extractRenderState(GroundworksExcavatorEntity entity, ExcavatorRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.baseYaw = entity.getYRot();
        state.basePitch = entity.getVehiclePitch();
        state.baseRoll = entity.getVehicleRoll();
        state.upperYaw = entity.getUpperYaw();
        state.boomAngle = entity.getBoomAngle();
        state.stickAngle = entity.getStickAngle();
        state.bucketAngle = entity.getBucketAngle();

        state.leftTrackSpeed = entity.getTrackLeftSpeed();
        state.rightTrackSpeed = entity.getTrackRightSpeed();

        state.materialId = entity.getBucketMaterialId();
        state.storedUnits = entity.getStoredUnits();
        state.capacity = entity.getBucketCapacity();
        state.fillRatio = (float) entity.getStoredUnits() / (float) Math.max(1, entity.getBucketCapacity());
        state.bucketType = entity.getBucketType();

        state.isDigging = entity.isDigging();
        state.isDumping = entity.isDumping();

        state.isOperating = entity.isOperating();
        state.machineLoad = entity.getMachineLoad();
        state.beaconSpin = (entity.tickCount + partialTick) * 0.75F;
        state.beaconFlash = state.isOperating && ((entity.tickCount / 4) % 2 == 0);
    }

    @Override
    public void submit(ExcavatorRenderState state, PoseStack stack, SubmitNodeCollector collector, CameraRenderState camera) {
        stack.pushPose();

        // Rotate undercarriage heading (facing where tracks drive)
        stack.rotateDegrees(Axis.YP, -state.baseYaw);

        // Apply ground pitch and roll
        if (Math.abs(state.basePitch) > 0.01F) {
            stack.rotateDegrees(Axis.XP, state.basePitch);
        }
        if (Math.abs(state.baseRoll) > 0.01F) {
            stack.rotateDegrees(Axis.ZP, state.baseRoll);
        }

        // Standard Minecraft entity model coordinate transform
        stack.scale(-1.0F, -1.0F, 1.0F);
        stack.translate(0.0F, -1.5F, 0.0F);

        this.model.setupAnim(state);

        // 1. Render solid opaque machine parts (tracks, chassis, engine, cab frame, boom, bucket)
        this.model.getCabinGlass().visible = false;
        collector.submitModel(
                this.model,
                state,
                stack,
                RenderTypes.entityCutout(TEXTURE),
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                state.outlineColor
        );

        // 2. Render cabin glass window panes in the translucent pass with proper turntable transform
        this.model.getCabinGlass().visible = true;
        stack.pushPose();
        // ModelPart upper_body transform:
        stack.translate(0.0F, 9.0F / 16.0F, 0.0F);
        stack.rotate(new Quaternionf().rotationZYX(0.0F, (float) Math.toRadians(state.upperYaw), 0.0F));
        collector.order(1).submitModelPart(
                this.model.getCabinGlass(),
                stack,
                RenderTypes.entityTranslucent(TEXTURE),
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                null
        );
        stack.popPose();

        stack.popPose();
    }
}
