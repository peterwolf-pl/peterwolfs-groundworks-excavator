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
 * Geometric hierarchy model for the tracked excavator with dual interchangeable buckets
 * and a realistic hollow ROPS safety cabin with genuine panoramic safety glass windows.
 *
 * <p>All components map to 100% non-overlapping UV regions on a clean 512x512 texture atlas.
 */
public class ExcavatorModel extends EntityModel<ExcavatorRenderState> {

    private final ModelPart undercarriage;
    private final ModelPart upperBody;
    private final ModelPart cabinGlass;
    private final ModelPart boom;
    private final ModelPart stick;
    private final ModelPart bucket;
    private final ModelPart bucketStandard;
    private final ModelPart bucketLarge;
    private final ModelPart bucketContentsStandard;
    private final ModelPart bucketContentsLarge;
    private final ModelPart beaconReflectorOn;
    private final ModelPart beaconReflectorOff;

    public ExcavatorModel(ModelPart root) {
        super(root);
        this.undercarriage = root.getChild("undercarriage");
        this.upperBody = root.getChild("upper_body");
        this.cabinGlass = this.upperBody.getChild("cabin_glass");
        this.boom = this.upperBody.getChild("boom");
        this.stick = this.boom.getChild("stick");
        this.bucket = this.stick.getChild("bucket");
        this.bucketStandard = this.bucket.getChild("bucket_standard");
        this.bucketLarge = this.bucket.getChild("bucket_large");
        this.bucketContentsStandard = this.bucketStandard.getChild("bucket_contents_std");
        this.bucketContentsLarge = this.bucketLarge.getChild("bucket_contents_large");
        ModelPart beaconBase = this.upperBody.getChild("beacon_base");
        this.beaconReflectorOn = beaconBase.getChild("beacon_reflector_on");
        this.beaconReflectorOff = beaconBase.getChild("beacon_reflector_off");
    }

    public ModelPart getCabinGlass() {
        return this.cabinGlass;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // ── 1. Undercarriage & Tracks (Ground level Y = 24 in model space) ──
        PartDefinition undercarriage = root.addOrReplaceChild(
                "undercarriage",
                CubeListBuilder.create()
                        // Central X-chassis frame: 120x46 UV [300..420, 0..46]
                        .texOffs(300, 0).addBox(-10.0F, 12.0F, -20.0F, 20.0F, 6.0F, 40.0F)
                        // Turntable base ring: 64x18 UV [448..512, 122..140]
                        .texOffs(448, 122).addBox(-8.0F, 10.0F, -8.0F, 16.0F, 2.0F, 16.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        // Left track crawler (with guide rollers & sprockets)
        undercarriage.addOrReplaceChild(
                "left_track",
                CubeListBuilder.create()
                        // Track belt: 132x70 UV [0..132, 0..70]
                        .texOffs(0, 0).addBox(-5.0F, -7.0F, -28.0F, 10.0F, 14.0F, 56.0F)
                        // Drive sprocket front & idler: 38x20 UV [346..384, 122..142]
                        .texOffs(346, 122).addBox(-5.5F, -6.0F, 22.0F, 11.0F, 12.0F, 8.0F)
                        .texOffs(346, 122).addBox(-5.5F, -6.0F, -30.0F, 11.0F, 12.0F, 8.0F)
                        // Bottom road rollers
                        .texOffs(346, 122).addBox(-4.5F, 4.0F, -18.0F, 9.0F, 4.0F, 6.0F)
                        .texOffs(346, 122).addBox(-4.5F, 4.0F, -6.0F, 9.0F, 4.0F, 6.0F)
                        .texOffs(346, 122).addBox(-4.5F, 4.0F, 6.0F, 9.0F, 4.0F, 6.0F)
                        .texOffs(346, 122).addBox(-4.5F, 4.0F, 18.0F, 9.0F, 4.0F, 6.0F)
                        // Top carrier return rollers
                        .texOffs(346, 122).addBox(-4.5F, -8.0F, -10.0F, 9.0F, 3.0F, 5.0F)
                        .texOffs(346, 122).addBox(-4.5F, -8.0F, 10.0F, 9.0F, 3.0F, 5.0F),
                PartPose.offset(-17.0F, 17.0F, 0.0F)
        );

        // Right track crawler
        undercarriage.addOrReplaceChild(
                "right_track",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5.0F, -7.0F, -28.0F, 10.0F, 14.0F, 56.0F)
                        .texOffs(346, 122).addBox(-5.5F, -6.0F, 22.0F, 11.0F, 12.0F, 8.0F)
                        .texOffs(346, 122).addBox(-5.5F, -6.0F, -30.0F, 11.0F, 12.0F, 8.0F)
                        .texOffs(346, 122).addBox(-4.5F, 4.0F, -18.0F, 9.0F, 4.0F, 6.0F)
                        .texOffs(346, 122).addBox(-4.5F, 4.0F, -6.0F, 9.0F, 4.0F, 6.0F)
                        .texOffs(346, 122).addBox(-4.5F, 4.0F, 6.0F, 9.0F, 4.0F, 6.0F)
                        .texOffs(346, 122).addBox(-4.5F, 4.0F, 18.0F, 9.0F, 4.0F, 6.0F)
                        .texOffs(346, 122).addBox(-4.5F, -8.0F, -10.0F, 9.0F, 3.0F, 5.0F)
                        .texOffs(346, 122).addBox(-4.5F, -8.0F, 10.0F, 9.0F, 3.0F, 5.0F),
                PartPose.offset(17.0F, 17.0F, 0.0F)
        );

        // ── 2. Upper Body (Turntable Deck & Machinery) ────────────────
        PartDefinition upperBody = root.addOrReplaceChild(
                "upper_body",
                CubeListBuilder.create()
                        // Upper rotating deck plate: 160x47 UV [136..296, 0..47]
                        .texOffs(136, 0).addBox(-18.0F, -2.0F, -24.0F, 36.0F, 3.0F, 44.0F)
                        // Rear counterweight with hazard stripes: 92x24 UV [158..250, 122..146]
                        .texOffs(158, 122).addBox(-18.0F, -14.0F, -26.0F, 36.0F, 14.0F, 10.0F)
                        // Engine compartment housing: 104x44 UV [0..104, 74..118]
                        .texOffs(0, 74).addBox(-2.0F, -14.0F, -16.0F, 20.0F, 12.0F, 32.0F)
                        // Engine exhaust stack: 16x14 UV [66..82, 152..166]
                        .texOffs(66, 152).addBox(12.0F, -24.0F, -18.0F, 3.0F, 10.0F, 3.0F)
                        .texOffs(66, 152).addBox(11.5F, -25.5F, -18.5F, 4.0F, 2.0F, 4.0F)
                        // Rearview mirror on cab front-left pillar: 10x8 UV [110..120, 152..160]
                        .texOffs(110, 152).addBox(-19.0F, -16.0F, 12.0F, 2.0F, 1.0F, 3.0F)
                        .texOffs(110, 152).addBox(-20.0F, -18.0F, 14.0F, 1.0F, 5.0F, 3.0F)

                        // ── Hollow Operator Cabin (ROPS Roll-Cage Frame) ──
                        // Cab floor plate: UV [254..286, 122..146]
                        .texOffs(254, 122).addBox(-17.0F, -2.0F, -6.0F, 14.0F, 2.0F, 22.0F)
                        // Rear steel bulkhead (behind driver seat)
                        .texOffs(254, 122).addBox(-17.0F, -25.0F, -6.0F, 14.0F, 23.0F, 2.0F)
                        // Left lower door panel
                        .texOffs(254, 122).addBox(-17.0F, -9.0F, -4.0F, 1.0F, 7.0F, 19.0F)
                        // Right lower sill panel
                        .texOffs(254, 122).addBox(-4.0F, -9.0F, -4.0F, 1.0F, 7.0F, 19.0F)
                        // Front lower dashboard console
                        .texOffs(254, 122).addBox(-16.0F, -7.0F, 12.0F, 12.0F, 5.0F, 3.0F)
                        // Cab roof ceiling plate: placed HIGH at y=-27 to NOT block player's forward view!
                        // 80x26 UV [0..80, 122..148]
                        .texOffs(0, 122).addBox(-18.0F, -27.0F, -7.0F, 16.0F, 2.0F, 23.0F)

                        // 4 Steel Corner Roll-Cage Pillars
                        .texOffs(254, 122).addBox(-17.0F, -26.0F, 14.0F, 1.5F, 24.0F, 1.5F) // Front-Left
                        .texOffs(254, 122).addBox(-4.5F, -26.0F, 14.0F, 1.5F, 24.0F, 1.5F)  // Front-Right
                        .texOffs(254, 122).addBox(-17.0F, -26.0F, -6.0F, 1.5F, 24.0F, 1.5F)  // Rear-Left
                        .texOffs(254, 122).addBox(-4.5F, -26.0F, -6.0F, 1.5F, 24.0F, 1.5F)   // Rear-Right

                        // ── Operator Seat & Dual Joystick Controls ────────
                        // Cushioned driver seat: 32x16 UV [30..62, 152..168]
                        .texOffs(30, 152).addBox(-13.5F, -6.0F, 0.0F, 7.0F, 4.0F, 7.0F)
                        // Ergonomic seat backrest
                        .texOffs(30, 152).addBox(-13.5F, -14.0F, 5.0F, 7.0F, 8.0F, 2.0F)
                        // Headrest
                        .texOffs(30, 152).addBox(-12.5F, -18.0F, 5.0F, 5.0F, 4.0F, 2.0F)
                        // Left joystick & armrest console: 16x14 UV [66..82, 152..166]
                        .texOffs(66, 152).addBox(-15.5F, -9.0F, 2.0F, 1.5F, 4.0F, 3.0F)
                        // Right joystick & armrest console
                        .texOffs(66, 152).addBox(-6.0F, -9.0F, 2.0F, 1.5F, 4.0F, 3.0F)
                        // Track foot drive pedals
                        .texOffs(66, 152).addBox(-12.0F, -2.5F, 10.0F, 2.0F, 1.0F, 3.0F)
                        .texOffs(66, 152).addBox(-9.0F, -2.5F, 10.0F, 2.0F, 1.0F, 3.0F),
                PartPose.offset(0.0F, 9.0F, 0.0F)
        );

        // ── Crystal-Clear Safety Glass Window Panes (Sub-part rendered in translucent pass) ──
        upperBody.addOrReplaceChild(
                "cabin_glass",
                CubeListBuilder.create()
                        // Front upper main windshield (high ceiling, full panoramic visibility!)
                        // 26x17 UV [0..26, 152..169]
                        .texOffs(0, 152).addBox(-15.5F, -25.5F, 14.2F, 11.0F, 18.5F, 0.5F)
                        // Front lower trench inspection window (looking right in front of tracks!)
                        .texOffs(0, 152).addBox(-15.5F, -7.0F, 14.8F, 11.0F, 5.0F, 0.5F)
                        // Left door glass window: 38x32 UV [348..386, 74..106]
                        .texOffs(348, 74).addBox(-16.8F, -25.0F, -4.0F, 0.5F, 16.0F, 18.0F)
                        // Right panoramic glass window (view of boom & hydraulics)
                        .texOffs(348, 74).addBox(-3.8F, -25.0F, -4.0F, 0.5F, 16.0F, 18.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        // Flashing Warning Beacon on Cab Roof (Base mount + Amber Dome)
        PartDefinition beaconBase = upperBody.addOrReplaceChild(
                "beacon_base",
                CubeListBuilder.create()
                        .texOffs(86, 152).addBox(-2.5F, -2.0F, -2.5F, 5.0F, 2.0F, 5.0F)
                        .texOffs(86, 160).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                PartPose.offset(-10.0F, -27.0F, 12.0F)
        );

        // Blinking strobe core: ON (bright yellow light) and OFF (dim amber)
        beaconBase.addOrReplaceChild(
                "beacon_reflector_on",
                CubeListBuilder.create()
                        .texOffs(108, 160).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        beaconBase.addOrReplaceChild(
                "beacon_reflector_off",
                CubeListBuilder.create()
                        .texOffs(108, 152).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        // ── 3. Boom ───────────────────────────────────────────────────
        PartDefinition boom = upperBody.addOrReplaceChild(
                "boom",
                CubeListBuilder.create()
                        // Main lower boom heavy arm: 78x40 UV [198..276, 74..114]
                        .texOffs(198, 74).addBox(-3.5F, -4.0F, 0.0F, 7.0F, 8.0F, 32.0F)
                        // Upper curved boom section: 64x33 UV [280..344, 74..107]
                        .texOffs(280, 74).addBox(-3.0F, -3.5F, 30.0F, 6.0F, 7.0F, 26.0F)
                        // Dual hydraulic boom lift cylinders: 56x28 UV [390..446, 74..102]
                        .texOffs(390, 74).addBox(-4.5F, 3.0F, 8.0F, 3.0F, 3.0F, 22.0F)
                        .texOffs(390, 74).addBox(1.5F, 3.0F, 8.0F, 3.0F, 3.0F, 22.0F),
                PartPose.offset(5.5F, -6.0F, 5.0F)
        );

        // ── 4. Stick ──────────────────────────────────────────────────
        PartDefinition stick = boom.addOrReplaceChild(
                "stick",
                CubeListBuilder.create()
                        // Stick beam: 86x43 UV [108..194, 74..117]
                        .texOffs(108, 74).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 38.0F)
                        // Stick hydraulic cylinder: 56x28 UV [390..446, 74..102]
                        .texOffs(390, 74).addBox(-1.5F, -6.5F, 6.0F, 3.0F, 4.0F, 20.0F),
                PartPose.offset(0.0F, 0.0F, 56.0F)
        );

        // ── 5. Root Bucket Joint ──────────────────────────────────────
        PartDefinition bucket = stick.addOrReplaceChild(
                "bucket",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 38.0F)
        );

        // ── 5A. STANDARD BUCKET (256 units / 0.500 m³ — 12px wide, 5 teeth) ──
        // 52x24 UV [290..342, 122..146]
        PartDefinition bucketStd = bucket.addOrReplaceChild(
                "bucket_standard",
                CubeListBuilder.create()
                        .texOffs(290, 122).addBox(-5.0F, -3.0F, -3.0F, 10.0F, 6.0F, 6.0F)
                        .texOffs(290, 122).addBox(-6.0F, -1.0F, 1.0F, 12.0F, 10.0F, 3.0F)
                        .texOffs(290, 122).addBox(-6.0F, 8.0F, -4.0F, 12.0F, 3.0F, 8.0F)
                        .texOffs(290, 122).addBox(-6.0F, 1.0F, -10.0F, 2.0F, 9.0F, 13.0F)
                        .texOffs(290, 122).addBox(4.0F, 1.0F, -10.0F, 2.0F, 9.0F, 13.0F)
                        .texOffs(290, 122).addBox(-6.0F, 8.0F, -12.0F, 12.0F, 2.0F, 10.0F)
                        .texOffs(290, 122).addBox(-6.0F, 8.0F, -14.0F, 12.0F, 2.0F, 2.0F)
                        // 5 Teeth: 16x8 UV [124..140, 152..160]
                        .texOffs(124, 152).addBox(-5.5F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(124, 152).addBox(-2.75F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(124, 152).addBox(0.0F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(124, 152).addBox(2.75F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(124, 152).addBox(5.0F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        // Soil inside standard bucket: 56x19 UV [388..444, 122..141]
        bucketStd.addOrReplaceChild(
                "bucket_contents_std",
                CubeListBuilder.create()
                        .texOffs(388, 122).addBox(-4.0F, 2.0F, -9.0F, 8.0F, 6.0F, 11.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        // ── 5B. LARGE BULK BUCKET (512 units / 1.000 m³ — 20px wide, 7 teeth) ──
        // 70x26 UV [84..154, 122..148]
        PartDefinition bucketLarge = bucket.addOrReplaceChild(
                "bucket_large",
                CubeListBuilder.create()
                        .texOffs(84, 122).addBox(-6.0F, -3.0F, -3.0F, 12.0F, 6.0F, 6.0F)
                        .texOffs(84, 122).addBox(-10.0F, -2.0F, 1.0F, 20.0F, 11.0F, 4.0F)
                        .texOffs(84, 122).addBox(-10.0F, 8.0F, -5.0F, 20.0F, 4.0F, 10.0F)
                        .texOffs(84, 122).addBox(-10.0F, 0.0F, -11.0F, 2.0F, 10.0F, 15.0F)
                        .texOffs(84, 122).addBox(8.0F, 0.0F, -11.0F, 2.0F, 10.0F, 15.0F)
                        .texOffs(84, 122).addBox(-10.0F, 8.0F, -13.0F, 20.0F, 2.0F, 12.0F)
                        .texOffs(84, 122).addBox(-10.0F, 8.0F, -14.5F, 20.0F, 2.0F, 2.5F)
                        // 7 Teeth: 16x8 UV [124..140, 152..160]
                        .texOffs(124, 152).addBox(-9.5F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(124, 152).addBox(-6.3F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(124, 152).addBox(-3.15F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(124, 152).addBox(0.0F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(124, 152).addBox(3.15F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(124, 152).addBox(6.3F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(124, 152).addBox(8.5F, 8.5F, -17.5F, 1.5F, 1.2F, 3.5F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        // Soil inside large bucket: 56x19 UV [388..444, 122..141]
        bucketLarge.addOrReplaceChild(
                "bucket_contents_large",
                CubeListBuilder.create()
                        .texOffs(388, 122).addBox(-8.0F, 1.0F, -10.0F, 16.0F, 7.0F, 12.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        return LayerDefinition.create(mesh, 512, 512);
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

        // Flashing yellow warning beacon: rotates and blinks bright yellow light during operation
        if (state.isOperating) {
            this.beaconReflectorOn.yRot = state.beaconSpin;
            this.beaconReflectorOff.yRot = state.beaconSpin;
            // Alternates blinking between bright flash and dim amber every few ticks
            this.beaconReflectorOn.visible = state.beaconFlash;
            this.beaconReflectorOff.visible = !state.beaconFlash;
        } else {
            this.beaconReflectorOn.visible = false;
            this.beaconReflectorOff.visible = false;
        }
    }
}
