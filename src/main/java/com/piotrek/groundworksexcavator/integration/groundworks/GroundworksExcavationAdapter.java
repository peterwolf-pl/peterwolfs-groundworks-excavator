package com.piotrek.groundworksexcavator.integration.groundworks;

import com.piotrek.groundworks.api.GroundworksApi;
import com.piotrek.groundworks.api.deposit.DepositResult;
import com.piotrek.groundworks.api.excavation.ExcavationApi;
import com.piotrek.groundworks.api.excavation.ExcavationResult;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.terrain.cell.GranularCell;
import com.piotrek.groundworks.terrain.conversion.BlockConverter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Dedicated service boundary between the Excavator mod and Peterwolf's Groundworks.
 *
 * <p>All terrain removal, material queries, and deposition go through this adapter.
 * The excavator mod never directly touches internal terrain storage or duplicates state.
 */
public final class GroundworksExcavationAdapter {

    public static final int UNITS_PER_BLOCK = 512;
    private static final int MICRO_RESOLUTION = 8;

    public record SurfaceDisplacement(int unitsMoved, GranularMaterial material) {
        public static final SurfaceDisplacement NONE =
                new SurfaceDisplacement(0, GranularMaterial.EMPTY);
    }

    private GroundworksExcavationAdapter() {}

    /**
     * Checks if a block position contains convertible or existing granular terrain.
     */
    public static boolean isDiggable(ServerLevel level, BlockPos pos) {
        GranularCell cell = GroundworksApi.queryCell(level, pos);
        if (cell != null && !cell.isEmpty()) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        return BlockConverter.isConvertible(state);
    }

    /**
     * Excavates up to {@code maxUnits} at a specific world hit point.
     * Uses Groundworks brush removal so the crater forms at the exact bucket teeth contact point.
     */
    public static ExcavationResult excavateAt(
            ServerLevel level, BlockPos pos, Vec3 hitLocation, int maxUnits) {
        if (maxUnits <= 0) {
            return ExcavationResult.NONE;
        }
        return ExcavationApi.excavateAt(level, pos, hitLocation, maxUnits);
    }

    /**
     * Excavates up to {@code maxUnits} from a cell, removing from top microvoxels.
     */
    public static ExcavationResult excavate(ServerLevel level, BlockPos pos, int maxUnits) {
        if (maxUnits <= 0) {
            return ExcavationResult.NONE;
        }
        return GroundworksApi.excavate(level, pos, maxUnits);
    }

    /**
     * Deposits granular material into the terrain at the given position with upward overflow.
     */
    public static DepositResult deposit(
            ServerLevel level, BlockPos pos, GranularMaterial material, int units) {
        if (units <= 0 || material == null || material.id() == 0) {
            return DepositResult.NONE;
        }
        return GroundworksApi.depositWithOverflow(level, pos, material, units);
    }

    /**
     * Queries the top surface microvoxel Y (0..7) in a granular cell.
     * Returns -1 if empty or not a granular cell.
     */
    public static int getSurfaceHeight(ServerLevel level, BlockPos pos, int localX, int localZ) {
        return GroundworksApi.getSurfaceHeight(level, pos, localX, localZ);
    }

    /**
     * Queries the cell at the given position, if converted.
     */
    @Nullable
    public static GranularCell queryCell(ServerLevel level, BlockPos pos) {
        return GroundworksApi.queryCell(level, pos);
    }

    /** Returns true only when the exact world point is inside granular material. */
    public static boolean containsMaterialAt(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        GranularCell cell = GroundworksApi.queryCell(level, pos);
        if (cell != null) {
            int x = microCoordinate(point.x - pos.getX());
            int y = microCoordinate(point.y - pos.getY());
            int z = microCoordinate(point.z - pos.getZ());
            return cell.isSet(x, y, z);
        }
        return BlockConverter.isConvertible(level.getBlockState(pos));
    }

    /** Returns the exact column surface Y, or negative infinity if no material exists. */
    public static double getSurfaceWorldY(
            ServerLevel level, BlockPos pos, double worldX, double worldZ) {
        GranularCell cell = GroundworksApi.queryCell(level, pos);
        if (cell != null) {
            int x = microCoordinate(worldX - pos.getX());
            int z = microCoordinate(worldZ - pos.getZ());
            int top = cell.getColumnHeight(x, z);
            return top < 0
                    ? Double.NEGATIVE_INFINITY
                    : pos.getY() + (top + 1) / (double) MICRO_RESOLUTION;
        }
        return BlockConverter.isConvertible(level.getBlockState(pos))
                ? pos.getY() + 1.0D
                : Double.NEGATIVE_INFINITY;
    }

    /**
     * Moves surface material forward without putting it in the bucket.
     * Any rejected destination volume is restored to the source cell.
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

        ExcavationResult removed = excavateAt(level, source, hitLocation, maxUnits);
        if (!removed.success() || removed.unitsRemoved() <= 0) {
            return SurfaceDisplacement.NONE;
        }

        DepositResult deposited = deposit(
                level, destination, removed.material(), removed.unitsRemoved());
        int rejected = removed.unitsRemoved() - deposited.unitsDeposited();
        if (rejected > 0) {
            DepositResult restored = GroundworksApi.depositWithOverflow(
                    level, source, removed.material(), rejected);
            if (restored.unitsDeposited() != rejected) {
                throw new IllegalStateException(
                        "Surface displacement lost material: removed=" + removed.unitsRemoved()
                                + ", deposited=" + deposited.unitsDeposited()
                                + ", restored=" + restored.unitsDeposited());
            }
        }

        return new SurfaceDisplacement(deposited.unitsDeposited(), removed.material());
    }

    private static int microCoordinate(double localCoordinate) {
        return Math.max(0, Math.min(
                MICRO_RESOLUTION - 1,
                (int) Math.floor(localCoordinate * MICRO_RESOLUTION)
        ));
    }

    /**
     * Converts Groundworks integer units to displayed cubic meters (e.g. 256 units = 0.500 m³).
     */
    public static double unitsToCubicMeters(int units) {
        return (double) units / (double) UNITS_PER_BLOCK;
    }
}
