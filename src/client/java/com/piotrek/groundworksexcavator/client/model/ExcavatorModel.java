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
 * Geometric hierarchy model for the tracked excavator.
 *
 * <p>Hierarchy:
 * <pre>
 * root
 *  ├── undercarriage
 *  │    ├── left_track
 *  │    └── right_track
 *  └── upper_body (turntable)
 *       ├── cab (with seat and glass)
 *       ├── engine_compartment & counterweight
 *       └── boom (gooseneck curved boom)
 *            └── stick (dipper arm)
 *                 └── bucket (backhoe scoop pointing down/in)
 *                      ├── teeth
 *                      └── bucket_contents (dynamic fill height)
 * </pre>
 */
public class ExcavatorModel extends EntityModel<ExcavatorRenderState> {

    private final ModelPart undercarriage;
    private final ModelPart upperBody;
    private final ModelPart boom;
    private final ModelPart stick;
    private final ModelPart bucket;
    private final ModelPart bucketContents;

    public ExcavatorModel(ModelPart root) {
        super(root);
        this.undercarriage = root.getChild("undercarriage");
        this.upperBody = root.getChild("upper_body");
        this.boom = this.upperBody.getChild("boom");
        this.stick = this.boom.getChild("stick");
        this.bucket = this.stick.getChild("bucket");
        this.bucketContents = this.bucket.getChild("bucket_contents");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // ── 1. Undercarriage & Tracks (Ground level Y = 24 in model space) ──
        PartDefinition undercarriage = root.addOrReplaceChild(
                "undercarriage",
                CubeListBuilder.create()
                        // Central X-chassis frame
                        .texOffs(0, 0).addBox(-10.0F, 12.0F, -20.0F, 20.0F, 6.0F, 40.0F)
                        // Turntable base ring
                        .texOffs(0, 46).addBox(-8.0F, 10.0F, -8.0F, 16.0F, 2.0F, 16.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        // Left track crawler
        undercarriage.addOrReplaceChild(
                "left_track",
                CubeListBuilder.create()
                        // Track belt
                        .texOffs(0, 64).addBox(-5.0F, -7.0F, -28.0F, 10.0F, 14.0F, 56.0F)
                        // Drive sprocket front
                        .texOffs(76, 64).addBox(-5.5F, -6.0F, 22.0F, 11.0F, 12.0F, 8.0F)
                        // Idler wheel rear
                        .texOffs(76, 64).addBox(-5.5F, -6.0F, -30.0F, 11.0F, 12.0F, 8.0F),
                PartPose.offset(-17.0F, 17.0F, 0.0F)
        );

        // Right track crawler
        undercarriage.addOrReplaceChild(
                "right_track",
                CubeListBuilder.create()
                        // Track belt
                        .texOffs(0, 64).addBox(-5.0F, -7.0F, -28.0F, 10.0F, 14.0F, 56.0F)
                        // Drive sprocket front
                        .texOffs(76, 64).addBox(-5.5F, -6.0F, 22.0F, 11.0F, 12.0F, 8.0F)
                        // Idler wheel rear
                        .texOffs(76, 64).addBox(-5.5F, -6.0F, -30.0F, 11.0F, 12.0F, 8.0F),
                PartPose.offset(17.0F, 17.0F, 0.0F)
        );

        // ── 2. Upper Body (Turntable) ──────────────────────────────────
        PartDefinition upperBody = root.addOrReplaceChild(
                "upper_body",
                CubeListBuilder.create()
                        // Upper rotating deck plate
                        .texOffs(0, 0).addBox(-18.0F, -2.0F, -24.0F, 36.0F, 3.0F, 44.0F)
                        // Heavy rear counterweight
                        .texOffs(0, 47).addBox(-18.0F, -14.0F, -26.0F, 36.0F, 14.0F, 10.0F)
                        // Engine compartment & hydraulic pump housing (right side)
                        .texOffs(80, 0).addBox(-2.0F, -14.0F, -16.0F, 20.0F, 12.0F, 32.0F)
                        // Operator cab (left side)
                        .texOffs(80, 44).addBox(-17.0F, -22.0F, -6.0F, 14.0F, 20.0F, 22.0F)
                        // Cab roof overhang
                        .texOffs(76, 84).addBox(-18.0F, -23.0F, -7.0F, 16.0F, 2.0F, 24.0F)
                        // Cab driver seat
                        .texOffs(0, 20).addBox(-14.0F, -8.0F, 2.0F, 8.0F, 6.0F, 8.0F)
                        // Seat backrest
                        .texOffs(0, 20).addBox(-14.0F, -16.0F, 8.0F, 8.0F, 8.0F, 2.0F),
                PartPose.offset(0.0F, 9.0F, 0.0F)
        );

        // ── 3. Boom (Gooseneck Boom, pivots at cab front-right) ─────────
        PartDefinition boom = upperBody.addOrReplaceChild(
                "boom",
                CubeListBuilder.create()
                        // Main lower boom heavy arm
                        .texOffs(0, 80).addBox(-3.5F, -4.0F, 0.0F, 7.0F, 8.0F, 32.0F)
                        // Upper curved boom section
                        .texOffs(46, 80).addBox(-3.0F, -3.5F, 30.0F, 6.0F, 7.0F, 26.0F)
                        // Hydraulic boom lift cylinder (mounted underneath)
                        .texOffs(76, 110).addBox(-2.0F, 4.0F, 8.0F, 4.0F, 4.0F, 24.0F),
                PartPose.offset(5.5F, -6.0F, 5.0F)
        );

        // ── 4. Stick (Dipper Arm, pivots at boom tip) ───────────────────
        PartDefinition stick = boom.addOrReplaceChild(
                "stick",
                CubeListBuilder.create()
                        // Stick beam
                        .texOffs(0, 120).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 38.0F)
                        // Stick hydraulic cylinder (mounted on top)
                        .texOffs(48, 115).addBox(-1.5F, -6.5F, 6.0F, 3.0F, 4.0F, 20.0F),
                PartPose.offset(0.0F, 0.0F, 56.0F)
        );

        // ── 5. Bucket (Backhoe Scoop hanging underneath, opening towards cab) ──
        PartDefinition bucket = stick.addOrReplaceChild(
                "bucket",
                CubeListBuilder.create()
                        // Bucket top pivot linkage
                        .texOffs(90, 86).addBox(-5.0F, -3.0F, -3.0F, 10.0F, 6.0F, 6.0F)
                        // Bucket outer curved back shell (facing forward/away from cab)
                        .texOffs(90, 94).addBox(-6.0F, -2.0F, 0.0F, 12.0F, 8.0F, 3.0F)
                        // Bucket heel plate (bottom curve)
                        .texOffs(90, 94).addBox(-6.0F, 6.0F, -4.0F, 12.0F, 3.0F, 7.0F)
                        // Left cheek plate (facing inward toward cab)
                        .texOffs(74, 98).addBox(-6.0F, 0.0F, -12.0F, 2.0F, 8.0F, 14.0F)
                        // Right cheek plate (facing inward toward cab)
                        .texOffs(74, 98).addBox(4.0F, 0.0F, -12.0F, 2.0F, 8.0F, 14.0F)
                        // Bottom floor plate
                        .texOffs(90, 108).addBox(-6.0F, 6.0F, -12.0F, 12.0F, 2.0F, 10.0F)
                        // Cutting edge base plate
                        .texOffs(90, 114).addBox(-6.0F, 6.0F, -13.5F, 12.0F, 2.0F, 2.0F)
                        // 5 Hardened cutting teeth (pointing towards cab)
                        .texOffs(90, 119).addBox(-5.5F, 6.5F, -16.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(90, 119).addBox(-2.75F, 6.5F, -16.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(90, 119).addBox(0.0F, 6.5F, -16.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(90, 119).addBox(2.75F, 6.5F, -16.5F, 1.5F, 1.2F, 3.5F)
                        .texOffs(90, 119).addBox(5.0F, 6.5F, -16.5F, 1.5F, 1.2F, 3.5F),
                PartPose.offset(0.0F, 0.0F, 38.0F)
        );

        // Visual granular material sitting inside bucket cavity
        bucket.addOrReplaceChild(
                "bucket_contents",
                CubeListBuilder.create()
                        .texOffs(90, 123).addBox(-4.0F, 1.0F, -10.0F, 8.0F, 5.0F, 10.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(ExcavatorRenderState state) {
        super.setupAnim(state);

        // Turntable yaw rotation (relative to undercarriage)
        this.upperBody.yRot = (float) Math.toRadians(-state.upperYaw);

        // Hierarchical arm joint angles:
        // In model coordinates with inverted scale(-1, -1, 1), positive xRot pitches the arm UP in world space.
        this.boom.xRot = (float) Math.toRadians(state.boomAngle);
        this.stick.xRot = (float) Math.toRadians(state.stickAngle);
        this.bucket.xRot = (float) Math.toRadians(state.bucketAngle);

        // Visual fill level inside bucket cavity
        this.bucketContents.visible = state.fillRatio > 0.02F;
        this.bucketContents.yScale = Math.max(0.15F, state.fillRatio);
    }
}
