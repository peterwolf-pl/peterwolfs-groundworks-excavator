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

import java.util.List;

/**
 * Dedicated service boundary between the Excavator mod and Peterwolf's Groundworks.
 *
 * <p>All terrain removal, material queries, and deposition go through this adapter.
 * The excavator mod never directly touches internal terrain storage or duplicates state.
 */
public final class GroundworksExcavationAdapter {

    public static final int UNITS_PER_BLOCK = 512;

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

    /**
     * Converts Groundworks integer units to displayed cubic meters (e.g. 256 units = 0.500 m³).
     */
    public static double unitsToCubicMeters(int units) {
        return (double) units / (double) UNITS_PER_BLOCK;
    }
}
