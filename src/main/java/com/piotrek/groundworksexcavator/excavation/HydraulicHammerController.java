package com.piotrek.groundworksexcavator.excavation;

import com.piotrek.groundworks.api.deposit.DepositResult;
import com.piotrek.groundworks.api.excavation.ExcavationResult;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import com.piotrek.groundworksexcavator.integration.groundworks.GroundworksExcavationAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Server-authoritative hydraulic breaker.
 *
 * <p>The breaker never stores material. Every successful impact removes exactly
 * one quarter-block of Groundworks cobblestone and immediately deposits that
 * volume beside the struck cell. The same path can therefore crush stone and
 * push already-crushed cobblestone without turning the hammer into a bucket.
 */
public final class HydraulicHammerController {

    public static final int UNITS_PER_IMPACT = GroundworksExcavationAdapter.UNITS_PER_BLOCK / 4;
    public static final int IMPACT_PERIOD_TICKS = 4;

    public record HammerTickResult(
            boolean active,
            boolean impact,
            int unitsCrushed,
            BlockPos source,
            BlockPos debris
    ) {
        public static final HammerTickResult INACTIVE =
                new HammerTickResult(false, false, 0, BlockPos.ZERO, BlockPos.ZERO);

        public static HammerTickResult activeNoImpact() {
            return new HammerTickResult(true, false, 0, BlockPos.ZERO, BlockPos.ZERO);
        }
    }

    private HydraulicHammerController() {}

    public static HammerTickResult tick(
            ServerLevel level,
            BucketPose hammerPose,
            boolean active,
            int tickCount
    ) {
        if (!active) {
            return HammerTickResult.INACTIVE;
        }

        if (Math.floorMod(tickCount, IMPACT_PERIOD_TICKS) != IMPACT_PERIOD_TICKS / 2) {
            return HammerTickResult.activeNoImpact();
        }

        Vec3 impactDirection = hammerPose.forwardCutting().normalize();
        Target target = findTarget(level, hammerPose.cuttingEdge(), impactDirection);
        if (target == null) {
            return HammerTickResult.activeNoImpact();
        }

        BlockPos source = target.pos();
        Vec3 probe = target.hitPoint();
        GranularMaterial material = target.material();

        ExcavationResult removed = GroundworksExcavationAdapter.excavateAt(
                level,
                probe,
                UNITS_PER_IMPACT,
                material
        );
        if (!removed.success()) {
            return HammerTickResult.activeNoImpact();
        }

        int remaining = removed.unitsRemoved();
        BlockPos firstDebris = BlockPos.ZERO;

        for (BlockPos candidate : debrisCandidates(source, impactDirection)) {
            if (remaining <= 0) break;

            DepositResult deposited = GroundworksExcavationAdapter.deposit(
                    level,
                    candidate,
                    removed.material(),
                    remaining
            );
            if (deposited.unitsDeposited() > 0) {
                if (firstDebris.equals(BlockPos.ZERO)) {
                    firstDebris = candidate.immutable();
                }
                remaining -= deposited.unitsDeposited();
            }
        }

        if (remaining > 0) {
            DepositResult restored = GroundworksExcavationAdapter.deposit(
                    level,
                    source,
                    removed.material(),
                    remaining
            );
            remaining -= restored.unitsDeposited();
        }

        if (remaining != 0) {
            throw new IllegalStateException(
                    "Hydraulic hammer lost granular material: removed="
                            + removed.unitsRemoved() + ", unresolved=" + remaining
            );
        }

        level.sendParticles(
                new BlockParticleOption(
                        ParticleTypes.BLOCK,
                        Blocks.COBBLESTONE.defaultBlockState()),
                probe.x, probe.y, probe.z,
                8,
                0.16D, 0.12D, 0.16D,
                0.08D
        );

        return new HammerTickResult(
                true,
                true,
                removed.unitsRemoved(),
                source.immutable(),
                firstDebris
        );
    }

    private static Target findTarget(
            ServerLevel level,
            Vec3 chiselTip,
            Vec3 impactDirection
    ) {
        // Probe a short distance beyond the visible maximum-stroke tip. This keeps
        // the logical contact anchored to the rendered chisel while avoiding missed
        // impacts at exact block boundaries and floating-point edge cases.
        for (int step = 0; step <= 3; step++) {
            Vec3 point = chiselTip.add(impactDirection.scale(step * 0.10D));
            BlockPos pos = BlockPos.containing(point);
            GranularMaterial material = GroundworksExcavationAdapter.getMaterial(level, pos);
            if (acceptsMaterial(material)) {
                return new Target(pos.immutable(), point, material);
            }
        }
        return null;
    }

    static boolean acceptsMaterial(GranularMaterial material) {
        return material != null
                && material.id() != 0
                && "cobblestone".equals(material.name());
    }

    static Direction outwardDirection(Vec3 impactDirection) {
        double x = -impactDirection.x;
        double y = -impactDirection.y;
        double z = -impactDirection.z;

        double ax = Math.abs(x);
        double ay = Math.abs(y);
        double az = Math.abs(z);

        if (ay >= ax && ay >= az) {
            return y >= 0.0D ? Direction.UP : Direction.DOWN;
        }
        if (ax >= az) {
            return x >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        return z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private record Target(BlockPos pos, Vec3 hitPoint, GranularMaterial material) {}

    private static Set<BlockPos> debrisCandidates(BlockPos source, Vec3 impactDirection) {
        Direction outward = outwardDirection(impactDirection);
        Set<BlockPos> candidates = new LinkedHashSet<>();
        candidates.add(source.relative(outward));
        candidates.add(source.above());
        candidates.add(source.north());
        candidates.add(source.south());
        candidates.add(source.east());
        candidates.add(source.west());
        candidates.add(source.below());
        return candidates;
    }
}
