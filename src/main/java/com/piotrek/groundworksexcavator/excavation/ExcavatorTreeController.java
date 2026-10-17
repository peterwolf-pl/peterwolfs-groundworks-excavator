package com.piotrek.groundworksexcavator.excavation;

import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
import com.piotrek.groundworksexcavator.integration.rtf.RealisticTreeFellingAdapter;
import com.piotrek.groundworksexcavator.integration.rtf.RealisticTreeFellingAdapter.TreeInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Authoritative controller for excavator tree and canopy interactions:
 * <ul>
 *   <li>Destroys and shreds tree leaves as the bucket/arm sweeps through foliage.</li>
 *   <li>Physical tree felling with leverage mechanics: higher bucket contact point
 *       yields greater torque ($M = F \cdot h$), making trees significantly easier
 *       to topple the higher the bucket strikes the trunk.</li>
 *   <li>Directional felling: pushing away fells the tree away from the excavator/player
 *       (stump near player, crown further away); swinging or pushing from the side
 *       fells the tree to the side.</li>
 * </ul>
 */
public final class ExcavatorTreeController {

    public static final int MAX_LEAVES_PER_TICK = 24;
    public static final double MAX_SWEEP_DISTANCE = 2.5D;
    public static final double CONTACT_PROXIMITY_RADIUS = 0.90D;
    public static final float BASE_BREAK_THRESHOLD = 5.0F;

    private ExcavatorTreeController() {}

    /**
     * Executes leaf destruction and tree pushing physics for one server tick.
     */
    public static void tick(
            ServerLevel level,
            GroundworksExcavatorEntity excavator,
            BucketPose previousPose,
            BucketPose currentPose,
            @Nullable Entity driver
    ) {
        if (previousPose == null || currentPose == null) {
            return;
        }

        double moveDist = currentPose.cuttingEdge().distanceTo(previousPose.cuttingEdge());
        if (moveDist > MAX_SWEEP_DISTANCE) {
            return;
        }

        AABB bucketBox = computeSweptBucketBox(previousPose, currentPose);

        // 1. Shred leaves intersected by the bucket swept volume
        smashLeaves(level, excavator, bucketBox, currentPose, driver);

        // 2. Detect and process physical tree trunk pushing & directional felling
        processTreePushing(level, excavator, bucketBox, previousPose, currentPose, driver);
    }

    /**
     * Smashes tree foliage in contact with the excavator bucket.
     */
    private static void smashLeaves(
            ServerLevel level,
            GroundworksExcavatorEntity excavator,
            AABB bucketBox,
            BucketPose currentPose,
            @Nullable Entity driver
    ) {
        int minX = (int) Math.floor(bucketBox.minX);
        int minY = (int) Math.floor(bucketBox.minY);
        int minZ = (int) Math.floor(bucketBox.minZ);
        int maxX = (int) Math.ceil(bucketBox.maxX);
        int maxY = (int) Math.ceil(bucketBox.maxY);
        int maxZ = (int) Math.ceil(bucketBox.maxZ);

        Entity breaker = driver != null ? driver : excavator;
        int leavesDestroyed = 0;
        BlockPos lastLeafPos = null;

        for (int x = minX; x <= maxX && leavesDestroyed < MAX_LEAVES_PER_TICK; x++) {
            for (int y = minY; y <= maxY && leavesDestroyed < MAX_LEAVES_PER_TICK; y++) {
                for (int z = minZ; z <= maxZ && leavesDestroyed < MAX_LEAVES_PER_TICK; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(BlockTags.LEAVES)) {
                        continue;
                    }

                    if (isPointNearBucket(Vec3.atCenterOf(pos), currentPose, CONTACT_PROXIMITY_RADIUS)) {
                        level.destroyBlock(pos, true, breaker);
                        leavesDestroyed++;
                        lastLeafPos = pos;
                    }
                }
            }
        }

        if (leavesDestroyed > 0 && lastLeafPos != null) {
            level.playSound(
                    null,
                    lastLeafPos,
                    SoundEvents.AZALEA_LEAVES_BREAK,
                    SoundSource.BLOCKS,
                    1.2F,
                    0.9F + (float) (Math.random() * 0.2F)
            );
        }
    }

    /**
     * Checks log contacts, applies leverage-scaled strain, and triggers directional felling.
     */
    private static void processTreePushing(
            ServerLevel level,
            GroundworksExcavatorEntity excavator,
            AABB bucketBox,
            BucketPose previousPose,
            BucketPose currentPose,
            @Nullable Entity driver
    ) {
        BlockPos contactLogPos = findContactLog(level, bucketBox, currentPose);
        if (contactLogPos == null) {
            // Decay strain if excavator is no longer in contact with a tree
            if (excavator.tickCount - excavator.getLastTreePushTick() > 10) {
                excavator.setTreePushStrain(0.0F);
                excavator.setPushedTreeBase(null);
            }
            return;
        }

        TreeInfo tree = RealisticTreeFellingAdapter.detectTree(level, contactLogPos);
        if (tree == null) {
            // Solitary log / stump pushing
            processStumpPushing(level, excavator, contactLogPos, previousPose, currentPose, driver);
            return;
        }

        BlockPos treeBase = tree.base();
        if (excavator.getPushedTreeBase() == null || !excavator.getPushedTreeBase().equals(treeBase)) {
            excavator.setPushedTreeBase(treeBase);
            excavator.setTreePushStrain(0.0F);
        }

        float effort = calculatePushEffort(excavator, previousPose, currentPose);
        if (effort < 0.04F) {
            return;
        }

        // Height of bucket contact relative to tree base
        double contactY = contactLogPos.getY() + 0.5D;
        double treeBaseY = treeBase.getY();
        double height = Math.max(0.5D, contactY - treeBaseY);

        // Leverage formula: M = F * h.
        // Higher contact point multiplies the effective push torque.
        float leverage = Math.max(0.25F, (float) (height / 2.0D));
        float deltaStrain = effort * leverage;

        float currentStrain = excavator.getTreePushStrain() + deltaStrain;
        excavator.setTreePushStrain(currentStrain);
        excavator.setPushingTree(true);
        excavator.setLastTreePushTick(excavator.tickCount);

        // Visual and auditory physical strain feedback
        if (excavator.tickCount % 3 == 0) {
            level.playSound(
                    null,
                    contactLogPos,
                    SoundEvents.WOOD_HIT,
                    SoundSource.BLOCKS,
                    1.1F,
                    0.80F + (float) (Math.random() * 0.15F)
            );
            spawnWoodParticles(level, contactLogPos);
        }

        float requiredThreshold = calculateBreakThreshold(tree.height());
        if (currentStrain >= requiredThreshold) {
            // Threshold reached: SNAP and FELL TREE!
            Direction fallDirection = calculateFallDirection(
                    excavator,
                    treeBase,
                    previousPose,
                    currentPose
            );

            ServerPlayer player = driver instanceof ServerPlayer sp ? sp : null;
            RealisticTreeFellingAdapter.fellTree(level, player, tree, fallDirection);

            level.playSound(
                    null,
                    contactLogPos,
                    SoundEvents.WOOD_BREAK,
                    SoundSource.BLOCKS,
                    1.6F,
                    0.85F
            );

            excavator.setTreePushStrain(0.0F);
            excavator.setPushedTreeBase(null);
        }
    }

    /**
     * Solitary log or remaining stump removal under sustained excavator push.
     */
    private static void processStumpPushing(
            ServerLevel level,
            GroundworksExcavatorEntity excavator,
            BlockPos stumpPos,
            BucketPose previousPose,
            BucketPose currentPose,
            @Nullable Entity driver
    ) {
        float effort = calculatePushEffort(excavator, previousPose, currentPose);
        if (effort < 0.05F) {
            return;
        }

        float strain = excavator.getTreePushStrain() + effort * 0.35F;
        excavator.setTreePushStrain(strain);
        excavator.setPushingTree(true);
        excavator.setLastTreePushTick(excavator.tickCount);

        if (strain >= BASE_BREAK_THRESHOLD * 1.5F) {
            Entity breaker = driver != null ? driver : excavator;
            level.destroyBlock(stumpPos, true, breaker);
            excavator.setTreePushStrain(0.0F);
            excavator.setPushedTreeBase(null);
        }
    }

    /**
     * Calculates operator push effort from tracks, arm hydraulics, and bucket velocity.
     */
    public static float calculatePushEffort(
            GroundworksExcavatorEntity excavator,
            BucketPose previousPose,
            BucketPose currentPose
    ) {
        float effort = 0.0F;

        // Differential tracks driving
        effort += Math.abs(excavator.getInputThrottle()) * 1.0F;
        effort += Math.abs(excavator.getInputSteer()) * 0.5F;

        // Hydraulic arm joints
        effort += Math.abs(excavator.getInputCabYaw()) * 1.3F;
        effort += Math.abs(excavator.getInputStick()) * 1.1F;
        effort += Math.abs(excavator.getInputBoom()) * 0.8F;
        effort += Math.abs(excavator.getInputBucket()) * 0.6F;

        // Dynamic impact speed
        double speed = currentPose.cuttingEdge().distanceTo(previousPose.cuttingEdge());
        if (speed > 0.02D) {
            effort += (float) (speed * 8.0D);
        }

        return Math.min(effort, 3.0F);
    }

    /**
     * Computes the directional felling vector:
     * <ul>
     *   <li>Pushing forward away from excavator/player: tree falls away along the arm/drive axis
     *       (stump at base near player, crown falling forward).</li>
     *   <li>Swinging or striking from the side: tree falls to the side.</li>
     * </ul>
     */
    public static Direction calculateFallDirection(
            GroundworksExcavatorEntity excavator,
            BlockPos treeBase,
            BucketPose previousPose,
            BucketPose currentPose
    ) {
        Vec3 bucketMotion = currentPose.cuttingEdge().subtract(previousPose.cuttingEdge());
        Vec3 vehicleMotion = excavator.getDeltaMovement();

        float totalCabYaw = excavator.getYRot() + excavator.getUpperYaw();
        double rad = Math.toRadians(totalCabYaw);

        // Unit vector pointing forward along cab/arm axis (away from excavator)
        Vec3 armForward = new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
        // Unit vector pointing right along cab/arm axis (clockwise perpendicular)
        Vec3 armRight = new Vec3(Math.cos(rad), 0.0D, Math.sin(rad));

        double pushX = 0.0D;
        double pushZ = 0.0D;

        // 1. Dynamic movement contributions
        if (Math.hypot(bucketMotion.x, bucketMotion.z) > 0.015D) {
            pushX += bucketMotion.x * 3.0D;
            pushZ += bucketMotion.z * 3.0D;
        }
        if (Math.hypot(vehicleMotion.x, vehicleMotion.z) > 0.01D) {
            pushX += vehicleMotion.x * 2.5D;
            pushZ += vehicleMotion.z * 2.5D;
        }

        // 2. Control intent contributions: cab swing pushes sideways
        float cabYawInput = excavator.getInputCabYaw();
        if (Math.abs(cabYawInput) > 0.05F) {
            pushX += armRight.x * cabYawInput * 3.2D;
            pushZ += armRight.z * cabYawInput * 3.2D;
        }

        // 3. Control intent contributions: driving or stick extending pushes along arm
        float throttle = excavator.getInputThrottle();
        float stick = excavator.getInputStick();
        float forwardPush = Math.max(throttle, stick);
        float pull = Math.min(throttle, stick);

        if (forwardPush > 0.05F) {
            pushX += armForward.x * forwardPush * 2.8D;
            pushZ += armForward.z * forwardPush * 2.8D;
        } else if (pull < -0.05F) {
            pushX += armForward.x * pull * 2.8D;
            pushZ += armForward.z * pull * 2.8D;
        }

        // 4. Fallback: vector pointing from excavator chassis toward tree trunk base (away from player)
        if (Math.hypot(pushX, pushZ) < 0.01D) {
            pushX = (treeBase.getX() + 0.5D) - excavator.getX();
            pushZ = (treeBase.getZ() + 0.5D) - excavator.getZ();
        }

        return getNearestHorizontal(pushX, pushZ);
    }

    /**
     * Maps horizontal (dx, dz) force vector to the nearest cardinal Direction.
     */
    public static Direction getNearestHorizontal(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0.0D ? Direction.EAST : Direction.WEST;
        } else {
            return dz >= 0.0D ? Direction.SOUTH : Direction.NORTH;
        }
    }

    public static float calculateBreakThreshold(int treeHeight) {
        return BASE_BREAK_THRESHOLD + Math.min(5.0F, Math.max(0, treeHeight - 4) * 0.25F);
    }

    private static @Nullable BlockPos findContactLog(
            ServerLevel level,
            AABB bucketBox,
            BucketPose currentPose
    ) {
        int minX = (int) Math.floor(bucketBox.minX);
        int minY = (int) Math.floor(bucketBox.minY);
        int minZ = (int) Math.floor(bucketBox.minZ);
        int maxX = (int) Math.ceil(bucketBox.maxX);
        int maxY = (int) Math.ceil(bucketBox.maxY);
        int maxZ = (int) Math.ceil(bucketBox.maxZ);

        BlockPos closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).is(BlockTags.LOGS)) {
                        continue;
                    }

                    Vec3 center = Vec3.atCenterOf(pos);
                    if (isPointNearBucket(center, currentPose, CONTACT_PROXIMITY_RADIUS)) {
                        double dSq = center.distanceToSqr(currentPose.cuttingEdge());
                        if (dSq < closestDistSq) {
                            closestDistSq = dSq;
                            closest = pos;
                        }
                    }
                }
            }
        }
        return closest;
    }

    private static boolean isPointNearBucket(Vec3 point, BucketPose pose, double radius) {
        double rSq = radius * radius;
        if (point.distanceToSqr(pose.cuttingEdge()) <= rSq) return true;
        if (point.distanceToSqr(pose.lip()) <= rSq) return true;
        if (point.distanceToSqr(pose.pivot()) <= rSq) return true;

        for (Vec3 tooth : pose.teethPoints()) {
            if (point.distanceToSqr(tooth) <= rSq) return true;
        }

        // Check line segment between pivot and cutting edge
        Vec3 ab = pose.cuttingEdge().subtract(pose.pivot());
        double abLenSq = ab.lengthSqr();
        if (abLenSq > 1.0E-5D) {
            double t = Math.clamp(point.subtract(pose.pivot()).dot(ab) / abLenSq, 0.0D, 1.0D);
            Vec3 proj = pose.pivot().add(ab.scale(t));
            if (point.distanceToSqr(proj) <= rSq) return true;
        }

        return false;
    }

    private static AABB computeSweptBucketBox(BucketPose prev, BucketPose curr) {
        List<Vec3> points = new ArrayList<>();
        points.add(prev.pivot());
        points.add(prev.cuttingEdge());
        points.add(prev.lip());
        points.addAll(prev.teethPoints());

        points.add(curr.pivot());
        points.add(curr.cuttingEdge());
        points.add(curr.lip());
        points.addAll(curr.teethPoints());

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (Vec3 pt : points) {
            minX = Math.min(minX, pt.x);
            minY = Math.min(minY, pt.y);
            minZ = Math.min(minZ, pt.z);
            maxX = Math.max(maxX, pt.x);
            maxY = Math.max(maxY, pt.y);
            maxZ = Math.max(maxZ, pt.z);
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.35D);
    }

    private static void spawnWoodParticles(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, state),
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                8,
                0.2D, 0.2D, 0.2D,
                0.05D
        );
    }
}
