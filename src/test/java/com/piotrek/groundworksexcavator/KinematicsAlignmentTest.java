package com.piotrek.groundworksexcavator;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class KinematicsAlignmentTest {

    @Test
    @DisplayName("ArmKinematics cutting edge matches ModelPart rendering hierarchy with 0mm error")
    void testKinematicsMatchesModelPartRendering() {
        float[] testYaws = { 0.0f, 45.0f, 90.0f, 180.0f, -90.0f };
        float[] testUpperYaws = { 0.0f, 30.0f, -60.0f };

        for (float baseYaw : testYaws) {
            for (float upperYaw : testUpperYaws) {
                float boomAngle = 20.0f;
                float stickAngle = -40.0f;
                float bucketAngle = 10.0f;

                // 1. ArmKinematics computation
                Vec3 base = new Vec3(10.0D, 180.0D, 20.0D);
                BucketPose pose = ArmKinematics.computeBucketPose(
                        base, baseYaw, 0.0f, 0.0f, upperYaw, boomAngle, stickAngle, bucketAngle
                );
                Vec3 kinTooth = pose.teethPoints().get(2); // center tooth

                // 2. Exact ModelPart hierarchy matrix as rendered by ExcavatorRenderer
                Matrix4f renderMat = new Matrix4f();
                renderMat.translate((float) base.x, (float) base.y, (float) base.z);
                // stack.rotateDegrees(Axis.YP, -state.baseYaw);
                renderMat.rotate((float) Math.toRadians(-baseYaw), 0, 1, 0);
                // stack.scale(-1.0F, -1.0F, 1.0F);
                renderMat.scale(-1.0f, -1.0f, 1.0f);
                // stack.translate(0.0F, -1.5F, 0.0F);
                renderMat.translate(0.0f, -1.5f, 0.0f);

                // ModelPart upper_body: PartPose.offset(0.0F, 9.0F, 0.0F)
                renderMat.translate(0.0f / 16.0f, 9.0f / 16.0f, 0.0f / 16.0f);
                renderMat.rotate(new Quaternionf().rotationZYX(0, (float) Math.toRadians(upperYaw), 0));

                // ModelPart boom: PartPose.offset(5.5F, -6.0F, 5.0F)
                renderMat.translate(5.5f / 16.0f, -6.0f / 16.0f, 5.0f / 16.0f);
                renderMat.rotate(new Quaternionf().rotationZYX(0, 0, (float) Math.toRadians(boomAngle)));

                // ModelPart stick: PartPose.offset(0.0F, 0.0F, 56.0F)
                renderMat.translate(0.0f / 16.0f, 0.0f / 16.0f, 56.0f / 16.0f);
                renderMat.rotate(new Quaternionf().rotationZYX(0, 0, (float) Math.toRadians(stickAngle)));

                // ModelPart bucket: PartPose.offset(0.0F, 0.0F, 38.0F)
                renderMat.translate(0.0f / 16.0f, 0.0f / 16.0f, 38.0f / 16.0f);
                renderMat.rotate(new Quaternionf().rotationZYX(0, 0, (float) Math.toRadians(bucketAngle + 45.0f)));

                // Center tooth in bucket local coords (0.75, 9.1, -17.5 / 16)
                Vector4f toothRender = new Vector4f(0.75f / 16.0f, 9.1f / 16.0f, -17.5f / 16.0f, 1.0f);
                renderMat.transform(toothRender);

                assertEquals(toothRender.x, (float) kinTooth.x, 0.001f, "X match at yaw " + baseYaw);
                assertEquals(toothRender.y, (float) kinTooth.y, 0.001f, "Y match at yaw " + baseYaw);
                assertEquals(toothRender.z, (float) kinTooth.z, 0.001f, "Z match at yaw " + baseYaw);
            }
        }
    }
}
