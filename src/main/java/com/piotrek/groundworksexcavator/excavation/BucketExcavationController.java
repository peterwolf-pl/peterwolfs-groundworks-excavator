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

import java.util.HashSet;
import java.util.Set;

/**
 * Authoritative controller for terrain excavation using swept bucket kinematics.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Precise tooth-level crater excavation exactly at contact coordinates.</li>
 *   <li>Surface skimming detection: scraping the surface layer scoops soil immediately.</li>
 *   <li>Only excavates when bucket has free capacity.</li>
 *   <li>Strict material volume conservation.</li>
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
        if (!sweep.valid() || sweep.contacts().isEmpty()) {
            return ExcavationTickResult.NONE;
        }

        int totalExcavated = 0;
        GranularMaterial lastMaterial = GranularMaterial.EMPTY;
        Vec3 lastHit = sweep.hitLocation();
        Set<BlockPos> processedBlocks = new HashSet<>();
        boolean isLarge = bucket.capacity() >= 512;
        int maxIntake = isLarge ? 128 : MAX_UNITS_PER_TICK;

        for (SweptBucketVolume.ToothContact contact : sweep.contacts()) {
            if (bucket.remainingCapacity() <= 0 || totalExcavated >= maxIntake) {
                break;
            }

            BlockPos targetPos = resolveDiggableBlock(level, contact, isLarge);
            if (targetPos == null || !processedBlocks.add(targetPos)) {
                continue;
            }

            int perContactMax = isLarge ? 64 : 32;
            int needed = Math.min(bucket.remainingCapacity(), Math.min(perContactMax, maxIntake - totalExcavated));
            if (needed <= 0) {
                break;
            }

            // Call Groundworks spherical crater excavation at exact tooth contact coordinates
            ExcavationResult result = GroundworksExcavationAdapter.excavateAt(
                    level, targetPos, contact.worldPoint(), needed
            );

            if (result.success()) {
                int accepted = bucket.acceptMaterial(result.material(), result.unitsRemoved());
                if (accepted > 0) {
                    totalExcavated += accepted;
                    lastMaterial = result.material();
                    lastHit = contact.worldPoint();

                    // Visual digging particles directly at the tooth contact point
                    spawnDigParticles(level, contact.worldPoint(), result.material());
                }
            }

            // If large bucket is biting deep into ground, also take from block directly below
            if (isLarge && bucket.remainingCapacity() > 0 && totalExcavated < maxIntake) {
                BlockPos belowPos = targetPos.below();
                if (processedBlocks.add(belowPos) && GroundworksExcavationAdapter.isDiggable(level, belowPos)) {
                    int extraNeeded = Math.min(bucket.remainingCapacity(), Math.min(48, maxIntake - totalExcavated));
                    if (extraNeeded > 0) {
                        ExcavationResult extraResult = GroundworksExcavationAdapter.excavateAt(
                                level, belowPos, contact.worldPoint().subtract(0, 0.5, 0), extraNeeded
                        );
                        if (extraResult.success()) {
                            int extraAccepted = bucket.acceptMaterial(extraResult.material(), extraResult.unitsRemoved());
                            if (extraAccepted > 0) {
                                totalExcavated += extraAccepted;
                            }
                        }
                    }
                }
            }
        }

        if (totalExcavated > 0) {
            return new ExcavationTickResult(totalExcavated, lastMaterial, lastHit, true);
        }

        return ExcavationTickResult.NONE;
    }

    /**
     * Resolves the target diggable block position. If tooth is in air but skimming near a diggable soil block below, targets the block below.
     */
    private static BlockPos resolveDiggableBlock(ServerLevel level, SweptBucketVolume.ToothContact contact, boolean isLarge) {
        BlockPos pos = contact.pos();
        if (GroundworksExcavationAdapter.isDiggable(level, pos)) {
            return pos;
        }

        // Surface skimming check
        Vec3 pt = contact.worldPoint();
        double fractionalY = pt.y - Math.floor(pt.y);
        double maxSkim = isLarge ? 0.65D : 0.35D;
        if (fractionalY < maxSkim) {
            BlockPos below = pos.below();
            if (GroundworksExcavationAdapter.isDiggable(level, below)) {
                return below;
            }
        }

        return null;
    }

    private static void spawnDigParticles(ServerLevel level, Vec3 pos, GranularMaterial material) {
        var block = material.sourceBlock() != null ? material.sourceBlock() : Blocks.DIRT;
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState()),
                pos.x, pos.y, pos.z,
                4,
                0.12D, 0.08D, 0.12D,
                0.05D
        );
    }
}
