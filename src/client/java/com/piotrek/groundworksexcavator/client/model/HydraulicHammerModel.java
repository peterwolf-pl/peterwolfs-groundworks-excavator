package com.piotrek.groundworksexcavator.client.model;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import com.piotrek.groundworksexcavator.client.render.ExcavatorRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Interchangeable hydraulic hammer rendered at the excavator bucket joint.
 */
public class HydraulicHammerModel extends EntityModel<ExcavatorRenderState> {

    private final ModelPart chisel;

    public HydraulicHammerModel(ModelPart root) {
        super(root);
        this.chisel = root.getChild("housing").getChild("chisel");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition housing = root.addOrReplaceChild(
                "housing",
                CubeListBuilder.create()
                        .texOffs(32, 0).addBox(-6.0F, -3.0F, -8.0F, 12.0F, 6.0F, 5.0F)
                        .texOffs(0, 0).addBox(-5.0F, -5.0F, -28.0F, 10.0F, 10.0F, 22.0F)
                        .texOffs(0, 24).addBox(-3.0F, -3.0F, -31.0F, 6.0F, 6.0F, 5.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        housing.addOrReplaceChild(
                "chisel",
                CubeListBuilder.create()
                        .texOffs(24, 24).addBox(-1.5F, -1.5F, -10.0F, 3.0F, 3.0F, 10.0F),
                PartPose.offset(0.0F, 0.0F, ArmKinematics.HAMMER_CHISEL_BASE_Z_PX)
        );

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(ExcavatorRenderState state) {
        super.setupAnim(state);
        this.chisel.z = ArmKinematics.HAMMER_CHISEL_BASE_Z_PX
                - state.hammerStroke * ArmKinematics.HAMMER_STROKE_PX;
    }
}
