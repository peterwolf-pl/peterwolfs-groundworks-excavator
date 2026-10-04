package com.piotrek.groundworksexcavator.client.model;

import com.piotrek.groundworksexcavator.client.render.ExcavatorRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Geometric hierarchy model for the tracked excavator with dual interchangeable buckets.
 *
 * <p>Includes:
 * <ul>
 *   <li>Standard 256-unit (0.500 m³) trenching scoop with 5 teeth</li>
 *   <li>Large 512-unit (1.000 m³ - 2x capacity) bulk earthmoving scoop with 7 teeth</li>
 *   <li>Flashing yellow warning beacon on cab roof</li>
 * </ul>
 */
public class ExcavatorModel extends EntityModel<ExcavatorRenderState> {

    private final ModelPart undercarriage;
    private final ModelPart upperBody;
    private final ModelPart boom;
    private final ModelPart stick;
    private final ModelPart bucket;
    private final ModelPart bucketStandard;
    private final ModelPart bucketLarge;
    private final ModelPart bucketContentsStandard;
    private final ModelPart bucketContentsLarge;
    private final ModelPart beaconReflector;

    public ExcavatorModel(ModelPart root) {
        super(root);
        this.undercarriage = root.getChild("undercarriage");
        this.upperBody = root.getChild("upper_body");
        this.boom = this.upperBody.getChild("boom");
        this.stick = this.boom.getChild("stick");
        this.bucket = this.stick.getChild("bucket");
        this.bucketStandard = this.bucket.getChild("bucket_standard");
        this.bucketLarge = this.bucket.getChild("bucket_large");
        this.bucketContentsStandard = this.bucketStandard.getChild("bucket_contents_std");
        this.bucketContentsLarge = this.bucketLarge.getChild("bucket_contents_large");
        this.beaconReflector = this.upperBody.getChild("beacon_base").getChild("beacon_reflector");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // ── 1. Undercarriage & Tracks (Ground level Y = 24 in model space) ──
        PartDefinition undercarriage = root.addOrReplaceChild(
                "undercarriage",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-10.0F, 12.0F, -20.0F, 20.0F, 6.0F, 40.0F)
                        .texOffs(0, 46).addBox(-8.0F, 10.0F, -8.0F, 16.0F, 2.0F, 16.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        // Left track crawler
        undercarriage.addOrReplaceChild(
                "left_track",
                CubeListBuilder.create()
                        .texOffs(0, 64).addBox(-5.0F, -7.0F, -28.0F, 10.0F, 14.0F, 56.0F)
                        .texOffs(76, 64).addBox(-5.5F, -6.0F, 22.0F, 11.0F, 12.0F, 8.0F)
                        .texOffs(76, 64).addBox(-5.5F, -6.0F, -30.0F, 11.0F, 12.0F, 8.0F)
                        .texOffs(76, 64).addBox(-4.5F, 4.0F, -18.0F, 9.0F, 4.0F, 6.0F)
                        .texOffs(76, 64).addBox(-4.5F, 4.0F, -6.0F, 9.0F, 4.0F, 6.0F)
                        .texOffs(76, 64).addBox(-4.5F, 4.0F, 6.0F, 9.0F, 4.0F, 6.0F)
                        .texOffs(76, 64).addBox(-4.5F, 4.0F, 18.0F, 9.0F, 4.0F, 6.0F)
                        .texOffs(76, 64).addBox(-4.5F, -8.0F, -10.0F, 9.0F, 3.0F, 5.0F)
                        .texOffs(76, 64).addBox(-4.5F, -8.0F, 10.0F, 9.0F, 3.0F, 5.0F),
                PartPose.offset(-17.0F, 17.0F, 0.0F)
        );

        // Right track crawler
        undercarriage.addOrReplaceChild(
                "right_track",
                CubeListBuilder.create()
                        .texOffs(0, 64).addBox(-5.0F, -7.0F, -28.0F, 10.0F, 14.0F, 56.0F)
                        .texOffs(76, 64).addBox(-5.5F, -6.0F, 22.0F, 11.0F, 12.0F, 8.0F)
                        .texOffs(76, 64).addBox(-5.5F, -6.0F, -30.0F, 11.0F, 12.0F, 8.0F)
                        .texOffs(76, 64).addBox(-4.5F, 4.0F, -18.0F, 9.0F, 4.0F, 6.0F)
                        .texOffs(76, 64).addBox(-4.5F, 4.0F, -6.0F, 9.0F, 4.0F, 6.0F)
                        .texOffs(76, 64).addBox(-4.5F, 4.0F, 6.0F, 9.0F, 4.0F, 6.0F)
                        .texOffs(76, 64).addBox(-4.5F, 4.0F, 18.0F, 9.0F, 4.0F, 6.0F)
                        .texOffs(76, 64).addBox(-4.5F, -8.0F, -10.0F, 9.0F, 3.0F, 5.0F)
                        .texOffs(76, 64).addBox(-4.5F, -8.0F, 10.0F, 9.0F, 3.0F, 5.0F),
                PartPose.offset(17.0F, 17.0F, 0.0F)
        );

        // ── 2. Upper Body (Turntable) ──────────────────────────────────
        PartDefinition upperBody = root.addOrReplaceChild(
                "upper_body",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-18.0F, -2.0F, -24.0F, 36.0F, 3.0F, 44.0F)
                        .texOffs(0, 47).addBox(-18.0F, -14.0F, -26.0F, 36.0F, 14.0F, 10.0F)
                        .texOffs(80, 0).addBox(-2.0F, -14.0F, -16.0F, 20.0F, 12.0F, 32.0F)
                        .texOffs(80, 44).addBox(-17.0F, -22.0F, -6.0F, 14.0F, 20.0F, 22.0F)
                        .texOffs(76, 84).addBox(-18.0F, -23.0F, -7.0F, 16.0F, 2.0F, 24.0F)
                        .texOffs(0, 20).addBox(-14.0F, -8.0F, 2.0F, 8.0F, 6.0F, 8.0F)
                        .texOffs(0, 20).addBox(-14.0F, -16.0F, 8.0F, 8.0F, 8.0F, 2.0F)
                        .texOffs(112, 68).addBox(-15.0F, -11.0F, 4.0F, 1.0F, 4.0F, 1.0F)
                        .texOffs(112, 68).addBox(-5.0F, -11.0F, 4.0F, 1.0F, 4.0F, 1.0F)
                        .texOffs(112, 104).addBox(-19.0F, -16.0F, 12.0F, 2.0F, 1.0F, 3.0F)
                        .texOffs(112, 104).addBox(-20.0F, -18.0F, 14.0F, 1.0F, 5.0F, 3.0F)
                        .texOffs(112, 92).addBox(12.0F, -24.0F, -18.0F, 3.0F, 10.0F, 3.0F)
                        .texOffs(112, 92).addBox(11.5F, -25.5F, -18.5F, 4.0F, 2.0F, 4.0F),
                PartPose.offset(0.0F, 9.0F, 0.0F)
        );

        // Flashing Warning Beacon on Cab Roof
        PartDefinition beaconBase = upperBody.addOrReplaceChild(
                "beacon_base",
                CubeListBuilder.create()
                        .texOffs(112, 68).addBox(-2.5F, -2.0F, -2.5F, 5.0F, 2.0F, 5.0F)
                        .texOffs(112, 74).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                PartPose.offset(-10.0F, -23.0F, 12.0F)
        );

        beaconBase.addOrReplaceChild(
                "beacon_reflector",
                CubeListBuilder.create()
                        .texOffs(112, 86).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        // ── 3. Boom ───────────────────────────────────────────────────
        PartDefinition boom = upperBody.addOrReplaceChild(
                "boom",
                CubeListBuilder.create()
                        .texOffs(0, 80).addBox(-3.5F, -4.0F, 0.0F, 7.0F, 8.0F, 32.0F)
                        .texOffs(46, 80).addBox(-3.0F, -3.5F, 30.0F, 6.0F, 7.0F, 26.0F)
                        .texOffs(76, 110).addBox(-4.5F, 3.0F, 8.0F, 3.0F, 3.0F, 22.0F)
                        .texOffs(76, 110).addBox(1.5F, 3.0F, 8.0F, 3.0F, 3.0F, 22.0F),
                PartPose.offset(5.5F, -6.0F, 5.0F)
        );

        // ── 4. Stick ──────────────────────────────────────────────────
        PartDefinition stick = boom.addOrReplaceChild(
                "stick",
                CubeListBuilder.create()
                        .texOffs(0, 120).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 38.0F)
                        .texOffs(48, 115).addBox(-1.5F, -6.5F, 6.0F, 3.0F, 4.0F, 20.0F),
                PartPose.offset(0.0F, 0.0F, 56.0F)
        );

        // ── 5. Root Bucket Joint ──────────────────────────────────────
        PartDefinition bucket = stick.addOrReplaceChild(
                "bucket",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 38.0F)
        );

        // ── 5A. STANDARD BUCKET (256 units / 0.500 m³ — 12px wide, 5 teeth) ──
        PartDefinition bucketStd = bucket.addOrReplaceChild(
                "bucket_standard",
                CubeListBuilder.create()
                        // Top linkage
                        .texOffs(90, 86).addBox(-5.0F, -3.0F, -3.0F, 10.0F, 6.0F, 6.0F)
                        // Outer back wall
                        .texOffs(90, 94).addBox(-6.0F, -1.0F, 1.0F, 12.0F, 10.0F, 3.0F)
                        // Heel curve
                        .texOffs(90, 94).addBox(-6.0F, 8.0F, -4.0F, 12.0F, 3.0F, 8.0F)
                        // Cheeks
                        .texOffs(74, 98).addBox(-6.0F, 1.0F, -10.0F, 2.0F, 9.0F, 13.0F)
                        .texOffs(74, 98).addBox(4.0F, 1.0F, -10.0F, 2.0F, 9.0F, 13.0F)
                        // Floor & Lip
                        .texOffs(90, 108).addBox(-6.0F, 8.0F, -12.0F, 12.0F, 2.0F, 10.0F)
                        .texOffs(90, 114).addBox(-6.0F, 8.0F, -14.0F, 12.0F, 2.0F, 2.0F)
                        // 5 Teeth
                        .texOffs(90, 119).addBox(-5.5F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(90, 119).addBox(-2.75F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(90, 119).addBox(0.0F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(90, 119).addBox(2.75F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(90, 119).addBox(5.0F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        bucketStd.addOrReplaceChild(
                "bucket_contents_std",
                CubeListBuilder.create()
                        .texOffs(90, 123).addBox(-4.0F, 2.0F, -9.0F, 8.0F, 6.0F, 11.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        // ── 5B. LARGE BULK BUCKET (512 units / 1.000 m³ — 20px wide, 7 teeth, 2x capacity) ──
        PartDefinition bucketLarge = bucket.addOrReplaceChild(
                "bucket_large",
                CubeListBuilder.create()
                        // Heavy top linkage
                        .texOffs(90, 86).addBox(-6.0F, -3.0F, -3.0F, 12.0F, 6.0F, 6.0F)
                        // Wide outer back wall
                        .texOffs(90, 94).addBox(-10.0F, -2.0F, 1.0F, 20.0F, 11.0F, 4.0F)
                        // Deep heel curve
                        .texOffs(90, 94).addBox(-10.0F, 8.0F, -5.0F, 20.0F, 4.0F, 10.0F)
                        // Wide cheek plates
                        .texOffs(74, 98).addBox(-10.0F, 0.0F, -11.0F, 2.0F, 10.0F, 15.0F)
                        .texOffs(74, 98).addBox(8.0F, 0.0F, -11.0F, 2.0F, 10.0F, 15.0F)
                        // Wide bottom floor & Lip
                        .texOffs(90, 108).addBox(-10.0F, 8.0F, -13.0F, 20.0F, 2.0F, 12.0F)
                        .texOffs(90, 114).addBox(-10.0F, 8.0F, -14.5F, 20.0F, 2.0F, 2.5F)
                        // 7 Heavy chisel cutting teeth
                        .texOffs(90, 119).addBox(-9.5F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(90, 119).addBox(-6.3F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(90, 119).addBox(-3.15F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(90, 119).addBox(0.0F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(90, 119).addBox(3.15F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(90, 119).addBox(6.3F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(90, 119).addBox(8.5F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        bucketLarge.addOrReplaceChild(
                "bucket_contents_large",
                CubeListBuilder.create()
                        .texOffs(90, 123).addBox(-8.0F, 1.0F, -10.0F, 16.0F, 7.0F, 12.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(ExcavatorRenderState state) {
        super.setupAnim(state);

        // Turntable yaw rotation (relative to undercarriage)
        this.upperBody.yRot = (float) Math.toRadians(state.upperYaw);

        // Hierarchical arm joint angles
        this.boom.xRot = (float) Math.toRadians(state.boomAngle);
        this.stick.xRot = (float) Math.toRadians(state.stickAngle);
        this.bucket.xRot = (float) Math.toRadians(state.bucketAngle + 45.0F);

        // Switch bucket variant based on state.bucketType
        boolean isLarge = state.bucketType == 1;
        this.bucketStandard.visible = !isLarge;
        this.bucketLarge.visible = isLarge;

        // Visual fill level inside active bucket cavity
        this.bucketContentsStandard.visible = !isLarge && state.fillRatio > 0.02F;
        this.bucketContentsStandard.yScale = Math.max(0.15F, state.fillRatio);

        this.bucketContentsLarge.visible = isLarge && state.fillRatio > 0.02F;
        this.bucketContentsLarge.yScale = Math.max(0.15F, state.fillRatio);

        // Rotating warning beacon reflector when machine is operating
        if (state.isOperating) {
            this.beaconReflector.yRot = state.beaconSpin;
            this.beaconReflector.visible = true;
        } else {
            this.beaconReflector.yRot = 0.0F;
            this.beaconReflector.visible = false;
        }
    }
}
