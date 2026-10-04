package com.piotrek.groundworksexcavator.excavation;

import com.piotrek.groundworks.api.excavation.ExcavationResult;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import com.piotrek.groundworksexcavator.integration.groundworks.GroundworksExcavationAdapter;
import com.piotrek.groundworksexcavator.material.BucketMaterialContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Authoritative controller for terrain excavation using swept bucket kinematics.
 *
 * <p>Strictly enforces:
 * <ul>
 *   <li>Only excavates when bucket has free capacity.</li>
 *   <li>Only excavates when bucket is moving in a cutting direction.</li>
 *   <li>Material conservation: units excavated from terrain == units added to bucket.</li>
 *   <li>Zero phantom block deletion.</li>
 * </ul>
 */
public final class BucketExcavationController {

    public static final int MAX_UNITS_PER_TICK = 32;

    public record ExcavationTickResult(
            int unitsExcavated,
            GranularMaterial material,
            Vec3 hitLocation,
            boolean excavated
    ) {
        public static final ExcavationTickResult NONE = new ExcavationTickResult(
                0, GranularMaterial.EMPTY, Vec3.ZERO, false);
    }

    private BucketExcavationController() {}

    /**
     * Executes excavation simulation for one server tick.
     */
    public static ExcavationTickResult tick(
            ServerLevel level,
            BucketMaterialContainer bucket,
            BucketPose previousPose,
            BucketPose currentPose
    ) {
        if (!bucket.hasRoom()) {
            return ExcavationTickResult.NONE;
        }

        SweptBucketVolume.SweptResult sweep = SweptBucketVolume.compute(previousPose, currentPose);
        if (!sweep.valid() || sweep.hitPositions().isEmpty()) {
            return ExcavationTickResult.NONE;
        }

        int totalExcavated = 0;
        GranularMaterial lastMaterial = GranularMaterial.EMPTY;
        Vec3 lastHit = sweep.hitLocation();

        for (BlockPos pos : sweep.hitPositions()) {
            if (bucket.remainingCapacity() <= 0) {
                break;
            }

            if (!GroundworksExcavationAdapter.isDiggable(level, pos)) {
                continue;
            }

            int needed = Math.min(bucket.remainingCapacity(), MAX_UNITS_PER_TICK - totalExcavated);
            if (needed <= 0) {
                break;
            }

            ExcavationResult result = GroundworksExcavationAdapter.excavateAt(
                    level, pos, sweep.hitLocation(), needed
            );

            if (result.success()) {
                // Strict conservation: only add what Groundworks actually removed
                int accepted = bucket.acceptMaterial(result.material(), result.unitsRemoved());
                if (accepted > 0) {
                    totalExcavated += accepted;
                    lastMaterial = result.material();
                    lastHit = sweep.hitLocation();

                    // Visual digging particles
                    spawnDigParticles(level, lastHit, result.material());
                }
            }
        }

        if (totalExcavated > 0) {
            return new ExcavationTickResult(totalExcavated, lastMaterial, lastHit, true);
        }

        return ExcavationTickResult.NONE;
    }

    private static void spawnDigParticles(ServerLevel level, Vec3 pos, GranularMaterial material) {
        var block = material.sourceBlock() != null ? material.sourceBlock() : Blocks.DIRT;
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState()),
                pos.x, pos.y, pos.z,
                4,
                0.15D, 0.1D, 0.15D,
                0.05D
        );
    }
}
