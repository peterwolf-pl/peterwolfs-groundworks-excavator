package com.piotrek.groundworksexcavator.entity;

import com.piotrek.groundworks.api.container.GranularContainerTransferApi;
import com.piotrek.groundworks.api.container.IMobileWorldGranularContainer;
import com.piotrek.groundworks.api.container.IWorldGranularContainer;
import com.piotrek.groundworks.api.material.GranularMaterial;
import com.piotrek.groundworks.api.material.GranularMaterialRegistry;
import com.piotrek.groundworksexcavator.GroundworksExcavatorMod;
import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import com.piotrek.groundworksexcavator.arm.ArmKinematics.BucketPose;
import com.piotrek.groundworksexcavator.automation.AutoTrenchController;
import com.piotrek.groundworksexcavator.excavation.ArmTerrainContactController;
import com.piotrek.groundworksexcavator.excavation.BucketDumpingController;
import com.piotrek.groundworksexcavator.excavation.BucketExcavationController;
import com.piotrek.groundworksexcavator.excavation.HydraulicHammerController;
import com.piotrek.groundworksexcavator.integration.groundworks.GroundworksExcavationAdapter;
import com.piotrek.groundworksexcavator.material.BucketMaterialContainer;
import com.piotrek.groundworksexcavator.vehicle.TrackMovementController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative tracked excavator vehicle entity.
 *
 * <p>Integrates differential track driving, rotating turntable cab, hierarchical arm kinematics,
 * volumetric bucket container, and swept terrain excavation/deposition with Peterwolf's Groundworks.
 */
public class GroundworksExcavatorEntity extends Entity {

    // ── Control Modes ────────────────────────────────────────────────
    public static final int MODE_DRIVE = 0;
    public static final int MODE_EXCAVATOR = 1;

    // ── Bucket Variants ──────────────────────────────────────────────
    public static final int BUCKET_STANDARD = 0; // 256 units (0.500 m³)
    public static final int BUCKET_LARGE = 1;    // 512 units (1.000 m³ - 2x capacity)
    public static final int BUCKET_HAMMER = 2;   // hydraulic breaker, no material intake
    public static final int CAPACITY_STANDARD = 256;
    public static final int CAPACITY_LARGE = 512;

    // ── Synched Entity Data ───────────────────────────────────────────
    private static final EntityDataAccessor<Integer> CONTROL_MODE =
            SynchedEntityData.defineId(GroundworksExcavatorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BUCKET_TYPE =
            SynchedEntityData.defineId(GroundworksExcavatorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> TRACK_LEFT_SPEED =
            SynchedEntityData.defineId(GroundworksExcavatorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TRACK_RIGHT_SPEED =
            SynchedEntityData.defineId(GroundworksExcavatorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> UPPER_YAW =
            SynchedEntityData.defineId(GroundworksExcavatorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BOOM_ANGLE =
            SynchedEntityData.defineId(GroundworksExcavatorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> STICK_ANGLE =
            SynchedEntityData.defineId(GroundworksExcavatorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BUCKET_ANGLE =
            SynchedEntityData.defineId(GroundworksExcavatorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> MATERIAL_ID =
            SynchedEntityData.defineId(GroundworksExcavatorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STORED_UNITS =
            SynchedEntityData.defineId(GroundworksExcavatorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CAPACITY =
            SynchedEntityData.defineId(GroundworksExcavatorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> VEHICLE_PITCH =
            SynchedEntityData.defineId(GroundworksExcavatorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> VEHICLE_ROLL =
            SynchedEntityData.defineId(GroundworksExcavatorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_DIGGING =
            SynchedEntityData.defineId(GroundworksExcavatorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_DUMPING =
            SynchedEntityData.defineId(GroundworksExcavatorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_HAMMERING =
            SynchedEntityData.defineId(GroundworksExcavatorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> MACHINE_LOAD =
            SynchedEntityData.defineId(GroundworksExcavatorEntity.class, EntityDataSerializers.FLOAT);

    // ── Components ───────────────────────────────────────────────────
    private final BucketMaterialContainer bucket = new BucketMaterialContainer();
    private final TrackMovementController trackController = new TrackMovementController();
    private final AutoTrenchController autoTrenchController = new AutoTrenchController();

    // ── Input & Kinematics State (Server-Authoritative) ───────────────
    private float inputThrottle;
    private float inputSteer;
    private float inputCabYaw;
    private float inputBoom;
    private float inputStick;
    private float inputBucket;
    private boolean inputHammerActive;
    private boolean hornInputLast;
    private long lastHornTick = Long.MIN_VALUE / 4L;
    private int autoTruckAdvanceStage;
    private int autoTruckHornDelayTicks;
    private int inputFreshTicks;

    private static final int DOUBLE_HORN_WINDOW_TICKS = 10;
    private static final int AUTO_SECOND_HORN_DELAY_TICKS = 4;
    private static final int AUTO_TRUCK_STAGE_IDLE = 0;
    private static final int AUTO_TRUCK_STAGE_WAIT_SECOND_HORN = 1;
    private static final int AUTO_TRUCK_STAGE_WAIT_TRUCK_MOVE = 2;
    private static final int AUTO_TRUCK_STAGE_READY_TO_REVERSE = 3;
    private static final int AUTO_TRUCK_STAGE_WAIT_REQUEST_ACCEPT = 4;
    private static final double HORN_TRUCK_SEARCH_RADIUS = 14.0D;
    private static final double REAR_TRUCK_MAX_DISTANCE = 10.0D;
    private static final double REAR_TRUCK_MAX_LATERAL = 4.0D;

    @Nullable
    private BucketPose previousBucketPose;
    @Nullable
    private BucketPose currentBucketPose;

    // Last recorded excavation / deposit amounts (for debug command & telemetry)
    private int lastExcavatedUnits;
    private int lastDepositedUnits;
    private boolean cabinBlockedLastTick;

    public GroundworksExcavatorEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CONTROL_MODE, MODE_DRIVE);
        builder.define(BUCKET_TYPE, BUCKET_STANDARD);
        builder.define(TRACK_LEFT_SPEED, 0.0F);
        builder.define(TRACK_RIGHT_SPEED, 0.0F);
        builder.define(UPPER_YAW, 0.0F);
        builder.define(BOOM_ANGLE, 15.0F);
        builder.define(STICK_ANGLE, -35.0F);
        builder.define(BUCKET_ANGLE, -10.0F);
        builder.define(MATERIAL_ID, 0);
        builder.define(STORED_UNITS, 0);
        builder.define(CAPACITY, BucketMaterialContainer.DEFAULT_CAPACITY);
        builder.define(VEHICLE_PITCH, 0.0F);
        builder.define(VEHICLE_ROLL, 0.0F);
        builder.define(IS_DIGGING, false);
        builder.define(IS_DUMPING, false);
        builder.define(IS_HAMMERING, false);
        builder.define(MACHINE_LOAD, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) {
            // Client-side visual sync container
            this.bucket.setCapacity(this.entityData.get(CAPACITY));
            this.bucket.setDirect(
                    GranularMaterialRegistry.byId(this.entityData.get(MATERIAL_ID)),
                    this.entityData.get(STORED_UNITS)
            );

            // Flashing warning beacon ambient light pulse when operating
            // Emits clean electrical/glow warning flashes instead of flame fire
            if (this.isOperating() && (this.tickCount % 6 == 0)) {
                Vec3 beaconPos = ArmKinematics.getBeaconWorldPosition(
                        this.position(), this.getYRot(), this.getUpperYaw()
                );
                this.level().addParticle(
                        ParticleTypes.ELECTRIC_SPARK,
                        beaconPos.x, beaconPos.y + 0.15D, beaconPos.z,
                        0.0D, 0.02D, 0.0D
                );
            }

            // Diesel engine exhaust smoke emission:
            // The exhaust pipe is located on the rear deck of the upper body and rotates with the turntable!
            // Load tiers:
            // > 30%: mała ilość jasno szarego dymu (WHITE_SMOKE co kilka ticków)
            // > 60%: średnia ilość ciemno szarego dymu (SMOKE co 2 ticki)
            // == 100% (przeciążenie): czarny dym z wydechu (LARGE_SMOKE / SMOKE każdy tick)
            float clientLoad = this.getMachineLoad();
            if (this.isOperating() && clientLoad > 0.30F) {
                Vec3 exhaustPos = ArmKinematics.getExhaustWorldPosition(
                        this.position(), this.getYRot(), this.getUpperYaw()
                );

                if (clientLoad >= 0.95F) {
                    // 100% obciążenia / opór: gęsty czarny dym z wydechu
                    this.level().addParticle(
                            ParticleTypes.LARGE_SMOKE,
                            exhaustPos.x, exhaustPos.y + 0.05D, exhaustPos.z,
                            (Math.random() - 0.5D) * 0.03D, 0.08D + Math.random() * 0.04D, (Math.random() - 0.5D) * 0.03D
                    );
                    this.level().addParticle(
                            ParticleTypes.SMOKE,
                            exhaustPos.x, exhaustPos.y + 0.05D, exhaustPos.z,
                            (Math.random() - 0.5D) * 0.02D, 0.06D, (Math.random() - 0.5D) * 0.02D
                    );
                } else if (clientLoad > 0.60F) {
                    // > 60% obciążenia: średnia ilość ciemno szarego dymu
                    if (this.tickCount % 2 == 0) {
                        this.level().addParticle(
                                ParticleTypes.SMOKE,
                                exhaustPos.x, exhaustPos.y + 0.05D, exhaustPos.z,
                                (Math.random() - 0.5D) * 0.02D, 0.05D + Math.random() * 0.02D, (Math.random() - 0.5D) * 0.02D
                        );
                    }
                } else {
                    // > 30% obciążenia: mała ilość jasno szarego dymu
                    if (this.tickCount % 4 == 0) {
                        this.level().addParticle(
                                ParticleTypes.WHITE_SMOKE,
                                exhaustPos.x, exhaustPos.y + 0.05D, exhaustPos.z,
                                (Math.random() - 0.5D) * 0.01D, 0.04D, (Math.random() - 0.5D) * 0.01D
                        );
                    }
                }
            }
            return;
        }

        ServerLevel serverLevel = (ServerLevel) this.level();
        Entity driver = this.getControllingPassenger();

        // Test-only automatic trench cycle. The controller writes through the same bounded
        // input path as a player, so normal hydraulics, contact checks, excavation, dumping,
        // movement, and material conservation remain authoritative.
        if (this.autoTrenchController.isActive()) {
            float trenchDepth = this.queryTrenchDepth(serverLevel);

            AutoTrenchController.Snapshot snapshot = new AutoTrenchController.Snapshot(
                    driver != null,
                    this.getUpperYaw(), this.getBoomAngle(),
                    this.getStickAngle(), this.getBucketAngle(),
                    this.getStoredUnits(), this.getBucketCapacity(),
                    this.position(), this.getYRot(),
                    this.cabinBlockedLastTick, trenchDepth);

            AutoTrenchController.Controls controls;
            if (this.autoTrenchController.isDumpTruckMode()
                    && this.autoTrenchController.phase() == AutoTrenchController.Phase.RESET_AND_REVERSE) {
                IMobileWorldGranularContainer rearTruck =
                        this.findRearMobileContainer(serverLevel);
                controls = this.tickAutomaticTruckAdvanceBeforeReverse(
                        serverLevel,
                        snapshot,
                        rearTruck
                );
            } else {
                this.resetAutomaticTruckAdvanceSequence();

                boolean dumpTruckAtDumpPoint =
                        !this.autoTrenchController.isDumpTruckMode()
                                || this.autoTrenchController.phase() != AutoTrenchController.Phase.DUMP_RIGHT
                                || this.findDumpTruckUnderPlannedRearDumpLip(serverLevel) != null;

                controls = this.autoTrenchController.tick(
                        snapshot,
                        dumpTruckAtDumpPoint
                );
            }

            this.setControlInputs(
                    controls.throttle(), controls.steer(), controls.cabYaw(),
                    controls.boom(), controls.stick(), controls.bucket());
        } else {
            this.resetAutomaticTruckAdvanceSequence();
        }

        // 1. Process driver input decay
        if (this.inputFreshTicks > 0) {
            this.inputFreshTicks--;
        } else {
            if (driver instanceof ServerPlayer player && this.isDriveMode()) {
                var input = player.getLastClientInput();
                this.inputThrottle = input.forward() ? 1.0F : input.backward() ? -1.0F : 0.0F;
                this.inputSteer = input.left() ? -1.0F : input.right() ? 1.0F : 0.0F;
            } else {
                this.inputThrottle = 0.0F;
                this.inputSteer = 0.0F;
            }
            this.inputCabYaw = 0.0F;
            this.inputBoom = 0.0F;
            this.inputStick = 0.0F;
            this.inputBucket = 0.0F;
            this.inputHammerActive = false;
        }

        // 2. Update hydraulic joint angles gradually
        float newCabYaw = Mth.wrapDegrees(this.getUpperYaw() + this.inputCabYaw * ArmKinematics.CAB_TURN_SPEED);
        float newBoom = Mth.clamp(
                this.getBoomAngle() + this.inputBoom * ArmKinematics.BOOM_SPEED,
                ArmKinematics.BOOM_MIN,
                ArmKinematics.BOOM_MAX
        );
        float newStick = Mth.clamp(
                this.getStickAngle() + this.inputStick * ArmKinematics.STICK_SPEED,
                ArmKinematics.STICK_MIN,
                ArmKinematics.STICK_MAX
        );
        float minBucket = ArmKinematics.getMinBucketAngle(this.getBucketType());
        float maxBucket = ArmKinematics.getMaxBucketAngle(this.getBucketType());
        float newBucket = Mth.clamp(
                this.getBucketAngle() + this.inputBucket * ArmKinematics.BUCKET_SPEED,
                minBucket,
                maxBucket
        );

        ArmTerrainContactController.JointAngles constrained =
                ArmTerrainContactController.constrain(
                        serverLevel,
                        this.position(),
                        this.getYRot(),
                        this.getVehiclePitch(),
                        this.getVehicleRoll(),
                        this.getBucketType(),
                        new ArmTerrainContactController.JointAngles(
                                this.getUpperYaw(), this.getBoomAngle(),
                                this.getStickAngle(), this.getBucketAngle()),
                        new ArmTerrainContactController.JointAngles(
                                newCabYaw, newBoom, newStick, newBucket));

        this.entityData.set(UPPER_YAW, constrained.cabin());
        this.entityData.set(BOOM_ANGLE, constrained.boom());
        this.entityData.set(STICK_ANGLE, constrained.stick());
        this.entityData.set(BUCKET_ANGLE, constrained.bucket());

        // 2b. Evaluate Machine Resistance & Hydraulic Overload
        boolean cabinBlocked = (newCabYaw != constrained.cabin()) && Math.abs(this.inputCabYaw) > 0.01F;
        this.cabinBlockedLastTick = cabinBlocked;
        boolean boomBlocked = (newBoom != constrained.boom()) && Math.abs(this.inputBoom) > 0.01F;
        boolean stickBlocked = (newStick != constrained.stick()) && Math.abs(this.inputStick) > 0.01F;
        boolean bucketBlocked = (newBucket != constrained.bucket()) && Math.abs(this.inputBucket) > 0.01F;
        boolean armRestricted = cabinBlocked || boomBlocked || stickBlocked || bucketBlocked;
        boolean hammerRequested = this.isHammerAttachment() && this.inputHammerActive;

        float targetLoad = 0.0F;
        if (armRestricted) {
            // High hydraulic overload when attempting to force steel into solid terrain
            targetLoad = 1.0F;
        } else if (hammerRequested) {
            targetLoad = 0.36F;
        } else if (this.isDigging()) {
            targetLoad = 0.55F;
        } else if (Math.abs(this.inputThrottle) > 0.01F || Math.abs(this.inputSteer) > 0.01F) {
            targetLoad = 0.30F;
        } else if (Math.abs(this.inputBoom) > 0.01F || Math.abs(this.inputStick) > 0.01F || Math.abs(this.inputBucket) > 0.01F) {
            targetLoad = 0.20F;
        }

        // Smooth load ramp up and decay
        float currentLoad = this.getMachineLoad();
        float updatedLoad = (!armRestricted && hammerRequested)
                ? 0.36F
                : Mth.lerp(armRestricted ? 0.45F : 0.15F, currentLoad, targetLoad);
        if (updatedLoad < 0.01F) updatedLoad = 0.0F;
        this.entityData.set(MACHINE_LOAD, updatedLoad);

        BucketPose preMovePose = ArmKinematics.computeBucketPose(
                this.position(), this.getYRot(),
                this.getVehiclePitch(), this.getVehicleRoll(),
                constrained.cabin(), constrained.boom(), constrained.stick(), constrained.bucket(),
                this.getBucketType());
        boolean teethEmbedded = ArmTerrainContactController.areTeethEmbedded(
                serverLevel, preMovePose);

        float allowedThrottle = this.inputThrottle;
        float allowedSteer = this.inputSteer;
        if (teethEmbedded) {
            this.trackController.lockDifferentialMotion();
            allowedSteer = 0.0F;
            double yawRadians = Math.toRadians(this.getYRot());
            Vec3 requestedTravel = new Vec3(
                    -Math.sin(yawRadians) * allowedThrottle,
                    0.0D,
                    Math.cos(yawRadians) * allowedThrottle);
            // Wyjazd w tył (throttle < 0) jest ZAWSZE dozwolony - odblokowuje wycofanie koparki z urobku!
            if (allowedThrottle > 0.0F && ArmTerrainContactController.isBlockedLateralDrag(
                    true, requestedTravel, preMovePose.forwardCutting())) {
                allowedThrottle = 0.0F;
                this.trackController.stopMotion();
            }
        }

        // 3. Update differential track physics and terrain conformity
        TrackMovementController.TrackState trackState = this.trackController.tick(
                serverLevel,
                this.position(),
                this.getYRot(),
                allowedThrottle,
                allowedSteer,
                this.onGround()
        );

        Vec3 candidateBase = this.position().add(trackState.forwardDelta());
        float candidateYaw = this.getYRot() + trackState.yawDeltaDegrees();
        if (!ArmTerrainContactController.allowsMachineMotion(
                serverLevel,
                this.position(), this.getYRot(),
                this.getVehiclePitch(), this.getVehicleRoll(),
                candidateBase, candidateYaw,
                trackState.pitch(), trackState.roll(),
                this.getBucketType(), constrained)) {
            this.trackController.stopMotion();
            trackState = new TrackMovementController.TrackState(
                    0.0F, 0.0F, Vec3.ZERO, 0.0F,
                    this.getVehiclePitch(), this.getVehicleRoll());
        }

        this.entityData.set(TRACK_LEFT_SPEED, trackState.leftSpeed());
        this.entityData.set(TRACK_RIGHT_SPEED, trackState.rightSpeed());
        this.entityData.set(VEHICLE_PITCH, trackState.pitch());
        this.entityData.set(VEHICLE_ROLL, trackState.roll());

        // Apply yaw rotation
        this.setYRot(this.getYRot() + trackState.yawDeltaDegrees());
        this.setYHeadRot(this.getYRot());
        this.setYBodyRot(this.getYRot());

        // Apply translation movement with gravity
        Vec3 movement = trackState.forwardDelta();
        if (!this.onGround()) {
            movement = movement.add(0.0D, -0.08D, 0.0D);
        } else {
            movement = movement.add(0.0D, -0.02D, 0.0D); // Keep tracks grounded
        }
        this.setDeltaMovement(movement);
        this.move(MoverType.SELF, movement);

        // Force position synchronization to passengers and tracking clients while driving
        if (Math.abs(trackState.leftSpeed()) > 0.001F || Math.abs(trackState.rightSpeed()) > 0.001F || Math.abs(trackState.yawDeltaDegrees()) > 0.01F) {
            this.syncPosition = true;
            this.needsSync = true;
        }

        // 4. Compute forward kinematics for current tick
        this.currentBucketPose = ArmKinematics.computeBucketPose(
                this.position(),
                this.getYRot(),
                this.getVehiclePitch(),
                this.getVehicleRoll(),
                this.getUpperYaw(),
                this.getBoomAngle(),
                this.getStickAngle(),
                this.getBucketAngle(),
                this.getBucketType()
        );

        // Initialize previous pose on spawn / chunk load
        if (this.previousBucketPose == null) {
            this.previousBucketPose = this.currentBucketPose;
        }

        // 5-6. Run exactly one working attachment. The hammer never feeds the
        // bucket container; crushed cobblestone is displaced directly into the world.
        if (this.isHammerAttachment()) {
            HydraulicHammerController.HammerTickResult hammerResult =
                    HydraulicHammerController.tick(
                            serverLevel,
                            this.currentBucketPose,
                            hammerRequested,
                            this.tickCount);

            this.lastExcavatedUnits = hammerResult.unitsCrushed();
            this.lastDepositedUnits = hammerResult.unitsCrushed();
            this.entityData.set(IS_DIGGING, false);
            this.entityData.set(IS_DUMPING, false);
            this.entityData.set(IS_HAMMERING, hammerRequested);
        } else {
            this.entityData.set(IS_HAMMERING, false);

            BucketExcavationController.ExcavationTickResult digResult =
                    BucketExcavationController.tick(
                            serverLevel,
                            this.bucket,
                            this.previousBucketPose,
                            this.currentBucketPose,
                            this.autoTrenchController.prefersBucketIntake());

            this.lastExcavatedUnits = digResult.unitsExcavated();
            this.entityData.set(IS_DIGGING, digResult.excavated());

            // AutoTrench owns its dump timing, so retry/penetration poses cannot
            // spill a partial load merely because the bucket crosses the dump angle.
            // DumpTruck mode qualifies the receiver from the fixed +50-degree,
            // rear-facing, closed-bucket check pose. Once qualified, that receiver
            // remains the transfer target while the bucket lip moves as it opens.
            BucketDumpingController.DumpTickResult dumpResult;
            if (!this.autoTrenchController.allowsBucketDumping()) {
                dumpResult = BucketDumpingController.DumpTickResult.NONE;
            } else if (this.autoTrenchController.isDumpTruckMode()) {
                IMobileWorldGranularContainer dumpReceiver =
                        this.findDumpTruckUnderPlannedRearDumpLip(serverLevel);
                dumpResult = dumpReceiver != null
                        ? BucketDumpingController.tickToReceiver(
                                serverLevel,
                                this,
                                this.bucket,
                                this.currentBucketPose,
                                dumpReceiver
                        )
                        : BucketDumpingController.DumpTickResult.NONE;
            } else {
                dumpResult = BucketDumpingController.tick(
                        serverLevel,
                        this,
                        this.bucket,
                        this.currentBucketPose
                );
            }

            this.lastDepositedUnits = dumpResult.unitsDeposited();
            this.entityData.set(IS_DUMPING, dumpResult.dumping());
        }

        // 7. Synchronize authoritative bucket state to clients
        this.entityData.set(MATERIAL_ID, this.bucket.materialId());
        this.entityData.set(STORED_UNITS, this.bucket.storedUnits());
        this.entityData.set(CAPACITY, this.bucket.capacity());

        // 8. Commit current pose as previous for next tick's swept volume
        this.previousBucketPose = this.currentBucketPose;
    }

    /**
     * Called when receiving authoritative driver input from the client.
     */
    public void setControlInputs(
            float throttle,
            float steer,
            float upperYawInput,
            float boomInput,
            float stickInput,
            float bucketInput
    ) {
        this.inputThrottle = Mth.clamp(throttle, -1.0F, 1.0F);
        this.inputSteer = Mth.clamp(steer, -1.0F, 1.0F);
        this.inputCabYaw = Mth.clamp(upperYawInput, -1.0F, 1.0F);
        this.inputBoom = Mth.clamp(boomInput, -1.0F, 1.0F);
        this.inputStick = Mth.clamp(stickInput, -1.0F, 1.0F);
        this.inputBucket = Mth.clamp(bucketInput, -1.0F, 1.0F);
        this.inputFreshTicks = 10;
    }

    public void setHammerInput(boolean active) {
        this.inputHammerActive = active && this.isHammerAttachment();
    }

    /**
     * C is a horn while a digging bucket is installed. The hydraulic hammer keeps
     * its existing C behavior when the hammer attachment is selected.
     */
    public void setHornInput(boolean active) {
        if (this.isHammerAttachment()) {
            this.hornInputLast = false;
            return;
        }

        boolean risingEdge = active && !this.hornInputLast;
        this.hornInputLast = active;

        if (risingEdge && !this.level().isClientSide()) {
            this.honkAndDispatchNearestTruck((ServerLevel) this.level());
        }
    }

    private void honkAndDispatchNearestTruck(ServerLevel level) {
        this.playHorn(level);

        long now = level.getGameTime();
        if (now - this.lastHornTick <= DOUBLE_HORN_WINDOW_TICKS) {
            IMobileWorldGranularContainer nearest = this.findNearestMobileContainer(
                    level,
                    HORN_TRUCK_SEARCH_RADIUS
            );
            if (nearest != null) {
                nearest.requestAdvance(1.0D);
            }
            this.lastHornTick = Long.MIN_VALUE / 4L;
        } else {
            this.lastHornTick = now;
        }
    }

    private void playHorn(ServerLevel level) {
        level.playSeededSound(
                null,
                getX(),
                getY() + 1.4D,
                getZ(),
                SoundEvents.RAID_HORN,
                SoundSource.BLOCKS,
                0.55F,
                1.45F,
                level.getRandom().nextLong()
        );
    }

    /**
     * End-of-station handshake for DumpTruck AutoTrench:
     * two short horn blasts -> truck advances exactly one block -> excavator reverses.
     */
    private AutoTrenchController.Controls tickAutomaticTruckAdvanceBeforeReverse(
            ServerLevel level,
            AutoTrenchController.Snapshot snapshot,
            @Nullable IMobileWorldGranularContainer rearTruck
    ) {
        if (rearTruck == null) {
            return AutoTrenchController.Controls.STOPPED;
        }

        if (this.autoTruckAdvanceStage == AUTO_TRUCK_STAGE_IDLE) {
            this.playHorn(level);
            this.autoTruckHornDelayTicks = AUTO_SECOND_HORN_DELAY_TICKS;
            this.autoTruckAdvanceStage = AUTO_TRUCK_STAGE_WAIT_SECOND_HORN;
            return AutoTrenchController.Controls.STOPPED;
        }

        if (this.autoTruckAdvanceStage == AUTO_TRUCK_STAGE_WAIT_SECOND_HORN) {
            if (--this.autoTruckHornDelayTicks > 0) {
                return AutoTrenchController.Controls.STOPPED;
            }

            this.playHorn(level);
            if (rearTruck.requestAdvance(1.0D) || rearTruck.isAdvanceInProgress()) {
                this.autoTruckAdvanceStage = AUTO_TRUCK_STAGE_WAIT_TRUCK_MOVE;
            } else {
                // The signal has already been given. Do not spam the horn if the
                // receiver is temporarily unable to accept the move request.
                this.autoTruckAdvanceStage = AUTO_TRUCK_STAGE_WAIT_REQUEST_ACCEPT;
            }
            return AutoTrenchController.Controls.STOPPED;
        }

        if (this.autoTruckAdvanceStage == AUTO_TRUCK_STAGE_WAIT_REQUEST_ACCEPT) {
            if (rearTruck.requestAdvance(1.0D) || rearTruck.isAdvanceInProgress()) {
                this.autoTruckAdvanceStage = AUTO_TRUCK_STAGE_WAIT_TRUCK_MOVE;
            }
            return AutoTrenchController.Controls.STOPPED;
        }

        if (this.autoTruckAdvanceStage == AUTO_TRUCK_STAGE_WAIT_TRUCK_MOVE) {
            if (rearTruck.isAdvanceInProgress()) {
                return AutoTrenchController.Controls.STOPPED;
            }

            // One settle tick after the truck stops prevents both machines from
            // starting their movement in the same server tick.
            this.autoTruckAdvanceStage = AUTO_TRUCK_STAGE_READY_TO_REVERSE;
            return AutoTrenchController.Controls.STOPPED;
        }

        AutoTrenchController.Controls controls = this.autoTrenchController.tick(
                snapshot,
                true
        );

        if (this.autoTrenchController.phase() != AutoTrenchController.Phase.RESET_AND_REVERSE) {
            this.resetAutomaticTruckAdvanceSequence();
        }

        return controls;
    }

    private void resetAutomaticTruckAdvanceSequence() {
        this.autoTruckAdvanceStage = AUTO_TRUCK_STAGE_IDLE;
        this.autoTruckHornDelayTicks = 0;
    }

    @Nullable
    private IMobileWorldGranularContainer findDumpTruckUnderPlannedRearDumpLip(
            ServerLevel level
    ) {
        BucketPose plannedDumpPose = ArmKinematics.computeBucketPose(
                this.position(),
                this.getYRot(),
                this.getVehiclePitch(),
                this.getVehicleRoll(),
                AutoTrenchController.REAR_DUMP_YAW,
                AutoTrenchController.REAR_DUMP_CHECK_BOOM,
                AutoTrenchController.DUMP_STICK,
                AutoTrenchController.HELD_BUCKET,
                this.getBucketType()
        );

        Vec3 plannedLip = plannedDumpPose.lip();
        IWorldGranularContainer receiver = GranularContainerTransferApi.findReceiver(
                level,
                plannedLip,
                this,
                4.0D
        );

        return receiver instanceof IMobileWorldGranularContainer mobile
                ? mobile
                : null;
    }

    @Nullable
    private IMobileWorldGranularContainer findNearestMobileContainer(
            ServerLevel level,
            double radius
    ) {
        AABB area = this.getBoundingBox().inflate(radius, 4.0D, radius);
        Entity nearestEntity = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Entity candidate : level.getEntitiesOfClass(
                Entity.class,
                area,
                entity -> entity != this
                        && entity instanceof IMobileWorldGranularContainer
        )) {
            double distance = candidate.distanceToSqr(getX(), getY(), getZ());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestEntity = candidate;
            }
        }

        return nearestEntity instanceof IMobileWorldGranularContainer mobile
                ? mobile
                : null;
    }

    @Nullable
    private IMobileWorldGranularContainer findRearMobileContainer(ServerLevel level) {
        double yawRad = Math.toRadians(this.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        Vec3 rear = forward.scale(-1.0D);
        Vec3 right = new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad));

        AABB area = this.getBoundingBox().inflate(
                REAR_TRUCK_MAX_DISTANCE,
                4.0D,
                REAR_TRUCK_MAX_DISTANCE
        );

        Entity nearestEntity = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Entity candidate : level.getEntitiesOfClass(
                Entity.class,
                area,
                entity -> entity != this
                        && entity instanceof IMobileWorldGranularContainer
        )) {
            Vec3 delta = candidate.position().subtract(this.position());
            double behind = delta.dot(rear);
            double lateral = Math.abs(delta.dot(right));

            if (behind >= 1.5D
                    && behind <= REAR_TRUCK_MAX_DISTANCE
                    && lateral <= REAR_TRUCK_MAX_LATERAL
                    && Math.abs(delta.y) <= 2.5D) {
                double distance = candidate.distanceToSqr(getX(), getY(), getZ());
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

    private boolean hasRearDumpTruck(ServerLevel level) {
        return this.findRearMobileContainer(level) != null;
    }

    public void startAutoTrench() {
        startAutoTrench(
                AutoTrenchController.DEFAULT_MAX_TRENCH_DEPTH,
                AutoTrenchController.DEFAULT_CUTS_PER_STATION,
                AutoTrenchController.DEFAULT_LEFT_EXPANSION_BLOCKS,
                AutoTrenchController.DEFAULT_LEFT_EXPANSION_CYCLES
        );
    }

    public void startAutoTrench(float depthBlocks, int cutsBeforeReverse) {
        startAutoTrench(
                depthBlocks,
                cutsBeforeReverse,
                AutoTrenchController.DEFAULT_LEFT_EXPANSION_BLOCKS,
                AutoTrenchController.DEFAULT_LEFT_EXPANSION_CYCLES
        );
    }

    public void startAutoTrench(float depthBlocks, int cutsCenter, float expandLeftBlocks, int cutsLeft) {
        if (this.level().isClientSide()) return;
        this.setBucketType(BUCKET_LARGE);
        this.setControlMode(MODE_EXCAVATOR);
        this.cabinBlockedLastTick = false;
        this.autoTrenchController.start(depthBlocks, cutsCenter, expandLeftBlocks, cutsLeft);
    }

    public void startAutoTrenchDumpTruck(
            float depthBlocks,
            int cutsCenter,
            float expandLeftBlocks,
            int cutsLeft
    ) {
        if (this.level().isClientSide()) return;
        this.setBucketType(BUCKET_LARGE);
        this.setControlMode(MODE_EXCAVATOR);
        this.cabinBlockedLastTick = false;
        this.resetAutomaticTruckAdvanceSequence();
        this.autoTrenchController.startWithDumpTruck(
                depthBlocks,
                cutsCenter,
                expandLeftBlocks,
                cutsLeft
        );
    }

    public void stopAutoTrench() {
        this.cabinBlockedLastTick = false;
        this.resetAutomaticTruckAdvanceSequence();
        this.autoTrenchController.stop();
        this.setControlInputs(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    public boolean isAutoTrenchActive() {
        return this.autoTrenchController.isActive();
    }

    public boolean isAutoTrenchDumpTruckMode() {
        return this.autoTrenchController.isDumpTruckMode();
    }

    public AutoTrenchController.Phase getAutoTrenchPhase() {
        return this.autoTrenchController.phase();
    }

    public int getAutoTrenchCompletedSections() {
        return this.autoTrenchController.completedSections();
    }

    public int getAutoTrenchCutsAtStation() {
        return this.autoTrenchController.cutsAtCurrentStation();
    }

    public int getAutoTrenchMaxCutsPerStation() {
        return this.autoTrenchController.maxCutsPerStation();
    }

    public float getAutoTrenchMaxDepth() {
        return this.autoTrenchController.maxDiggingDepth();
    }

    public float getAutoTrenchLeftExpansionBlocks() {
        return this.autoTrenchController.leftExpansionBlocks();
    }

    public int getAutoTrenchLeftExpansionCycles() {
        return this.autoTrenchController.leftExpansionCycles();
    }

    public boolean isAutoTrenchInLeftPass() {
        return this.autoTrenchController.isInLeftExpansionPass();
    }

    private float queryTrenchDepth(ServerLevel level) {
        float workYawDeg = this.autoTrenchController.currentWorkCabinYaw();
        float yawRad = (float) Math.toRadians(this.getYRot() - workYawDeg);
        Vec3 fwd = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        // Sonda sprawdza punkt roboczy przed krawędzią tnącą (zasięg ~4.5m)
        Vec3 probePoint = this.position().add(fwd.scale(4.5D));
        int probeX = BlockPos.containing(probePoint).getX();
        int probeZ = BlockPos.containing(probePoint).getZ();
        int baseY = (int) Math.floor(this.getY());

        for (int y = baseY + 1; y >= baseY - 3; y--) {
            BlockPos pos = new BlockPos(probeX, y, probeZ);
            if (GroundworksExcavationAdapter.isDiggable(level, pos)) {
                double surfaceY = GroundworksExcavationAdapter.getSurfaceWorldY(
                        level, pos, probePoint.x, probePoint.z);
                if (Double.isFinite(surfaceY)) {
                    return (float) Math.max(0.0D, this.getY() - surfaceY);
                }
            }
            if (level.getBlockState(pos).isSolid()) {
                double surfaceY = pos.getY() + 1.0D;
                return (float) Math.max(0.0D, this.getY() - surfaceY);
            }
        }
        return 0.0F;
    }

    // ── Driver & Passenger Interaction ────────────────────────────────

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }

        if (!this.level().isClientSide()) {
            if (this.getPassengers().isEmpty()) {
                player.startRiding(this);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof LivingEntity && this.getPassengers().isEmpty();
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        Entity first = this.getFirstPassenger();
        return first instanceof LivingEntity living ? living : null;
    }

    public boolean isDriver(Entity entity) {
        return entity != null && entity == this.getControllingPassenger();
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return ArmKinematics.getDriverSeatWorldPosition(
                this.position(),
                this.getYRot(),
                this.getUpperYaw()
        );
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return this.getPassengerRidingPosition(passenger).subtract(this.position());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // Place dismounted passenger safely to the left side of the machine
        double yawRad = Math.toRadians(this.getYRot());
        Vec3 left = new Vec3(-Math.cos(yawRad), 0.0D, -Math.sin(yawRad));
        return this.position().add(left.scale(2.0D)).add(0.0D, 0.25D, 0.0D);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.isInvulnerable()) {
            return false;
        }
        if (source.getEntity() instanceof Player player) {
            if (player.getAbilities().instabuild) {
                this.discard();
                return true;
            }
            this.spawnAtLocation(level, GroundworksExcavatorMod.EXCAVATOR_ITEM);
            this.discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isClientAuthoritative() {
        return false;
    }

    @Override
    protected boolean isLocalClientAuthoritative() {
        return false;
    }

    @Override
    public float maxUpStep() {
        return 1.25F;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return other != null && !this.hasPassenger(other);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // ── Getters for Renderers & Controllers ───────────────────────────

    public float getMachineLoad() {
        return this.entityData.get(MACHINE_LOAD);
    }

    public boolean isOperating() {
        return this.getFirstPassenger() != null
                || Math.abs(this.getTrackLeftSpeed()) > 0.001F
                || Math.abs(this.getTrackRightSpeed()) > 0.001F
                || this.isDigging()
                || this.isDumping()
                || this.isHammering();
    }

    public int getControlMode() {
        return this.entityData.get(CONTROL_MODE);
    }

    public void setControlMode(int mode) {
        this.entityData.set(CONTROL_MODE, mode);
    }

    public boolean isDriveMode() {
        return this.getControlMode() == MODE_DRIVE;
    }

    public boolean isExcavatorMode() {
        return this.getControlMode() == MODE_EXCAVATOR;
    }

    public int getBucketType() {
        return this.entityData.get(BUCKET_TYPE);
    }

    public void setBucketType(int type) {
        int clamped = (type == BUCKET_LARGE || type == BUCKET_HAMMER)
                ? type
                : BUCKET_STANDARD;
        this.entityData.set(BUCKET_TYPE, clamped);

        // Clamp joint angle immediately to the active tool's physical limits so attachments never clip into the stick/boom
        float currentAngle = this.getBucketAngle();
        float minAngle = ArmKinematics.getMinBucketAngle(clamped);
        float maxAngle = ArmKinematics.getMaxBucketAngle(clamped);
        if (currentAngle < minAngle) {
            this.entityData.set(BUCKET_ANGLE, minAngle);
        } else if (currentAngle > maxAngle) {
            this.entityData.set(BUCKET_ANGLE, maxAngle);
        }

        // The detached bucket keeps its stored material while the hammer is fitted.
        // Capacity changes only when an actual bucket is selected.
        if (clamped != BUCKET_HAMMER) {
            int targetCap = (clamped == BUCKET_LARGE) ? CAPACITY_LARGE : CAPACITY_STANDARD;
            this.bucket.setCapacity(targetCap);
            this.entityData.set(CAPACITY, targetCap);
            this.inputHammerActive = false;
            this.entityData.set(IS_HAMMERING, false);
        } else {
            this.entityData.set(CAPACITY, this.bucket.capacity());
        }
    }

    public void toggleBucketType() {
        setBucketType((getBucketType() + 1) % 3);
    }

    public boolean isHammerAttachment() {
        return this.getBucketType() == BUCKET_HAMMER;
    }

    public float getTrackLeftSpeed() {
        return this.entityData.get(TRACK_LEFT_SPEED);
    }

    public float getTrackRightSpeed() {
        return this.entityData.get(TRACK_RIGHT_SPEED);
    }

    public float getUpperYaw() {
        return this.entityData.get(UPPER_YAW);
    }

    public float getBoomAngle() {
        return this.entityData.get(BOOM_ANGLE);
    }

    public float getStickAngle() {
        return this.entityData.get(STICK_ANGLE);
    }

    public float getBucketAngle() {
        return this.entityData.get(BUCKET_ANGLE);
    }

    public int getStoredUnits() {
        return this.entityData.get(STORED_UNITS);
    }

    public int getBucketCapacity() {
        return this.entityData.get(CAPACITY);
    }

    public int getBucketMaterialId() {
        return this.entityData.get(MATERIAL_ID);
    }

    public GranularMaterial getBucketMaterial() {
        return GranularMaterialRegistry.byId(this.getBucketMaterialId());
    }

    public float getVehiclePitch() {
        return this.entityData.get(VEHICLE_PITCH);
    }

    public float getVehicleRoll() {
        return this.entityData.get(VEHICLE_ROLL);
    }

    public boolean isDigging() {
        return this.entityData.get(IS_DIGGING);
    }

    public boolean isDumping() {
        return this.entityData.get(IS_DUMPING);
    }

    public boolean isHammering() {
        return this.entityData.get(IS_HAMMERING);
    }

    public BucketMaterialContainer getBucket() {
        return this.bucket;
    }

    @Nullable
    public BucketPose getCurrentBucketPose() {
        return this.currentBucketPose;
    }

    @Nullable
    public BucketPose getPreviousBucketPose() {
        return this.previousBucketPose;
    }

    public void setPreviousBucketPose(BucketPose pose) {
        this.previousBucketPose = pose;
    }

    public int getLastExcavatedUnits() {
        return this.lastExcavatedUnits;
    }

    public int getLastDepositedUnits() {
        return this.lastDepositedUnits;
    }

    // ── Persistence (Minecraft 26.3 ValueInput / ValueOutput) ─────────

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setControlMode(input.getIntOr("ControlMode", MODE_DRIVE));
        int loadedBucketType = input.getIntOr("BucketType", BUCKET_STANDARD);
        this.setBucketType(loadedBucketType);
        this.entityData.set(UPPER_YAW, input.getFloatOr("UpperYaw", 0.0F));
        this.entityData.set(BOOM_ANGLE, input.getFloatOr("BoomAngle", 15.0F));
        this.entityData.set(STICK_ANGLE, input.getFloatOr("StickAngle", -35.0F));
        float loadedBucketAngle = input.getFloatOr("BucketAngle", -10.0F);
        float minB = ArmKinematics.getMinBucketAngle(loadedBucketType);
        float maxB = ArmKinematics.getMaxBucketAngle(loadedBucketType);
        this.entityData.set(BUCKET_ANGLE, Mth.clamp(loadedBucketAngle, minB, maxB));
        this.entityData.set(VEHICLE_PITCH, input.getFloatOr("VehiclePitch", 0.0F));
        this.entityData.set(VEHICLE_ROLL, input.getFloatOr("VehicleRoll", 0.0F));

        this.bucket.load(input);
        this.entityData.set(CAPACITY, this.bucket.capacity());
        this.entityData.set(STORED_UNITS, this.bucket.storedUnits());
        this.entityData.set(MATERIAL_ID, this.bucket.materialId());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("ControlMode", this.getControlMode());
        output.putInt("BucketType", this.getBucketType());
        output.putFloat("UpperYaw", this.getUpperYaw());
        output.putFloat("BoomAngle", this.getBoomAngle());
        output.putFloat("StickAngle", this.getStickAngle());
        output.putFloat("BucketAngle", this.getBucketAngle());
        output.putFloat("VehiclePitch", this.getVehiclePitch());
        output.putFloat("VehicleRoll", this.getVehicleRoll());

        this.bucket.save(output);
    }
}
