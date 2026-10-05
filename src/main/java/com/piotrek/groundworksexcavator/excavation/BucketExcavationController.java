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
    private static final int MAX_SURFACE_PUSH_PER_CONTACT = 8;
    private static final int MAX_SURFACE_PUSH_PER_TICK = 32;
    private static final double MIN_SURFACE_PUSH_SPEED = 0.015D;
    private static final double SURFACE_PENETRATION_TOLERANCE = 0.125D;
    private static final double MAX_SURFACE_GAP = 0.18D;

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
            BucketPose currentPose,
            boolean preferIntake
    ) {
        SweptBucketVolume.SweptResult sweep = SweptBucketVolume.compute(previousPose, currentPose);
        if (!sweep.valid() || sweep.contacts().isEmpty()) {
            return ExcavationTickResult.NONE;
        }

        Vec3 movement = currentPose.cuttingEdge().subtract(previousPose.cuttingEdge());
        double horizontalSpeed = Math.hypot(movement.x, movement.z);
        int totalExcavated = 0;
        int totalDisplaced = 0;
        GranularMaterial lastMaterial = GranularMaterial.EMPTY;
        Vec3 lastHit = sweep.hitLocation();
        Set<BlockPos> processedBlocks = new HashSet<>();
        boolean isLarge = bucket.capacity() >= 512;
        int maxIntake = isLarge ? 128 : MAX_UNITS_PER_TICK;

        for (SweptBucketVolume.ToothContact contact : sweep.contacts()) {
            ContactTarget target = resolveContact(level, contact.worldPoint(), preferIntake);
            if (target == null || !processedBlocks.add(target.pos())) continue;

            if (target.surfaceSkim() && horizontalSpeed >= MIN_SURFACE_PUSH_SPEED
                    && totalDisplaced < MAX_SURFACE_PUSH_PER_TICK) {
                int requested = Math.min(
                        MAX_SURFACE_PUSH_PER_CONTACT,
                        MAX_SURFACE_PUSH_PER_TICK - totalDisplaced);
                GroundworksExcavationAdapter.SurfaceDisplacement displacement =
                        GroundworksExcavationAdapter.displaceSurface(
                                level,
                                target.pos(),
                                contact.worldPoint(),
                                surfacePushTarget(target.pos(), movement),
                                requested);
                if (displacement.unitsMoved() > 0) {
                    totalDisplaced += displacement.unitsMoved();
                    lastMaterial = displacement.material();
                    lastHit = contact.worldPoint();
                    spawnDigParticles(level, contact.worldPoint(), displacement.material());
                }
                continue;
            }

            if (target.surfaceSkim() || !bucket.hasRoom() || totalExcavated >= maxIntake) {
                continue;
            }

            int perContactMax = isLarge ? 64 : 32;
            int needed = Math.min(
                    bucket.remainingCapacity(),
                    Math.min(perContactMax, maxIntake - totalExcavated));
            if (needed <= 0) continue;

            GranularMaterial requiredMaterial = bucket.isEmpty()
                    ? GroundworksExcavationAdapter.getMaterial(level, target.pos())
                    : bucket.storedMaterial();
            if (requiredMaterial == GranularMaterial.EMPTY) {
                continue;
            }

            ExcavationResult result = GroundworksExcavationAdapter.excavateAt(
                    level,
                    contact.worldPoint(),
                    needed,
                    requiredMaterial
            );
            if (!result.success()) continue;

            if (result.material().id() != requiredMaterial.id()) {
                throw new IllegalStateException(
                        "Groundworks material filter violation: required="
                                + requiredMaterial.name()
                                + ", removed=" + result.material().name()
                );
            }

            int accepted = bucket.acceptMaterial(result.material(), result.unitsRemoved());
            if (accepted != result.unitsRemoved()) {
                throw new IllegalStateException(
                        "Bucket rejected filtered excavation: removed="
                                + result.unitsRemoved() + ", accepted=" + accepted
                );
            }

            totalExcavated += accepted;
            lastMaterial = result.material();
            lastHit = contact.worldPoint();
            spawnDigParticles(level, contact.worldPoint(), result.material());

            // Preserve the large bucket's deeper bite while keeping rejected units conserved.
            if (isLarge && bucket.hasRoom() && totalExcavated < maxIntake) {
                BlockPos below = target.pos().below();
                if (processedBlocks.add(below)
                        && GroundworksExcavationAdapter.isDiggable(level, below)) {
                    int extraNeeded = Math.min(
                            bucket.remainingCapacity(), Math.min(48, maxIntake - totalExcavated));
                    ExcavationResult extra = GroundworksExcavationAdapter.excavateAt(
                            level,
                            contact.worldPoint().subtract(0.0D, 0.5D, 0.0D),
                            extraNeeded,
                            bucket.storedMaterial()
                    );
                    if (extra.success()) {
                        if (extra.material().id() != bucket.storedMaterial().id()) {
                            throw new IllegalStateException(
                                    "Groundworks deep-bite material filter violation");
                        }

                        int extraAccepted = bucket.acceptMaterial(
                                extra.material(), extra.unitsRemoved());
                        if (extraAccepted != extra.unitsRemoved()) {
                            throw new IllegalStateException(
                                    "Bucket rejected filtered deep bite: removed="
                                            + extra.unitsRemoved() + ", accepted=" + extraAccepted
                            );
                        }
                        totalExcavated += extraAccepted;
                    }
                }
            }
        }

        if (totalExcavated > 0 || totalDisplaced > 0) {
            return new ExcavationTickResult(totalExcavated, lastMaterial, lastHit, true);
        }
        return ExcavationTickResult.NONE;
    }

    private static ContactTarget resolveContact(ServerLevel level, Vec3 point, boolean preferIntake) {
        BlockPos direct = BlockPos.containing(point);
        BlockPos[] candidates = { direct, direct.below() };
        for (BlockPos candidate : candidates) {
            if (!GroundworksExcavationAdapter.isDiggable(level, candidate)) continue;
            double surfaceY = GroundworksExcavationAdapter.getSurfaceWorldY(
                    level, candidate, point.x, point.z);
            double gap = point.y - surfaceY;
            if (isSurfaceSkim(gap)) {
                return new ContactTarget(candidate, !preferIntake);
            }
        }

        if (GroundworksExcavationAdapter.containsMaterialAt(level, point)
                && GroundworksExcavationAdapter.isDiggable(level, direct)) {
            return new ContactTarget(direct, false);
        }
        return null;
    }

    static boolean isSurfaceSkim(double pointMinusSurfaceY) {
        return pointMinusSurfaceY >= -SURFACE_PENETRATION_TOLERANCE
                && pointMinusSurfaceY <= MAX_SURFACE_GAP;
    }

    static BlockPos surfacePushTarget(BlockPos source, Vec3 movement) {
        double max = Math.max(Math.abs(movement.x), Math.abs(movement.z));
        if (max < MIN_SURFACE_PUSH_SPEED) return source;
        int dx = Math.abs(movement.x) >= max * 0.5D ? movement.x > 0.0D ? 1 : -1 : 0;
        int dz = Math.abs(movement.z) >= max * 0.5D ? movement.z > 0.0D ? 1 : -1 : 0;
        return source.offset(dx, 0, dz);
    }

    private record ContactTarget(BlockPos pos, boolean surfaceSkim) {}

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
