package com.piotrek.groundworksexcavator.integration.groundworks;

import com.piotrek.groundworks.api.GroundworksApi;
import com.piotrek.groundworks.api.deposit.DepositResult;
import com.piotrek.groundworks.api.excavation.ExcavationResult;
import com.piotrek.groundworks.api.material.GranularMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Thin service boundary between the Excavator mod and Peterwolf's Groundworks.
 *
 * <p>All world mutation, lazy conversion, synchronization, dirty flags, and
 * simulation scheduling are owned by the public Groundworks API.
 */
public final class GroundworksExcavationAdapter {

    public static final int UNITS_PER_BLOCK = 512;

    public record SurfaceDisplacement(int unitsMoved, GranularMaterial material) {
        public static final SurfaceDisplacement NONE =
                new SurfaceDisplacement(0, GranularMaterial.EMPTY);
    }

    private GroundworksExcavationAdapter() {}

    public static boolean isDiggable(ServerLevel level, BlockPos pos) {
        return GroundworksApi.isDiggable(level, pos);
    }

    public static GranularMaterial getMaterial(ServerLevel level, BlockPos pos) {
        GranularMaterial material = GroundworksApi.getMaterial(level, pos);
        return material != null ? material : GranularMaterial.EMPTY;
    }

    /**
     * World-space excavation centered at the exact bucket contact point.
     * The Groundworks brush may cross block boundaries.
     */
    public static ExcavationResult excavateAt(
            ServerLevel level,
            Vec3 hitLocation,
            int maxUnits
    ) {
        if (maxUnits <= 0) {
            return ExcavationResult.NONE;
        }
        return GroundworksApi.excavateAt(level, hitLocation, maxUnits);
    }

    public static ExcavationResult excavate(ServerLevel level, BlockPos pos, int maxUnits) {
        if (maxUnits <= 0) {
            return ExcavationResult.NONE;
        }
        return GroundworksApi.excavate(level, pos, maxUnits);
    }

    public static DepositResult deposit(
            ServerLevel level,
            BlockPos pos,
            GranularMaterial material,
            int units
    ) {
        if (units <= 0 || material == null || material.id() == 0) {
            return DepositResult.NONE;
        }
        return GroundworksApi.depositWithOverflow(level, pos, material, units);
    }

    public static boolean containsMaterialAt(ServerLevel level, Vec3 point) {
        return GroundworksApi.containsMaterialAt(level, point);
    }

    public static double getSurfaceWorldY(
            ServerLevel level,
            BlockPos pos,
            double worldX,
            double worldZ
    ) {
        return GroundworksApi.getSurfaceWorldY(level, pos, worldX, worldZ);
    }

    /**
     * Moves surface material forward without putting it in the bucket.
     * Rejected destination volume is restored to the source column.
     */
    public static SurfaceDisplacement displaceSurface(
            ServerLevel level,
            BlockPos source,
            Vec3 hitLocation,
            BlockPos destination,
            int maxUnits
    ) {
        if (maxUnits <= 0 || source.equals(destination)) {
            return SurfaceDisplacement.NONE;
        }

        ExcavationResult removed = excavateAt(level, hitLocation, maxUnits);
        if (!removed.success()) {
            return SurfaceDisplacement.NONE;
        }

        DepositResult deposited = deposit(
                level,
                destination,
                removed.material(),
                removed.unitsRemoved()
        );

        int rejected = removed.unitsRemoved() - deposited.unitsDeposited();
        if (rejected > 0) {
            DepositResult restored = GroundworksApi.depositWithOverflow(
                    level,
                    source,
                    removed.material(),
                    rejected
            );
            if (restored.unitsDeposited() != rejected) {
                throw new IllegalStateException(
                        "Surface displacement lost material: removed=" + removed.unitsRemoved()
                                + ", deposited=" + deposited.unitsDeposited()
                                + ", restored=" + restored.unitsDeposited()
                );
            }
        }

        return new SurfaceDisplacement(deposited.unitsDeposited(), removed.material());
    }

    public static double unitsToCubicMeters(int units) {
        return (double) units / (double) UNITS_PER_BLOCK;
    }
}
