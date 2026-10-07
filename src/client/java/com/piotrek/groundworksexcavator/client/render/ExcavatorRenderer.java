package com.piotrek.groundworksexcavator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
import com.piotrek.groundworksexcavator.GroundworksExcavatorMod;
import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import com.piotrek.groundworksexcavator.client.GroundworksExcavatorClient;
import com.piotrek.groundworksexcavator.client.model.ExcavatorModel;
import com.piotrek.groundworksexcavator.client.model.HydraulicHammerModel;
import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
import com.piotrek.groundworksexcavator.vehicle.BeaconLight;
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
    public static final Identifier HAMMER_TEXTURE =
            GroundworksExcavatorMod.id("textures/entity/hydraulic_hammer.png");

    private final ExcavatorModel model;
    private final HydraulicHammerModel hammerModel;

    public ExcavatorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new ExcavatorModel(context.bakeLayer(GroundworksExcavatorClient.EXCAVATOR_LAYER));
        this.hammerModel = new HydraulicHammerModel(
                context.bakeLayer(GroundworksExcavatorClient.HYDRAULIC_HAMMER_LAYER));
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
        state.upperYaw = entity.getVisualUpperYaw(partialTick);
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
        state.isHammering = entity.isHammering();
        float hammerPhase = ((entity.tickCount + partialTick) % 4.0F) / 4.0F;
        state.hammerStroke = state.isHammering
                ? (float) Math.sin(hammerPhase * Math.PI)
                : 0.0F;

        state.isOperating = entity.isOperating();
        state.machineLoad = entity.getMachineLoad();
        state.beaconSpin = BeaconLight.spin(entity.tickCount, partialTick);
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

        // Render excavator model using cutout layer for crisp, artifact-free textures and open cabin view
        collector.submitModel(
                this.model,
                state,
                stack,
                RenderTypes.entityCutout(TEXTURE),
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                state.outlineColor
        );

        if (state.bucketType == GroundworksExcavatorEntity.BUCKET_HAMMER) {
            stack.pushPose();

            // Reproduce the exact ModelPart hierarchy to the bucket joint.
            // ArmKinematics uses the same offsets and rotations server-side.
            stack.translate(0.0F, 9.0F / 16.0F, 0.0F);
            stack.rotateDegrees(Axis.YP, state.upperYaw);
            stack.translate(5.5F / 16.0F, -6.0F / 16.0F, 5.0F / 16.0F);
            stack.rotateDegrees(Axis.XP, state.boomAngle);
            stack.translate(0.0F, 0.0F, 56.0F / 16.0F);
            stack.rotateDegrees(Axis.XP, state.stickAngle);
            stack.translate(0.0F, 0.0F, 38.0F / 16.0F);
            stack.rotateDegrees(Axis.XP, state.bucketAngle + 45.0F);

            this.hammerModel.setupAnim(state);
            collector.submitModel(
                    this.hammerModel,
                    state,
                    stack,
                    RenderTypes.entityCutout(HAMMER_TEXTURE),
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    state.outlineColor
            );
            stack.popPose();
        }

        stack.popPose();
    }
}
