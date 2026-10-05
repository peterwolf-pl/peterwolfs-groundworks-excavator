package com.piotrek.groundworksexcavator.automation;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import net.minecraft.world.phys.Vec3;

/**
 * Deterministic server-side motion plan used by the trench automation.
 *
 * <p>One cycle positions the bucket, performs an inward digging stroke
 * (forearm crowd + bucket curl) with depth automatically adapted to trench depth,
 * lifts and swings the load to the machine's right, dumps with straightened forearm
 * and fully opened bucket, then reverses one block after completing station cuts.</p>
 */
public final class AutoTrenchController {

    public static final float WORK_CABIN_YAW = 0.0F;
    public static final float RIGHT_DUMP_YAW = 90.0F;

    public static final float SAFE_BOOM = 48.0F;
    public static final float TUCKED_STICK = -80.0F;
    public static final float SAFE_STICK = -45.0F; // Forearm stays extended during safe transit
    public static final float HELD_BUCKET = ArmKinematics.BUCKET_MIN; // -60.0F: zamykaj łyżkę do końca!

    // Dumping: forearm extended out nicely and bucket opened all the way
    public static final float DUMP_BOOM = 34.0F;
    public static final float DUMP_STICK = -50.0F;
    public static final float DUMP_BUCKET = ArmKinematics.BUCKET_MAX; // 100.0F

    public static final float OPEN_BUCKET = ArmKinematics.BUCKET_MAX; // 100.0F - otwarcie łyżki na maxa!
    public static final float APPROACH_BOOM = 18.0F;
    public static final float APPROACH_STICK = -45.0F;
    public static final float APPROACH_BUCKET = OPEN_BUCKET;

    // Cut depth adaptation: initial pass is shallow (~0.38m), full cut reaches ~0.95m
    public static final float INITIAL_CUT_DEPTH = 0.38F;
    public static final float INITIAL_PENETRATE_BOOM = 11.8F;
    public static final float FULL_CUT_DEPTH = 0.95F;
    public static final float FULL_PENETRATE_BOOM = 5.5F;

    // Backwards-compatible constants
    public static final float PENETRATE_BOOM = INITIAL_PENETRATE_BOOM;
    public static final float PENETRATE_STICK = -45.0F;
    public static final float PENETRATE_BUCKET = OPEN_BUCKET;

    // Digging stroke: after lowering into ground, stick pulls inward ("sciagac przedramie") and bucket curls/closes
    public static final float CUT_STICK = -75.0F;
    public static final float CUT_BUCKET = HELD_BUCKET;
    public static final float SCOOP_STICK = -85.0F;

    public static final int CUTS_PER_STATION = 2;
    public static final float DEFAULT_MAX_TRENCH_DEPTH = FULL_CUT_DEPTH;
    public static final int DEFAULT_CUTS_PER_STATION = CUTS_PER_STATION;
    public static final float DEFAULT_LEFT_EXPANSION_BLOCKS = 0.0F;
    public static final int DEFAULT_LEFT_EXPANSION_CYCLES = 0;

    private int maxCutsPerStation = DEFAULT_CUTS_PER_STATION;
    private float maxDiggingDepth = DEFAULT_MAX_TRENCH_DEPTH;
    private float leftExpansionBlocks = DEFAULT_LEFT_EXPANSION_BLOCKS;
    private int leftExpansionCycles = DEFAULT_LEFT_EXPANSION_CYCLES;
    private boolean inLeftExpansionPass = false;
    public static final int STALL_THRESHOLD_TICKS = 8;
    public static final int STALL_RELIEF_TICKS = 4;
    public static final int MAX_STALL_RETRIES = 3;

    // Load quality rules for the automatic digging cycle. A normal cycle aims for at least
    // 75% fill. A partially filled bucket is retried several times before a fallback dump.
    public static final float TARGET_FILL_RATIO = 0.75F;
    public static final float MIN_ACCEPTABLE_FILL_RATIO = 0.50F;
    public static final int MAX_LOW_FILL_RETRIES = 5;

    private static final float ANGLE_TOLERANCE = 1.25F;
    private static final float REVERSE_THROTTLE = -0.65F;
    private static final double REVERSE_DISTANCE = 1.0D;
    private static final int ROTATION_SETTLE_TICKS = 2;
    private static final int POSITION_SETTLE_TICKS = 3;
    private static final int PENETRATION_SETTLE_TICKS = 2;
    private static final int CUT_SETTLE_TICKS = 2;
    private static final int SCOOP_SETTLE_TICKS = 2;

    public enum Phase {
        POSITION_FOR_CUT,
        REOPEN_AND_RESET_ARM,
        PENETRATE_FOR_CUT,
        CUT_AND_CURL,
        CLOSE_BUCKET_IN_TRENCH,
        SCOOP_AND_CURL,
        RELIEVE_STALL,
        LIFT_AND_SWING_RIGHT,
        DUMP_RIGHT,
        RESET_AND_REVERSE
    }

    public record Snapshot(
            boolean occupied,
            float cabin,
            float boom,
            float stick,
            float bucket,
            int storedUnits,
            int bucketCapacity,
            Vec3 position,
            float baseYaw,
            boolean cabinBlocked,
            float trenchDepth
    ) {
        public Snapshot(
                boolean occupied,
                float cabin,
                float boom,
                float stick,
                float bucket,
                int storedUnits,
                Vec3 position,
                float baseYaw
        ) {
            this(occupied, cabin, boom, stick, bucket, storedUnits, 512, position, baseYaw, false, 0.0F);
        }

        public Snapshot(
                boolean occupied,
                float cabin,
                float boom,
                float stick,
                float bucket,
                int storedUnits,
                Vec3 position,
                float baseYaw,
                boolean cabinBlocked,
                float trenchDepth
        ) {
            this(occupied, cabin, boom, stick, bucket, storedUnits, 512, position, baseYaw, cabinBlocked, trenchDepth);
        }

        public boolean isBucketFull() {
            int cap = bucketCapacity > 0 ? bucketCapacity : 512;
            return storedUnits >= Math.max(128, cap - 32);
        }

        public float fillRatio() {
            int cap = bucketCapacity > 0 ? bucketCapacity : 512;
            return Math.clamp((float) storedUnits / (float) cap, 0.0F, 1.0F);
        }

        public boolean hasTargetLoad() {
            return fillRatio() >= TARGET_FILL_RATIO;
        }

        public boolean hasMinimumAcceptableLoad() {
            return fillRatio() >= MIN_ACCEPTABLE_FILL_RATIO;
        }

        public boolean isBucketHalfFull() {
            return hasMinimumAcceptableLoad();
        }
    }

    public record Controls(
            float throttle,
            float steer,
            float cabYaw,
            float boom,
            float stick,
            float bucket
    ) {
        public static final Controls STOPPED = new Controls(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    private boolean active;
    private Phase phase = Phase.POSITION_FOR_CUT;
    private int settledTicks;
    private int completedSections;
    private int cutsAtCurrentStation;
    private int retriesAtCurrentStation;
    private int lowFillRetriesAtCurrentStation;
    private int stallTicks;
    private int reliefTicks;
    private int rotationStallTicks;
    private int rotationSettleTicks;
    private boolean rotationOverWorkAreaComplete;
    private float swingObstacleBoomBoost;
    private float prevCabin;
    private float prevBoom;
    private float prevStick;
    private float prevBucket;
    private Vec3 reverseOrigin;

    public void start() {
        start(DEFAULT_MAX_TRENCH_DEPTH, DEFAULT_CUTS_PER_STATION, DEFAULT_LEFT_EXPANSION_BLOCKS, DEFAULT_LEFT_EXPANSION_CYCLES);
    }

    public void start(float targetDepthBlocks, int cutsBeforeReverse) {
        start(targetDepthBlocks, cutsBeforeReverse, DEFAULT_LEFT_EXPANSION_BLOCKS, DEFAULT_LEFT_EXPANSION_CYCLES);
    }

    public void start(float targetDepthBlocks, int cutsCenter, float expandLeftBlocks, int cutsLeft) {
        this.active = true;
        this.phase = Phase.POSITION_FOR_CUT;
        this.maxDiggingDepth = Math.max(0.2F, targetDepthBlocks);
        this.maxCutsPerStation = Math.max(1, cutsCenter);
        this.leftExpansionBlocks = Math.max(0.0F, expandLeftBlocks);
        this.leftExpansionCycles = Math.max(0, cutsLeft);
        this.inLeftExpansionPass = false;
        this.settledTicks = 0;
        this.completedSections = 0;
        this.cutsAtCurrentStation = 0;
        this.retriesAtCurrentStation = 0;
        this.lowFillRetriesAtCurrentStation = 0;
        this.stallTicks = 0;
        this.reliefTicks = 0;
        this.rotationStallTicks = 0;
        this.rotationSettleTicks = 0;
        this.rotationOverWorkAreaComplete = false;
        this.swingObstacleBoomBoost = 0.0F;
        this.reverseOrigin = null;
    }

    public void stop() {
        this.active = false;
        this.inLeftExpansionPass = false;
        this.settledTicks = 0;
        this.cutsAtCurrentStation = 0;
        this.retriesAtCurrentStation = 0;
        this.lowFillRetriesAtCurrentStation = 0;
        this.stallTicks = 0;
        this.reliefTicks = 0;
        this.rotationStallTicks = 0;
        this.rotationSettleTicks = 0;
        this.rotationOverWorkAreaComplete = false;
        this.swingObstacleBoomBoost = 0.0F;
        this.reverseOrigin = null;
    }

    public Controls tick(Snapshot state) {
        if (!active) return Controls.STOPPED;
        if (!state.occupied()) {
            stop();
            return Controls.STOPPED;
        }

        if (phase == Phase.POSITION_FOR_CUT && state.storedUnits() > 0) {
            // A leftover partial load must not enter LIFT_AND_SWING_RIGHT and deadlock there.
            // Keep it secured and perform another digging stroke unless it is already useful.
            if (state.hasMinimumAcceptableLoad()) {
                changePhase(Phase.LIFT_AND_SWING_RIGHT);
            } else {
                lowFillRetriesAtCurrentStation = Math.max(lowFillRetriesAtCurrentStation, 1);
                changePhase(Phase.REOPEN_AND_RESET_ARM);
            }
        }

        boolean isDiggingPhase = phase == Phase.PENETRATE_FOR_CUT
                || phase == Phase.CUT_AND_CURL
                || phase == Phase.CLOSE_BUCKET_IN_TRENCH
                || phase == Phase.SCOOP_AND_CURL;

        // Gdy łyżka jest już pełna, natychmiast domykamy łyżkę na dnie wykopu zanim zaczniemy podnosić ramię w górę
        if (isDiggingPhase && state.isBucketFull() && phase != Phase.CLOSE_BUCKET_IN_TRENCH) {
            changePhase(Phase.CLOSE_BUCKET_IN_TRENCH);
        }

        if (isDiggingPhase) {
            float movement = Math.abs(state.boom() - prevBoom)
                    + Math.abs(state.stick() - prevStick)
                    + Math.abs(state.bucket() - prevBucket)
                    + Math.abs(state.cabin() - prevCabin);
            if (movement < 0.05F) {
                stallTicks++;
                if (stallTicks >= STALL_THRESHOLD_TICKS) {
                    changePhase(Phase.RELIEVE_STALL);
                    stallTicks = 0;
                    reliefTicks = 0;
                }
            } else {
                stallTicks = 0;
            }
        } else if (phase != Phase.RELIEVE_STALL) {
            stallTicks = 0;
        }

        prevCabin = state.cabin();
        prevBoom = state.boom();
        prevStick = state.stick();
        prevBucket = state.bucket();

        return switch (phase) {
            case POSITION_FOR_CUT -> positionForCut(state);
            case REOPEN_AND_RESET_ARM -> reopenAndResetArm(state);
            case PENETRATE_FOR_CUT -> penetrateForCut(state);
            case CUT_AND_CURL -> cutAndCurl(state);
            case CLOSE_BUCKET_IN_TRENCH -> closeBucketInTrench(state);
            case SCOOP_AND_CURL -> scoopAndCurl(state);
            case RELIEVE_STALL -> relieveStall(state);
            case LIFT_AND_SWING_RIGHT -> liftAndSwingRight(state);
            case DUMP_RIGHT -> dumpRight(state);
            case RESET_AND_REVERSE -> resetAndReverse(state);
        };
    }

    public static float calculateCutBoom(float trenchDepth, int cutsAtCurrentStation) {
        return calculateCutBoom(trenchDepth, cutsAtCurrentStation, FULL_CUT_DEPTH);
    }

    public static float calculateCutBoom(float trenchDepth, int cutsAtCurrentStation, float targetMaxDepth) {
        return calculateCutBoom(trenchDepth, cutsAtCurrentStation, targetMaxDepth, 0);
    }

    public static float calculateCutBoom(float trenchDepth, int cutsAtCurrentStation, float targetMaxDepth, int retries) {
        float effectiveMax = Math.max(INITIAL_CUT_DEPTH, targetMaxDepth);
        float targetDepth;
        if (cutsAtCurrentStation == 0 && trenchDepth < 0.2F) {
            // Początek nowego wykopu: pewne wejście zębów w grunt na głębokość ~0.60m
            targetDepth = Math.min(0.60F, effectiveMax);
        } else if (trenchDepth >= 0.2F) {
            targetDepth = Math.min(effectiveMax, Math.max(INITIAL_CUT_DEPTH + 0.35F, trenchDepth + 0.45F));
        } else {
            targetDepth = effectiveMax;
        }
        // W razie ponowienia (brak towaru / <50%): każde retry schodzi lekko głębiej (+0.18m na próbę)
        if (retries > 0) {
            targetDepth = Math.min(effectiveMax + 0.35F, targetDepth + retries * 0.18F);
        }
        // Przy stick=-45 i bucket=100 (otwarta łyżka): depth = (12.45 - boom) / 9.25
        // Zatem boom = 12.5 - (targetDepth * 9.2) -> dla 0.60m daje boom ~7.0 (depth 0.60m)
        float boom = 12.5F - (targetDepth * 9.2F);
        return Math.clamp(boom, ArmKinematics.BOOM_MIN, ArmKinematics.BOOM_MAX);
    }

    private void handleRotationObstacle(Snapshot state, float targetYaw) {
        boolean isTurning = Math.abs(wrapDegrees(targetYaw - state.cabin())) > ANGLE_TOLERANCE;
        if (!isTurning) {
            rotationStallTicks = 0;
            swingObstacleBoomBoost = Math.max(0.0F, swingObstacleBoomBoost - 0.5F);
            return;
        }

        boolean cabBlocked = state.cabinBlocked()
                || (Math.abs(wrapDegrees(state.cabin() - prevCabin)) < 0.04F);
        if (cabBlocked) {
            rotationStallTicks++;
            if (rotationStallTicks >= 2) {
                // Opór zatrzymał koparkę podczas obrotu - wyżej podnosimy całe ramię koparki!
                swingObstacleBoomBoost = Math.min(
                        ArmKinematics.BOOM_MAX - SAFE_BOOM,
                        swingObstacleBoomBoost + 1.5F
                );
            }
        } else {
            rotationStallTicks = 0;
        }
    }

    public float currentWorkCabinYaw() {
        if (!inLeftExpansionPass || leftExpansionBlocks <= 0.0F) {
            return WORK_CABIN_YAW;
        }
        // W Peterwolfs-Groundworks-Excavator kąt ujemny obraca wieżyczkę w lewą stronę koparki.
        // Przy wysięgu ~5.8m krawędź tnąca przesuwa się w lewo o ~1.0m na każde 10° kąta obrotu:
        // shiftLeft = R * sin(-yaw) -> yaw = -degrees(asin(blocks / 5.8m))
        double ratio = Math.clamp(leftExpansionBlocks / 5.8D, 0.0D, 0.65D);
        float yawOffset = (float) Math.toDegrees(Math.asin(ratio));
        return -yawOffset;
    }

    public int activeTargetCuts() {
        return inLeftExpansionPass ? leftExpansionCycles : maxCutsPerStation;
    }

    private Controls positionForCut(Snapshot state) {
        float workYaw = currentWorkCabinYaw();
        boolean alignedWithWorkArea = Math.abs(wrapDegrees(workYaw - state.cabin())) <= ANGLE_TOLERANCE;
        handleRotationObstacle(state, workYaw);

        if (!rotationOverWorkAreaComplete) {
            // Po wysypaniu ramię NIE może za szybko opadać, a przedramię i łyżka muszą pozostać
            // wyprostowane/otwarte aż do całkowitego zakończenia obrotu nad pole robocze kopanego dołu.
            float targetBoom = Math.min(ArmKinematics.BOOM_MAX, SAFE_BOOM + swingObstacleBoomBoost);
            if (alignedWithWorkArea) {
                if (++rotationSettleTicks >= ROTATION_SETTLE_TICKS) {
                    rotationOverWorkAreaComplete = true;
                    settledTicks = 0;
                }
            } else {
                rotationSettleTicks = 0;
            }
            return target(state, workYaw, targetBoom, APPROACH_STICK, OPEN_BUCKET, 0.0F);
        }

        // Dopiero po zakończeniu obrotu nad pole robocze ramię zaczyna obniżać się do gruntu z otwartą łyżką
        float targetCutBoom = calculateCutBoom(state.trenchDepth(), cutsAtCurrentStation, maxDiggingDepth, digRetryDepth());
        Controls controls = target(state, workYaw, targetCutBoom, APPROACH_STICK, OPEN_BUCKET, 0.0F);

        if (atTarget(state, workYaw, targetCutBoom, APPROACH_STICK, OPEN_BUCKET)) {
            if (++settledTicks >= POSITION_SETTLE_TICKS) changePhase(Phase.PENETRATE_FOR_CUT);
        } else {
            settledTicks = 0;
        }
        return controls;
    }

    private Controls penetrateForCut(Snapshot state) {
        float workYaw = currentWorkCabinYaw();
        float targetCutBoom = calculateCutBoom(state.trenchDepth(), cutsAtCurrentStation, maxDiggingDepth, digRetryDepth());

        // During a retry, retain the partial load while the arm moves back into the trench.
        // Only reopen the bucket once boom and stick are already at the penetration pose.
        boolean retryingLowFill = lowFillRetriesAtCurrentStation > 0;
        boolean armAtPenetration = atArmTarget(state, workYaw, targetCutBoom, PENETRATE_STICK);
        float bucketTarget = (retryingLowFill && !armAtPenetration) ? HELD_BUCKET : OPEN_BUCKET;

        Controls controls = target(
                state, workYaw, targetCutBoom, PENETRATE_STICK, bucketTarget, 0.0F);
        if (atTarget(state, workYaw, targetCutBoom, PENETRATE_STICK, OPEN_BUCKET)) {
            if (++settledTicks >= PENETRATION_SETTLE_TICKS) changePhase(Phase.CUT_AND_CURL);
        } else {
            settledTicks = 0;
        }
        return controls;
    }

    private Controls cutAndCurl(Snapshot state) {
        if (state.isBucketFull()) {
            changePhase(Phase.CLOSE_BUCKET_IN_TRENCH);
            return closeBucketInTrench(state);
        }

        float workYaw = currentWorkCabinYaw();
        float targetCutBoom = calculateCutBoom(state.trenchDepth(), cutsAtCurrentStation, maxDiggingDepth, digRetryDepth());
        // Pełniejszy zakres ruchu: mocniejsze ściąganie przedramienia do siebie (-85.0F zamiast -75.0F) podczas zamykania łyżki
        float targetStick = (retriesAtCurrentStation > 0) ? -88.0F : SCOOP_STICK;
        Controls controls = target(state, workYaw, targetCutBoom, targetStick, HELD_BUCKET, 0.0F);

        // DO KOŃCA: nie przechodzimy dalej, dopóki łyżka nie zamknie się prawie do końca (<= -50.0F)
        boolean strokeFinished = atTarget(state, workYaw, targetCutBoom, targetStick, HELD_BUCKET)
                || (state.bucket() <= -52.0F && Math.abs(state.stick() - targetStick) <= ANGLE_TOLERANCE * 2);

        if (strokeFinished) {
            if (++settledTicks >= CUT_SETTLE_TICKS) {
                changePhase(Phase.CLOSE_BUCKET_IN_TRENCH);
            }
        } else {
            settledTicks = 0;
        }
        return controls;
    }

    private Controls closeBucketInTrench(Snapshot state) {
        // Kluczowe: domknięcie łyżki do końca w gruncie zanim wysięgnik ruszy w górę!
        // Przy ponowieniu (retry) ściągamy przedramię jeszcze mocniej do siebie podczas domykania łyżki
        float workYaw = currentWorkCabinYaw();
        float targetCutBoom = calculateCutBoom(state.trenchDepth(), cutsAtCurrentStation, maxDiggingDepth, digRetryDepth());
        float holdStick = Math.min(state.stick(), (digRetryDepth() > 0) ? -88.0F : -85.0F);
        Controls controls = target(state, workYaw, targetCutBoom, holdStick, HELD_BUCKET, 0.0F);

        // Do not raise the boom until the bucket is genuinely closed, not merely "close enough"
        // at -54 degrees. This gives the bucket the final hydraulic ticks needed to secure material.
        boolean bucketClosedInGround = Math.abs(state.bucket() - HELD_BUCKET) <= ANGLE_TOLERANCE;

        if (bucketClosedInGround) {
            if (++settledTicks < SCOOP_SETTLE_TICKS) {
                return controls;
            }

            // Prefer a well-filled bucket. A weak bite is repeated while the bucket remains
            // secured during repositioning. Only after several failed attempts do we accept
            // a smaller load as a fallback so the automation cannot loop forever.
            if (state.hasTargetLoad()) {
                lowFillRetriesAtCurrentStation = 0;
                changePhase(Phase.LIFT_AND_SWING_RIGHT);
                return liftAndSwingRight(state);
            }

            if (lowFillRetriesAtCurrentStation < MAX_LOW_FILL_RETRIES) {
                lowFillRetriesAtCurrentStation++;
                changePhase(Phase.REOPEN_AND_RESET_ARM);
                return reopenAndResetArm(state);
            }

            if (state.storedUnits() > 0) {
                changePhase(Phase.LIFT_AND_SWING_RIGHT);
                return liftAndSwingRight(state);
            }

            // No material after all retry attempts: start another low digging attempt instead
            // of lifting an empty bucket and hanging above the trench.
            lowFillRetriesAtCurrentStation = 0;
            changePhase(Phase.REOPEN_AND_RESET_ARM);
            return reopenAndResetArm(state);
        }

        settledTicks = 0;
        return controls;
    }

    private Controls reopenAndResetArm(Snapshot state) {
        // Retry with a weak load: first reposition with the bucket CLOSED so the material
        // already collected is not dumped above the trench. PENETRATE_FOR_CUT will reopen
        // it only after the arm is back at the cut depth.
        float workYaw = currentWorkCabinYaw();
        float targetCutBoom = calculateCutBoom(state.trenchDepth(), cutsAtCurrentStation, maxDiggingDepth, digRetryDepth());
        float resetBoom = Math.min(ArmKinematics.BOOM_MAX, targetCutBoom + 6.0F);

        Controls controls = target(state, workYaw, resetBoom, APPROACH_STICK, HELD_BUCKET, 0.0F);
        if (atTarget(state, workYaw, resetBoom, APPROACH_STICK, HELD_BUCKET)) {
            if (++settledTicks >= POSITION_SETTLE_TICKS) {
                changePhase(Phase.PENETRATE_FOR_CUT);
            }
        } else {
            settledTicks = 0;
        }
        return controls;
    }

    private Controls scoopAndCurl(Snapshot state) {
        changePhase(Phase.CLOSE_BUCKET_IN_TRENCH);
        return closeBucketInTrench(state);
    }

    private Controls relieveStall(Snapshot state) {
        reliefTicks++;
        if (reliefTicks >= STALL_RELIEF_TICKS) {
            reliefTicks = 0;
            stallTicks = 0;
            retriesAtCurrentStation++;
            if (!state.hasTargetLoad()) {
                if (lowFillRetriesAtCurrentStation < MAX_LOW_FILL_RETRIES) {
                    lowFillRetriesAtCurrentStation++;
                    changePhase(Phase.REOPEN_AND_RESET_ARM);
                    return reopenAndResetArm(state);
                }
                if (state.storedUnits() <= 0) {
                    lowFillRetriesAtCurrentStation = 0;
                    changePhase(Phase.REOPEN_AND_RESET_ARM);
                    return reopenAndResetArm(state);
                }
            }
            changePhase(Phase.LIFT_AND_SWING_RIGHT);
            return liftAndSwingRight(state);
        }
        return new Controls(0.0F, 0.0F, 0.0F, 0.5F, 0.5F, 0.0F);
    }

    private Controls liftAndSwingRight(Snapshot state) {
        float currentWorkYaw = currentWorkCabinYaw();

        if (state.storedUnits() <= 0) {
            changePhase(Phase.POSITION_FOR_CUT);
            return positionForCut(state);
        }

        // Stage 0: finish closing the bucket before the boom is allowed to rise.
        // This removes the visual/physical race where the arm used to lift a still-closing bucket.
        boolean bucketSecurelyClosed = Math.abs(state.bucket() - HELD_BUCKET) <= ANGLE_TOLERANCE;
        if (!bucketSecurelyClosed) {
            return target(state, currentWorkYaw, state.boom(), state.stick(), HELD_BUCKET, 0.0F);
        }

        handleRotationObstacle(state, RIGHT_DUMP_YAW);
        float targetBoom = Math.min(ArmKinematics.BOOM_MAX, SAFE_BOOM + swingObstacleBoomBoost);

        // Sekwencja podnoszenia i obrotu (2-etapowa, aby nic się nie wysypało):
        // 1. Dociągamy przedramię do wewnątrz (state.stick() zmierza do CUT_STICK / TUCKED_STICK),
        //    a nie prostujemy go od razu na -45, bo wyprostowane przedramię przy wysokim wysięgniku
        //    obraca dno łyżki do góry i wylewa urobek!
        //    Przedramię wysuwamy dopiero gdy wysięgnik jest już bezpiecznie wysoko (boom >= 32.0F).
        float targetStick = (state.boom() >= 32.0F) ? SAFE_STICK : CUT_STICK;

        // 2. Obrót wieżyczki w prawo dopuszczamy dopiero, gdy łyżka jest domknięta,
        //    wysięgnik podniósł urobek ponad poziom gruntu (boom >= 22.0F),
        //    oraz łyżka ma urobek (przynajmniej 50% lub ostatecznie wyczerpano wszystkie próby).
        boolean armClearedGround = state.boom() >= 22.0F;

        // Reaching this phase is already the authoritative decision that the load should be
        // dumped. Do not apply a second fill threshold here, because that created the
        // raised-arm deadlock for partially filled buckets.
        float targetCab = armClearedGround ? RIGHT_DUMP_YAW : currentWorkYaw;

        Controls controls = target(
                state, targetCab, targetBoom, targetStick, HELD_BUCKET, 0.0F);
        if (atTarget(state, RIGHT_DUMP_YAW, targetBoom, SAFE_STICK, HELD_BUCKET)) {
            changePhase(Phase.DUMP_RIGHT);
        }
        return controls;
    }

    private Controls dumpRight(Snapshot state) {
        // Podczas wysypywania bardziej wyprostowane przedramię (DUMP_STICK) i otwarta do końca łyżka (DUMP_BUCKET)
        Controls controls = target(
                state, RIGHT_DUMP_YAW, DUMP_BOOM, DUMP_STICK, DUMP_BUCKET, 0.0F);
        if (state.storedUnits() == 0
                && atTarget(state, RIGHT_DUMP_YAW, DUMP_BOOM, DUMP_STICK, DUMP_BUCKET)) {
            cutsAtCurrentStation++;
            retriesAtCurrentStation = 0;
            lowFillRetriesAtCurrentStation = 0;
            swingObstacleBoomBoost = 0.0F;

            int targetLimit = inLeftExpansionPass ? leftExpansionCycles : maxCutsPerStation;
            if (cutsAtCurrentStation < targetLimit) {
                // Kolejne cięcie w bieżącej serii
                changePhase(Phase.POSITION_FOR_CUT);
            } else {
                // Zakończono serię: sprawdzamy czy teraz przechodzimy do poszerzenia po lewej
                if (!inLeftExpansionPass && leftExpansionBlocks > 0.0F && leftExpansionCycles > 0) {
                    inLeftExpansionPass = true;
                    cutsAtCurrentStation = 0;
                    changePhase(Phase.POSITION_FOR_CUT);
                } else {
                    // Wykonano cięcia główne oraz poszerzenie po lewej -> czas na cofnięcie gąsienicami!
                    inLeftExpansionPass = false;
                    cutsAtCurrentStation = 0;
                    this.reverseOrigin = state.position();
                    changePhase(Phase.RESET_AND_REVERSE);
                }
            }
        }
        return controls;
    }

    private Controls resetAndReverse(Snapshot state) {
        if (reverseOrigin == null) reverseOrigin = state.position();
        Vec3 forward = forward(state.baseYaw());
        double reversed = reverseOrigin.subtract(state.position()).dot(forward);
        if (reversed >= REVERSE_DISTANCE) {
            completedSections++;
            cutsAtCurrentStation = 0;
            inLeftExpansionPass = false;
            retriesAtCurrentStation = 0;
            lowFillRetriesAtCurrentStation = 0;
            swingObstacleBoomBoost = 0.0F;
            changePhase(Phase.POSITION_FOR_CUT);
            reverseOrigin = null;
            return Controls.STOPPED;
        }
        handleRotationObstacle(state, WORK_CABIN_YAW);
        float targetBoom = Math.min(ArmKinematics.BOOM_MAX, SAFE_BOOM + swingObstacleBoomBoost);
        return target(state, WORK_CABIN_YAW, targetBoom, APPROACH_STICK, APPROACH_BUCKET, REVERSE_THROTTLE);
    }

    private static Controls target(
            Snapshot state,
            float cabin,
            float boom,
            float stick,
            float bucket,
            float throttle
    ) {
        return new Controls(
                throttle,
                0.0F,
                inputForWrappedAngle(state.cabin(), cabin, ArmKinematics.CAB_TURN_SPEED),
                inputFor(state.boom(), boom, ArmKinematics.BOOM_SPEED),
                inputFor(state.stick(), stick, ArmKinematics.STICK_SPEED),
                inputFor(state.bucket(), bucket, ArmKinematics.BUCKET_SPEED)
        );
    }

    private static boolean atArmTarget(
            Snapshot state, float cabin, float boom, float stick) {
        return Math.abs(wrapDegrees(cabin - state.cabin())) <= ANGLE_TOLERANCE
                && Math.abs(boom - state.boom()) <= ANGLE_TOLERANCE
                && Math.abs(stick - state.stick()) <= ANGLE_TOLERANCE;
    }

    private static boolean atTarget(
            Snapshot state, float cabin, float boom, float stick, float bucket) {
        return Math.abs(wrapDegrees(cabin - state.cabin())) <= ANGLE_TOLERANCE
                && Math.abs(boom - state.boom()) <= ANGLE_TOLERANCE
                && Math.abs(stick - state.stick()) <= ANGLE_TOLERANCE
                && Math.abs(bucket - state.bucket()) <= ANGLE_TOLERANCE;
    }

    private static float inputFor(float current, float target, float speed) {
        float error = target - current;
        if (Math.abs(error) <= ANGLE_TOLERANCE) return 0.0F;
        return Math.clamp(error / speed, -1.0F, 1.0F);
    }

    private static float inputForWrappedAngle(float current, float target, float speed) {
        float error = wrapDegrees(target - current);
        if (Math.abs(error) <= ANGLE_TOLERANCE) return 0.0F;
        return Math.clamp(error / speed, -1.0F, 1.0F);
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    private static Vec3 forward(float baseYaw) {
        double yaw = Math.toRadians(baseYaw);
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    private int digRetryDepth() {
        return Math.max(retriesAtCurrentStation, lowFillRetriesAtCurrentStation);
    }

    private void changePhase(Phase next) {
        this.phase = next;
        this.settledTicks = 0;
        this.rotationSettleTicks = 0;
        this.rotationOverWorkAreaComplete = (next != Phase.POSITION_FOR_CUT);
    }

    void startForTest(Phase phase, Vec3 reverseOrigin) {
        this.active = true;
        this.phase = phase;
        this.reverseOrigin = reverseOrigin;
        this.settledTicks = 0;
        this.completedSections = 0;
        this.cutsAtCurrentStation = 0;
        this.retriesAtCurrentStation = 0;
        this.lowFillRetriesAtCurrentStation = 0;
        this.stallTicks = 0;
        this.reliefTicks = 0;
        this.rotationStallTicks = 0;
        this.rotationSettleTicks = 0;
        this.rotationOverWorkAreaComplete = true;
        this.swingObstacleBoomBoost = 0.0F;
    }

    void setPhaseForTest(Phase phase) {
        this.phase = phase;
        this.settledTicks = 0;
        this.stallTicks = 0;
        this.reliefTicks = 0;
        this.retriesAtCurrentStation = 0;
        this.lowFillRetriesAtCurrentStation = 0;
        this.rotationStallTicks = 0;
        this.rotationSettleTicks = 0;
        this.rotationOverWorkAreaComplete = true;
        this.swingObstacleBoomBoost = 0.0F;
    }

    public boolean isActive() {
        return active;
    }

    public Phase phase() {
        return phase;
    }

    public int completedSections() {
        return completedSections;
    }

    public int cutsAtCurrentStation() {
        return cutsAtCurrentStation;
    }

    public int maxCutsPerStation() {
        return maxCutsPerStation;
    }

    public float maxDiggingDepth() {
        return maxDiggingDepth;
    }

    public float leftExpansionBlocks() {
        return leftExpansionBlocks;
    }

    public int leftExpansionCycles() {
        return leftExpansionCycles;
    }

    public boolean isInLeftExpansionPass() {
        return inLeftExpansionPass;
    }

    public float swingObstacleBoomBoost() {
        return swingObstacleBoomBoost;
    }

    public boolean prefersBucketIntake() {
        return active && (phase == Phase.PENETRATE_FOR_CUT
                || phase == Phase.CUT_AND_CURL
                || phase == Phase.CLOSE_BUCKET_IN_TRENCH
                || phase == Phase.SCOOP_AND_CURL
                || phase == Phase.RELIEVE_STALL);
    }
}
