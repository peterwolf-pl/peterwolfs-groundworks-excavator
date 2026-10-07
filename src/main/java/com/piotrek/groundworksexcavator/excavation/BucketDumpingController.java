package com.piotrek.groundworksexcavator.excavation;

import com.piotrek.groundworks.api.container.GranularContainerTransferApi;
import com.piotrek.groundworks.api.container.IWorldGranularContainer;
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
import net.minecraft.world.entity.Entity;
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
        return tick(level, null, bucket, currentPose);
    }

    /**
     * Executes dumping with a source entity excluded from receiver lookup.
     */
    public static DumpTickResult tick(
            ServerLevel level,
            @Nullable Entity source,
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
        // Standard: 8..32 units/tick. Large (512u): 24..96 units/tick
        int minFlow = (bucket.capacity() >= 512) ? MIN_FLOW_RATE * 3 : MIN_FLOW_RATE;
        int maxFlow = (bucket.capacity() >= 512) ? MAX_FLOW_RATE * 3 : MAX_FLOW_RATE;
        float progress = Mth.clamp((tilt - ArmKinematics.DUMP_THRESHOLD_DEG) / 45.0F, 0.0F, 1.0F);
        int flowRate = Math.round(Mth.lerp(progress, (float) minFlow, (float) maxFlow));
        int toDump = Math.min(bucket.storedUnits(), flowRate);

        if (toDump <= 0) {
            return DumpTickResult.NONE;
        }

        Vec3 lip = currentPose.lip();
        GranularMaterial material = bucket.storedMaterial();

        // Prefer a physical Groundworks container under the bucket lip.
        // The receiver owns its overflow policy. The dump truck fills its
        // 10-block body first and then spills excess to both sides.
        IWorldGranularContainer receiver =
                GranularContainerTransferApi.findReceiver(
                        level,
                        lip,
                        source,
                        4.0D
                );

        if (receiver != null) {
            int consumed = receiver.receiveMaterialAt(
                    level,
                    lip,
                    material,
                    toDump
            );

            consumed = Math.clamp(consumed, 0, toDump);
            if (consumed > 0) {
                int extracted = bucket.extractMaterial(consumed);
                spawnContainerTransferParticles(
                        level,
                        lip,
                        material,
                        extracted
                );
                return new DumpTickResult(
                        extracted,
                        material,
                        lip,
                        true
                );
            }

            // Receiver was physically hit but could not consume the material.
            // Keep it in the bucket instead of falling through to terrain.
            return DumpTickResult.NONE;
        }

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

    /**
     * Dumps only into a receiver that was already validated at a stable machine
     * check point. The actual bucket lip may move while the bucket opens, so the
     * receiver geometry must not be re-qualified from the moving lip every tick.
     *
     * <p>No terrain fallback is allowed here. If the receiver disappears or
     * rejects material, the load stays in the bucket.</p>
     */
    public static DumpTickResult tickToReceiver(
            ServerLevel level,
            @Nullable Entity source,
            BucketMaterialContainer bucket,
            BucketPose currentPose,
            IWorldGranularContainer receiver
    ) {
        return tickToReceiver(level, source, bucket, currentPose, receiver, true);
    }

    /**
     * Fleet AutoTrench transfer. Material is capped to the receiver's remaining
     * capacity, so a nearly full truck is topped off without spilling the rest.
     * Any material left in the bucket can then be carried to the next truck.
     */
    public static DumpTickResult tickToReceiverWithoutOverflow(
            ServerLevel level,
            @Nullable Entity source,
            BucketMaterialContainer bucket,
            BucketPose currentPose,
            IWorldGranularContainer receiver
    ) {
        return tickToReceiver(level, source, bucket, currentPose, receiver, false);
    }

    private static DumpTickResult tickToReceiver(
            ServerLevel level,
            @Nullable Entity source,
            BucketMaterialContainer bucket,
            BucketPose currentPose,
            IWorldGranularContainer receiver,
            boolean allowReceiverOverflow
    ) {
        if (receiver == null || bucket.isEmpty() || currentPose == null) {
            return DumpTickResult.NONE;
        }

        float tilt = currentPose.dumpTiltDegrees();
        if (tilt < ArmKinematics.DUMP_THRESHOLD_DEG) {
            return DumpTickResult.NONE;
        }

        int minFlow = (bucket.capacity() >= 512) ? MIN_FLOW_RATE * 3 : MIN_FLOW_RATE;
        int maxFlow = (bucket.capacity() >= 512) ? MAX_FLOW_RATE * 3 : MAX_FLOW_RATE;
        float progress = Mth.clamp(
                (tilt - ArmKinematics.DUMP_THRESHOLD_DEG) / 45.0F,
                0.0F,
                1.0F
        );
        int flowRate = Math.round(Mth.lerp(progress, (float) minFlow, (float) maxFlow));
        int toDump = Math.min(bucket.storedUnits(), flowRate);

        if (!allowReceiverOverflow) {
            int receiverRoom = Math.max(0, receiver.capacity() - receiver.storedUnits());
            toDump = Math.min(toDump, receiverRoom);
        }

        if (toDump <= 0) {
            return DumpTickResult.NONE;
        }

        Vec3 lip = currentPose.lip();
        GranularMaterial material = bucket.storedMaterial();
        int consumed = receiver.receiveMaterialAt(
                level,
                lip,
                material,
                toDump
        );

        consumed = Math.clamp(consumed, 0, toDump);
        if (consumed <= 0) {
            return DumpTickResult.NONE;
        }

        int extracted = bucket.extractMaterial(consumed);
        spawnContainerTransferParticles(
                level,
                lip,
                material,
                extracted
        );

        return new DumpTickResult(
                extracted,
                material,
                lip,
                true
        );
    }

    @Nullable
    private static BlockPos findDepositSurface(ServerLevel level, Vec3 lip, GranularMaterial material) {
        BlockPos start = BlockPos.containing(lip.x, lip.y, lip.z);
        int lipY = start.getY();
        int minY = Math.max(level.getMinY(), lipY - 14);

        for (int y = lipY; y >= minY; y--) {
            BlockPos checkPos = new BlockPos(start.getX(), y, start.getZ());
            GranularMaterial terrainMaterial =
                    GroundworksExcavationAdapter.getMaterial(level, checkPos);

            if (terrainMaterial != GranularMaterial.EMPTY) {
                // Same material may fill the current partial cell. A different
                // material receives the dump in the cell above.
                return terrainMaterial.id() == material.id()
                        ? checkPos
                        : checkPos.above();
            }

            BlockState state = level.getBlockState(checkPos);
            if (!state.isAir()) {
                return checkPos.above();
            }
        }

        return null;
    }

    private static void spawnContainerTransferParticles(
            ServerLevel level,
            Vec3 lip,
            GranularMaterial material,
            int units
    ) {
        var block = material.sourceBlock() != null
                ? material.sourceBlock()
                : Blocks.DIRT;

        BlockParticleOption particle = new BlockParticleOption(
                ParticleTypes.BLOCK,
                block.defaultBlockState()
        );

        int count = Math.clamp(units / 4, 4, 20);
        level.sendParticles(
                particle,
                lip.x,
                lip.y,
                lip.z,
                count,
                0.14D,
                0.10D,
                0.14D,
                0.04D
        );
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
