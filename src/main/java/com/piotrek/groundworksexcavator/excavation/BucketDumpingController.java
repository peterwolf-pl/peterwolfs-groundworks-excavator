package com.piotrek.groundworksexcavator.excavation;

import com.piotrek.groundworks.api.deposit.DepositResult;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.terrain.cell.GranularCell;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Authoritative controller for pouring granular material out of the bucket into terrain.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Only pours when bucket tilt passes the dump threshold.</li>
 *   <li>Gradual flow rate based on dump angle (8..32 units/tick).</li>
 *   <li>Deposits into the terrain surface directly underneath the bucket lip via gravity search.</li>
 *   <li>Material conservation: units deposited to terrain == units subtracted from bucket.</li>
 *   <li>Rejected units stay in the bucket (zero material destruction).</li>
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
        GranularMaterial material = bucket.storedMaterial();

        // Find the exact ground or pile surface directly below the bucket lip via gravity raycast
        BlockPos targetPos = findDepositSurface(level, lip, material);
        if (targetPos == null) {
            return DumpTickResult.NONE;
        }

        DepositResult result = GroundworksExcavationAdapter.deposit(level, targetPos, material, toDump);

        if (result.success()) {
            // Strictly extract only the units that Groundworks actually stored in terrain
            int extracted = bucket.extractMaterial(result.unitsDeposited());

            // Visual falling material stream from bucket lip down to ground target
            spawnFallingStreamParticles(level, lip, targetPos, material, flowRate);

            return new DumpTickResult(extracted, material, lip, true);
        }

        return DumpTickResult.NONE;
    }

    @Nullable
    private static BlockPos findDepositSurface(ServerLevel level, Vec3 lip, GranularMaterial material) {
        BlockPos start = BlockPos.containing(lip.x, lip.y, lip.z);

        // 1. If lip is directly submerged in or touching an existing cell of the same material
        GranularCell cellAtLip = GroundworksExcavationAdapter.queryCell(level, start);
        if (cellAtLip != null && (cellAtLip.isEmpty() || cellAtLip.materialId() == material.id())) {
            return start;
        }

        // 2. Gravity raycast straight down from the lip to find the receiving ground/pile surface
        int lipY = start.getY();
        int minY = Math.max(level.getMinY(), lipY - 14);

        for (int y = lipY; y >= minY; y--) {
            BlockPos checkPos = new BlockPos(start.getX(), y, start.getZ());
            GranularCell cell = GroundworksExcavationAdapter.queryCell(level, checkPos);
            if (cell != null) {
                if (cell.isEmpty() || cell.materialId() == material.id()) {
                    if (cell.unitCount() < 512) {
                        return checkPos; // Existing cell with room
                    } else {
                        return checkPos.above(); // Cell is full, pile upward
                    }
                }
            }

            BlockState state = level.getBlockState(checkPos);
            if (!state.isAir()) {
                // Found ground surface (solid block or convertible soil)
                return checkPos.above();
            }
        }

        return null;
    }

    private static void spawnFallingStreamParticles(
            ServerLevel level, Vec3 lip, BlockPos targetPos, GranularMaterial material, int flowRate
    ) {
        var block = material.sourceBlock() != null ? material.sourceBlock() : Blocks.DIRT;
        BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState());

        int count = Math.max(4, flowRate / 4);
        double targetY = targetPos.getY() + 0.1D;
        double fallDistance = Math.max(0.1D, lip.y - targetY);

        for (int i = 0; i < count; i++) {
            double fraction = level.getRandom().nextDouble();
            double py = lip.y - fraction * fallDistance;
            double px = lip.x + (level.getRandom().nextDouble() - 0.5D) * 0.20D;
            double pz = lip.z + (level.getRandom().nextDouble() - 0.5D) * 0.20D;

            level.sendParticles(particle, px, py, pz, 1, 0.02D, -0.20D, 0.02D, 0.05D);
        }
    }
}
