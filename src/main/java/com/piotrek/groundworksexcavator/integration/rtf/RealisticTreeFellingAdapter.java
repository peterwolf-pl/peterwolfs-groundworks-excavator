package com.piotrek.groundworksexcavator.integration.rtf;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service adapter providing directional tree felling integration with PeterWolf's
 * Realistic Tree Felling (MOD ID: {@code peterwolfs_realistic_tree_felling}).
 *
 * <p>When the mod is present, delegates directly to {@code TreeDetector} and
 * {@code TreeCutManager} to spawn animated, physics-enabled falling tree entities.
 * When absent, gracefully falls back to deterministic tree harvesting without crashing.
 */
public final class RealisticTreeFellingAdapter {

    public static final String MOD_ID = "peterwolfs_realistic_tree_felling";

    public record TreeInfo(
            BlockPos base,
            int height,
            Set<BlockPos> allBlocks,
            @Nullable Object handle
    ) {
        public boolean contains(BlockPos pos) {
            return allBlocks.contains(pos);
        }
    }

    private RealisticTreeFellingAdapter() {}

    public static boolean isModLoaded() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    /**
     * Detects a valid tree from any contacted trunk/branch log block.
     */
    public static @Nullable TreeInfo detectTree(ServerLevel level, BlockPos hitPos) {
        if (!level.getBlockState(hitPos).is(BlockTags.LOGS)) {
            return null;
        }

        if (isModLoaded()) {
            return RtfBridge.detect(level, hitPos);
        }
        return FallbackTreeHandler.detect(level, hitPos);
    }

    /**
     * Fells the tree in the specified horizontal direction.
     */
    public static boolean fellTree(
            ServerLevel level,
            @Nullable ServerPlayer player,
            TreeInfo tree,
            Direction direction
    ) {
        if (isModLoaded()) {
            return RtfBridge.fell(level, player, tree, direction);
        }
        return FallbackTreeHandler.fell(level, tree);
    }

    /**
     * Isolated bridge touching {@code com.peterwolf.realtreefelling.*}.
     * Loaded only when {@link #isModLoaded()} returns true.
     */
    private static final class RtfBridge {
        static @Nullable TreeInfo detect(ServerLevel level, BlockPos hitPos) {
            Optional<com.peterwolf.realtreefelling.detection.DetectedTree> detected =
                    com.peterwolf.realtreefelling.detection.TreeDetector.detect(level, hitPos);
            if (detected.isEmpty()) {
                return null;
            }

            com.peterwolf.realtreefelling.detection.DetectedTree dt = detected.get();
            Set<BlockPos> worldBlocks = new HashSet<>();
            for (com.peterwolf.realtreefelling.detection.DetectedTree.TreeBlockData b : dt.blocks()) {
                worldBlocks.add(dt.base().offset(b.relative()));
            }
            return new TreeInfo(dt.base(), dt.height(), worldBlocks, dt);
        }

        static boolean fell(
                ServerLevel level,
                @Nullable ServerPlayer player,
                TreeInfo tree,
                Direction direction
        ) {
            if (tree.handle() instanceof com.peterwolf.realtreefelling.detection.DetectedTree dt) {
                com.peterwolf.realtreefelling.cutting.TreeCutManager.startFallWithDirection(
                        level, player, dt, direction);
                return true;
            }
            return false;
        }
    }

    /**
     * Fallback tree detection and felling for headless tests or environments
     * where PeterWolf's Realistic Tree Felling is not installed.
     */
    private static final class FallbackTreeHandler {
        private static final int MAX_TREE_BLOCKS = 256;

        static @Nullable TreeInfo detect(ServerLevel level, BlockPos hitPos) {
            BlockPos current = hitPos;
            while (current.getY() > level.getMinY() && level.getBlockState(current.below()).is(BlockTags.LOGS)) {
                current = current.below();
            }
            BlockPos base = current;

            Set<BlockPos> logs = new HashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(base);
            logs.add(base);

            int highestY = base.getY();

            while (!queue.isEmpty() && logs.size() < MAX_TREE_BLOCKS) {
                BlockPos p = queue.poll();
                highestY = Math.max(highestY, p.getY());

                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = p.relative(dir);
                    if (!logs.contains(neighbor) && level.getBlockState(neighbor).is(BlockTags.LOGS)) {
                        logs.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }

            if (logs.size() < 2) {
                return null;
            }

            int height = highestY - base.getY() + 1;
            return new TreeInfo(base, height, logs, null);
        }

        static boolean fell(ServerLevel level, TreeInfo tree) {
            for (BlockPos p : tree.allBlocks()) {
                if (!p.equals(tree.base())) {
                    level.destroyBlock(p, true);
                }
            }
            level.playSound(null, tree.base(), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.5F, 0.8F);
            return true;
        }
    }
}
