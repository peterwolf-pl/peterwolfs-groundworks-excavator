package com.piotrek.groundworksexcavator.automation;

import com.piotrek.groundworks.api.container.IMobileWorldGranularContainer;
import com.piotrek.groundworks.api.container.IWorldGranularContainer;
import com.piotrek.groundworks.api.container.GranularContainerTransferApi;
import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import com.piotrek.groundworksexcavator.entity.GroundworksExcavatorEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Spatial targeting and receiver spotting for haul trucks and mobile granular containers.
 *
 * <p>Solves optimal dumping poses, searches for mobile receivers in the world, and manages
 * fleet receiver tracking for automated excavation cycles.</p>
 */
public final class ExcavatorTruckSpotter {

    public static final double FLEET_TRUCK_SEARCH_RADIUS = 14.0D;
    public static final float FLEET_YAW_STEP_DEGREES = 2.0F;
    public static final float FLEET_FINE_YAW_STEP_DEGREES = 1.0F;
    public static final float FLEET_MIN_DUMP_BOOM = 42.0F;
    public static final float FLEET_MAX_DUMP_BOOM = 58.0F;
    public static final float FLEET_DUMP_BOOM_STEP = 2.0F;
    public static final float FLEET_MIN_DUMP_STICK = -70.0F;
    public static final float FLEET_MAX_DUMP_STICK = -34.0F;
    public static final float FLEET_DUMP_STICK_STEP = 4.0F;
    public static final double FLEET_INTAKE_PROBE_STEP = 0.25D;
    public static final double REAR_TRUCK_MAX_DISTANCE = 10.0D;
    public static final double REAR_TRUCK_MAX_LATERAL = 4.0D;

    private int fleetTargetEntityId = -1;
    private float fleetTargetYaw = AutoTrenchController.REAR_DUMP_YAW;
    private float fleetTargetBoom = AutoTrenchController.REAR_DUMP_CHECK_BOOM;
    private float fleetTargetStick = AutoTrenchController.DUMP_STICK;

    public record FleetDumpPose(
            float yaw,
            float boom,
            float stick,
            Vec3 receiverCenter,
            double bucketCenterError,
            double lipError
    ) {}

    public void reset() {
        this.fleetTargetEntityId = -1;
        this.fleetTargetYaw = AutoTrenchController.REAR_DUMP_YAW;
        this.fleetTargetBoom = AutoTrenchController.REAR_DUMP_CHECK_BOOM;
        this.fleetTargetStick = AutoTrenchController.DUMP_STICK;
    }

    public int getFleetTargetEntityId() {
        return this.fleetTargetEntityId;
    }

    public float getFleetTargetYaw() {
        return this.fleetTargetYaw;
    }

    public float getFleetTargetBoom() {
        return this.fleetTargetBoom;
    }

    public float getFleetTargetStick() {
        return this.fleetTargetStick;
    }

    @Nullable
    public IMobileWorldGranularContainer getFleetTarget(ServerLevel level) {
        if (this.fleetTargetEntityId < 0) {
            return null;
        }

        Entity entity = level.getEntity(this.fleetTargetEntityId);
        return entity instanceof IMobileWorldGranularContainer mobile
                ? mobile
                : null;
    }

    /**
     * Keeps one fleet receiver selected until its bed is full. A full receiver
     * is replaced by the fullest remaining truck. If the old truck filled before
     * the bucket emptied, the controller closes the bucket before swinging toward
     * the next truck.
     */
    @Nullable
    public IMobileWorldGranularContainer updateFleetTarget(
            ServerLevel level,
            GroundworksExcavatorEntity excavator,
            AutoTrenchController autoTrench,
            AutoTrenchController.Snapshot snapshot,
            Predicate<IWorldGranularContainer> isReceiverFull,
            @Nullable Consumer<IMobileWorldGranularContainer> onFullTruck
    ) {
        IMobileWorldGranularContainer current = this.getFleetTarget(level);

        if (current != null && isReceiverFull.test(current)) {
            if (onFullTruck != null) {
                onFullTruck.accept(current);
            }

            if (snapshot.storedUnits() <= 0
                    && autoTrench.phase() == AutoTrenchController.Phase.DUMP_RIGHT) {
                return current;
            }

            this.fleetTargetEntityId = -1;
            current = null;
        }

        if (current != null) {
            BucketPose openPose = plannedFleetOpenDumpPose(
                    excavator,
                    this.fleetTargetYaw,
                    this.fleetTargetBoom,
                    this.fleetTargetStick
            );

            if (current.canReceiveAt(openPose.lip())) {
                autoTrench.setFleetDumpPose(
                        this.fleetTargetYaw,
                        this.fleetTargetBoom,
                        this.fleetTargetStick
                );
                return current;
            }

            FleetDumpPose correctedPose = this.findFleetDumpPose(excavator, current);
            if (correctedPose != null) {
                boolean changed = Math.abs(Mth.wrapDegrees(
                        correctedPose.yaw() - this.fleetTargetYaw
                )) > 0.75F
                        || Math.abs(correctedPose.boom() - this.fleetTargetBoom) > 0.5F
                        || Math.abs(correctedPose.stick() - this.fleetTargetStick) > 0.5F;

                this.applyFleetDumpPose(correctedPose);

                if (snapshot.storedUnits() > 0
                        && autoTrench.phase() == AutoTrenchController.Phase.DUMP_RIGHT
                        && changed) {
                    autoTrench.retargetFleetDump(
                            correctedPose.yaw(),
                            correctedPose.boom(),
                            correctedPose.stick()
                    );
                } else {
                    autoTrench.setFleetDumpPose(
                            correctedPose.yaw(),
                            correctedPose.boom(),
                            correctedPose.stick()
                    );
                }
                return current;
            }

            this.fleetTargetEntityId = -1;
            current = null;
        }

        IMobileWorldGranularContainer selected = this.selectBestFleetTarget(level, excavator, isReceiverFull);
        if (selected != null) {
            if (snapshot.storedUnits() > 0
                    && autoTrench.phase() == AutoTrenchController.Phase.DUMP_RIGHT) {
                autoTrench.retargetFleetDump(
                        this.fleetTargetYaw,
                        this.fleetTargetBoom,
                        this.fleetTargetStick
                );
            } else {
                autoTrench.setFleetDumpPose(
                        this.fleetTargetYaw,
                        this.fleetTargetBoom,
                        this.fleetTargetStick
                );
            }
            return selected;
        }

        if (snapshot.storedUnits() > 0
                && autoTrench.phase() == AutoTrenchController.Phase.DUMP_RIGHT) {
            autoTrench.retargetFleetDump(
                    snapshot.cabin(),
                    AutoTrenchController.REAR_DUMP_CHECK_BOOM,
                    AutoTrenchController.DUMP_STICK
            );
        }

        return null;
    }

    @Nullable
    public IMobileWorldGranularContainer selectBestFleetTarget(
            ServerLevel level,
            GroundworksExcavatorEntity excavator,
            Predicate<IWorldGranularContainer> isReceiverFull
    ) {
        IMobileWorldGranularContainer best = null;
        Entity bestEntity = null;
        FleetDumpPose bestPose = null;
        double bestFill = -1.0D;
        double bestDistance = Double.MAX_VALUE;

        for (IMobileWorldGranularContainer candidate :
                findAllMobileContainers(level, excavator, FLEET_TRUCK_SEARCH_RADIUS)) {
            if (!(candidate instanceof Entity entity)
                    || candidate.capacity() <= 0
                    || isReceiverFull.test(candidate)
                    || candidate.isAdvanceInProgress()) {
                continue;
            }

            FleetDumpPose pose = this.findFleetDumpPose(excavator, candidate);
            if (pose == null) {
                continue;
            }

            double fill = (double) candidate.storedUnits() / (double) candidate.capacity();
            double distance = entity.distanceToSqr(excavator.getX(), excavator.getY(), excavator.getZ());

            if (fill > bestFill + 1.0E-6D
                    || (Math.abs(fill - bestFill) <= 1.0E-6D && distance < bestDistance)) {
                best = candidate;
                bestEntity = entity;
                bestPose = pose;
                bestFill = fill;
                bestDistance = distance;
            }
        }

        if (best != null && bestEntity != null && bestPose != null) {
            this.fleetTargetEntityId = bestEntity.getId();
            this.applyFleetDumpPose(bestPose);
        }
        return best;
    }

    public void applyFleetDumpPose(FleetDumpPose pose) {
        this.fleetTargetYaw = pose.yaw();
        this.fleetTargetBoom = pose.boom();
        this.fleetTargetStick = pose.stick();
    }

    /**
     * Finds the geometric center of the receiver opening through the public
     * canReceiveAt contract. This avoids a hard dependency on the dump-truck class
     * while still centering over its real bed footprint.
     */
    @Nullable
    public static Vec3 estimateFleetReceiverCenter(IMobileWorldGranularContainer receiver) {
        if (!(receiver instanceof Entity entity)) {
            return null;
        }

        double yawRad = Math.toRadians(entity.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        Vec3 right = new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
        double probeY = entity.getY() + 2.5D;

        double sumX = 0.0D;
        double sumZ = 0.0D;
        int accepted = 0;

        for (double localForward = -5.5D; localForward <= 2.5D;
             localForward += FLEET_INTAKE_PROBE_STEP) {
            for (double localRight = -3.0D; localRight <= 3.0D;
                 localRight += FLEET_INTAKE_PROBE_STEP) {
                Vec3 probe = new Vec3(entity.getX(), probeY, entity.getZ())
                        .add(forward.scale(localForward))
                        .add(right.scale(localRight));
                if (receiver.canReceiveAt(probe)) {
                    sumX += probe.x;
                    sumZ += probe.z;
                    accepted++;
                }
            }
        }

        if (accepted <= 0) {
            return null;
        }

        return new Vec3(
                sumX / accepted,
                probeY,
                sumZ / accepted
        );
    }

    /**
     * Solves yaw + boom + stick against the OPEN bucket geometry. The primary
     * objective is the bucket body's horizontal center over the receiver center.
     * The material release lip must also remain inside the receiver opening.
     */
    @Nullable
    public FleetDumpPose findFleetDumpPose(
            GroundworksExcavatorEntity excavator,
            IMobileWorldGranularContainer receiver
    ) {
        Vec3 receiverCenter = estimateFleetReceiverCenter(receiver);
        if (receiverCenter == null) {
            return null;
        }

        float roughYaw = this.findRoughFleetYaw(excavator, receiverCenter);
        FleetDumpPose best = null;
        double bestScore = Double.MAX_VALUE;

        for (float boom = FLEET_MIN_DUMP_BOOM;
             boom <= FLEET_MAX_DUMP_BOOM + 0.01F;
             boom += FLEET_DUMP_BOOM_STEP) {
            for (float stick = FLEET_MIN_DUMP_STICK;
                 stick <= FLEET_MAX_DUMP_STICK + 0.01F;
                 stick += FLEET_DUMP_STICK_STEP) {
                for (float yawOffset = -12.0F; yawOffset <= 12.0F + 0.01F;
                     yawOffset += FLEET_FINE_YAW_STEP_DEGREES) {
                    float yaw = Mth.wrapDegrees(roughYaw + yawOffset);
                    BucketPose pose = plannedFleetOpenDumpPose(excavator, yaw, boom, stick);

                    // The actual stream exits from the open bucket lip. It must project
                    // over the body opening or this pose is not allowed.
                    if (!receiver.canReceiveAt(pose.lip())) {
                        continue;
                    }

                    Vec3 bucketCenter = pose.pivot().add(pose.lip()).scale(0.5D);
                    double centerError = horizontalDistanceSqr(bucketCenter, receiverCenter);
                    double lipError = horizontalDistanceSqr(pose.lip(), receiverCenter);

                    // Centering the bucket body is the primary objective; keeping the
                    // pouring lip close to the middle adds a strong visual/safety bias.
                    double score = centerError + (lipError * 0.55D);
                    if (score < bestScore) {
                        bestScore = score;
                        best = new FleetDumpPose(
                                yaw,
                                boom,
                                stick,
                                receiverCenter,
                                Math.sqrt(centerError),
                                Math.sqrt(lipError)
                        );
                    }
                }
            }
        }

        return best;
    }

    public float findRoughFleetYaw(
            GroundworksExcavatorEntity excavator,
            Vec3 receiverCenter
    ) {
        float bestYaw = excavator.getUpperYaw();
        double bestDistance = Double.MAX_VALUE;

        for (float yaw = -180.0F; yaw < 180.0F; yaw += FLEET_YAW_STEP_DEGREES) {
            BucketPose pose = plannedFleetOpenDumpPose(
                    excavator,
                    yaw,
                    AutoTrenchController.REAR_DUMP_CHECK_BOOM,
                    AutoTrenchController.DUMP_STICK
            );
            Vec3 bucketCenter = pose.pivot().add(pose.lip()).scale(0.5D);
            double distance = horizontalDistanceSqr(bucketCenter, receiverCenter);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestYaw = yaw;
            }
        }
        return bestYaw;
    }

    private static double horizontalDistanceSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    public static BucketPose plannedFleetOpenDumpPose(
            GroundworksExcavatorEntity excavator,
            float cabinYaw,
            float boom,
            float stick
    ) {
        return ArmKinematics.computeBucketPose(
                excavator.position(),
                excavator.getYRot(),
                excavator.getVehiclePitch(),
                excavator.getVehicleRoll(),
                cabinYaw,
                boom,
                stick,
                AutoTrenchController.DUMP_BUCKET,
                excavator.getBucketType()
        );
    }

    @Nullable
    public static IMobileWorldGranularContainer findDumpTruckUnderPlannedRearDumpLip(
            ServerLevel level,
            GroundworksExcavatorEntity excavator
    ) {
        BucketPose plannedDumpPose = ArmKinematics.computeBucketPose(
                excavator.position(),
                excavator.getYRot(),
                excavator.getVehiclePitch(),
                excavator.getVehicleRoll(),
                AutoTrenchController.REAR_DUMP_YAW,
                AutoTrenchController.REAR_DUMP_CHECK_BOOM,
                AutoTrenchController.DUMP_STICK,
                AutoTrenchController.HELD_BUCKET,
                excavator.getBucketType()
        );

        Vec3 plannedLip = plannedDumpPose.lip();
        IWorldGranularContainer receiver = GranularContainerTransferApi.findReceiver(
                level,
                plannedLip,
                excavator,
                8.0D
        );

        return receiver instanceof IMobileWorldGranularContainer mobile
                ? mobile
                : null;
    }

    @Nullable
    public static IMobileWorldGranularContainer findNearestMobileContainer(
            ServerLevel level,
            Entity excavator,
            double radius
    ) {
        AABB area = excavator.getBoundingBox().inflate(radius, 4.0D, radius);
        Entity nearestEntity = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Entity candidate : level.getEntitiesOfClass(
                Entity.class,
                area,
                entity -> entity != excavator
                        && entity instanceof IMobileWorldGranularContainer
        )) {
            double distance = candidate.distanceToSqr(excavator.getX(), excavator.getY(), excavator.getZ());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestEntity = candidate;
            }
        }

        return nearestEntity instanceof IMobileWorldGranularContainer mobile
                ? mobile
                : null;
    }

    public static List<IMobileWorldGranularContainer> findAllMobileContainers(
            ServerLevel level,
            Entity excavator,
            double radius
    ) {
        AABB area = excavator.getBoundingBox().inflate(radius, 4.0D, radius);
        List<IMobileWorldGranularContainer> result = new ArrayList<>();

        for (Entity candidate : level.getEntitiesOfClass(
                Entity.class,
                area,
                entity -> entity != excavator
                        && entity instanceof IMobileWorldGranularContainer
        )) {
            result.add((IMobileWorldGranularContainer) candidate);
        }
        return result;
    }

    @Nullable
    public static IMobileWorldGranularContainer findRearMobileContainer(
            ServerLevel level,
            GroundworksExcavatorEntity excavator
    ) {
        double yawRad = Math.toRadians(excavator.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        Vec3 rear = forward.scale(-1.0D);
        Vec3 right = new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad));

        AABB area = excavator.getBoundingBox().inflate(
                REAR_TRUCK_MAX_DISTANCE,
                4.0D,
                REAR_TRUCK_MAX_DISTANCE
        );

        Entity nearestEntity = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Entity candidate : level.getEntitiesOfClass(
                Entity.class,
                area,
                entity -> entity != excavator
                        && entity instanceof IMobileWorldGranularContainer
        )) {
            Vec3 delta = candidate.position().subtract(excavator.position());
            double behind = delta.dot(rear);
            double lateral = Math.abs(delta.dot(right));

            if (behind >= 1.5D
                    && behind <= REAR_TRUCK_MAX_DISTANCE
                    && lateral <= REAR_TRUCK_MAX_LATERAL
                    && Math.abs(delta.y) <= 2.5D) {
                double distance = candidate.distanceToSqr(excavator.getX(), excavator.getY(), excavator.getZ());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestEntity = candidate;
                }
            }
        }

        return nearestEntity instanceof IMobileWorldGranularContainer mobile
                ? mobile
                : null;
    }

    public static boolean hasRearDumpTruck(
            ServerLevel level,
            GroundworksExcavatorEntity excavator
    ) {
        return findRearMobileContainer(level, excavator) != null;
    }
}
