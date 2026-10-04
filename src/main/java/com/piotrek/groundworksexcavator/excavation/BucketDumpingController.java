package com.piotrek.groundworksexcavator.excavation;

import com.piotrek.groundworks.api.deposit.DepositResult;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import com.piotrek.groundworksexcavator.integration.groundworks.GroundworksExcavationAdapter;
import com.piotrek.groundworksexcavator.material.BucketMaterialContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Authoritative controller for pouring granular material out of the bucket into terrain.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Only pours when bucket tilt passes the dump threshold.</li>
 *   <li>Gradual flow rate based on dump angle (8..32 units/tick).</li>
 *   <li>Material conservation: units deposited to terrain == units subtracted from bucket.</li>
 *   <li>Rejected units stay in the bucket (zero material destruction).</li>
 *   <li>Material exits at the bucket lip.</li>
 * </ul>
 */
public final class BucketDumpingController {

    public static final int MIN_FLOW_RATE = 8;
    public static final int MAX_FLOW_RATE = 32;

    public record DumpTickResult(
            int unitsDeposited,
            GranularMaterial material,
            Vec3 lipLocation,
            boolean dumping
    ) {
        public static final DumpTickResult NONE = new DumpTickResult(
                0, GranularMaterial.EMPTY, Vec3.ZERO, false);
    }

    private BucketDumpingController() {}

    /**
     * Executes dumping simulation for one server tick.
     */
    public static DumpTickResult tick(
            ServerLevel level,
            BucketMaterialContainer bucket,
            BucketPose currentPose
    ) {
        if (bucket.isEmpty() || currentPose == null) {
            return DumpTickResult.NONE;
        }

        float tilt = currentPose.dumpTiltDegrees();
        if (tilt < ArmKinematics.DUMP_THRESHOLD_DEG) {
            // Bucket is upright or level, material stays inside
            return DumpTickResult.NONE;
        }

        // Calculate flow rate based on how steep the bucket is tilted
        // At 30 deg -> 8 units/tick, at 75+ deg -> 32 units/tick
        float progress = Mth.clamp((tilt - ArmKinematics.DUMP_THRESHOLD_DEG) / 45.0F, 0.0F, 1.0F);
        int flowRate = Math.round(Mth.lerp(progress, (float) MIN_FLOW_RATE, (float) MAX_FLOW_RATE));
        int toDump = Math.min(bucket.storedUnits(), flowRate);

        if (toDump <= 0) {
            return DumpTickResult.NONE;
        }

        Vec3 lip = currentPose.lip();
        BlockPos depositPos = BlockPos.containing(lip.x, lip.y, lip.z);
        GranularMaterial material = bucket.storedMaterial();

        DepositResult result = GroundworksExcavationAdapter.deposit(level, depositPos, material, toDump);

        if (result.success()) {
            // Strictly extract only the units that Groundworks actually stored in terrain
            int extracted = bucket.extractMaterial(result.unitsDeposited());

            // Visual falling material stream
            spawnDumpParticles(level, lip, material, flowRate);

            return new DumpTickResult(extracted, material, lip, true);
        }

        return DumpTickResult.NONE;
    }

    private static void spawnDumpParticles(
            ServerLevel level, Vec3 lip, GranularMaterial material, int flowRate) {
        var block = material.sourceBlock() != null ? material.sourceBlock() : Blocks.DIRT;
        int count = Math.max(3, flowRate / 6);
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState()),
                lip.x, lip.y - 0.1D, lip.z,
                count,
                0.08D, 0.05D, 0.08D,
                0.08D
        );
    }
}
