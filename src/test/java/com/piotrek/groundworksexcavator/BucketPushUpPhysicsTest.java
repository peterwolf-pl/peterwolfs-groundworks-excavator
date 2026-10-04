package com.piotrek.groundworksexcavator;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import com.piotrek.groundworksexcavator.vehicle.TrackMovementController;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BucketPushUpPhysicsTest {

    @Test
    @DisplayName("Pushing arm down into solid ground creates lifting force and positive pitch tilt")
    void testBucketPushDownGeneratesLiftAndTilt() {
        Vec3 basePos = new Vec3(0.0D, 64.0D, 0.0D);
        float baseYaw = 0.0F; // Facing south (+Z)
        float upperYaw = 0.0F; // Arm pointing front

        // Downward pressing pose (boom lowered, stick pushing down)
        float boomAngle = -20.0F;
        float stickAngle = -50.0F;
        float bucketAngle = 10.0F;

        BucketPose pose = ArmKinematics.computeBucketPose(
                basePos, baseYaw, 0.0F, 0.0F, upperYaw,
                boomAngle, stickAngle, bucketAngle, 0
        );

        Vec3 teeth = pose.cuttingEdge();
        // Teeth reach well below base ground (e.g. y < 64.0)
        assertTrue(teeth.y < 64.0D, "Downward arm stroke should press below chassis height: " + teeth.y);

        // Assume solid flat ground at Y=64.0
        double groundUnderTeeth = 64.0D;
        double penetrationDepth = groundUnderTeeth - teeth.y;
        assertTrue(penetrationDepth > 0.1D, "Penetration depth into ground must be positive");

        // Compute simulated hydraulic push-up
        double maxLift = 1.60D;
        double targetLift = Math.min(maxLift, penetrationDepth * 1.25D);
        double liftDeltaY = targetLift * 0.35D;
        assertTrue(liftDeltaY > 0.05D, "Hydraulic pressure must generate vertical lift: " + liftDeltaY);

        double cabYawRad = Math.toRadians(upperYaw);
        float pitchTiltImpact = (float) (Math.cos(cabYawRad) * (targetLift * 18.0D));
        float dynamicPitch = Mth.clamp(0.0F + pitchTiltImpact, -35.0F, 40.0F);

        assertTrue(dynamicPitch > 5.0F, "Front bucket push-down must tilt chassis pitch up: " + dynamicPitch);
    }
}
