package com.piotrek.groundworksexcavator.automation;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import net.minecraft.world.phys.Vec3;

/**
 * Deterministic server-side motion plan used by the trench test command.
 *
 * <p>One cycle positions the bucket, performs a one-block inward/downward cut,
 * lifts and swings the load to the machine's right, empties it, then reverses
 * exactly one block before starting the next section.</p>
 */
public final class AutoTrenchController {

    public static final float APPROACH_BOOM = 20.0F;
    public static final float APPROACH_STICK = -85.0F;
    public static final float APPROACH_BUCKET = 10.0F;

    public static final float PENETRATE_BOOM = 9.0F;
    public static final float PENETRATE_STICK = -78.0F;
    public static final float PENETRATE_BUCKET = 10.0F;

    public static final float CUT_BOOM = PENETRATE_BOOM;
    public static final float CUT_STICK = PENETRATE_STICK;
    public static final float CUT_BUCKET = 35.0F;

    public static final float SAFE_BOOM = 52.0F;
    public static final float SAFE_STICK = -95.0F;
    public static final float HELD_BUCKET = -50.0F;
    public static final float DUMP_BUCKET = 60.0F;
    public static final float RIGHT_DUMP_YAW = 90.0F;

    private static final float ANGLE_TOLERANCE = 1.25F;
    private static final float REVERSE_THROTTLE = -0.65F;
    private static final double REVERSE_DISTANCE = 1.0D;
    private static final int POSITION_SETTLE_TICKS = 3;
    private static final int PENETRATION_SETTLE_TICKS = 2;
    private static final int CUT_SETTLE_TICKS = 2;
    private static final int SCOOP_SETTLE_TICKS = 2;

    public enum Phase {
        POSITION_FOR_CUT,
        PENETRATE_FOR_CUT,
        CUT_AND_CURL,
        SCOOP_AND_CURL,
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
            Vec3 position,
            float baseYaw
    ) {}

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
    private Vec3 reverseOrigin;

    public void start() {
        this.active = true;
        this.phase = Phase.POSITION_FOR_CUT;
        this.settledTicks = 0;
        this.completedSections = 0;
        this.reverseOrigin = null;
    }

    public void stop() {
        this.active = false;
        this.settledTicks = 0;
        this.reverseOrigin = null;
    }

    public Controls tick(Snapshot state) {
        if (!active) return Controls.STOPPED;
        if (!state.occupied()) {
            stop();
            return Controls.STOPPED;
        }

        if (phase == Phase.POSITION_FOR_CUT && state.storedUnits() > 0) {
            changePhase(Phase.LIFT_AND_SWING_RIGHT);
        }

        return switch (phase) {
            case POSITION_FOR_CUT -> positionForCut(state);
            case PENETRATE_FOR_CUT -> penetrateForCut(state);
            case CUT_AND_CURL -> cutAndCurl(state);
            case SCOOP_AND_CURL -> scoopAndCurl(state);
            case LIFT_AND_SWING_RIGHT -> liftAndSwingRight(state);
            case DUMP_RIGHT -> dumpRight(state);
            case RESET_AND_REVERSE -> resetAndReverse(state);
        };
    }

    private Controls positionForCut(Snapshot state) {
        Controls controls = target(state, 0.0F, APPROACH_BOOM, APPROACH_STICK, APPROACH_BUCKET, 0.0F);
        if (atTarget(state, 0.0F, APPROACH_BOOM, APPROACH_STICK, APPROACH_BUCKET)) {
            if (++settledTicks >= POSITION_SETTLE_TICKS) changePhase(Phase.PENETRATE_FOR_CUT);
        } else {
            settledTicks = 0;
        }
        return controls;
    }

    private Controls penetrateForCut(Snapshot state) {
        Controls controls = target(
                state, 0.0F, PENETRATE_BOOM, PENETRATE_STICK, PENETRATE_BUCKET, 0.0F);
        if (atTarget(state, 0.0F, PENETRATE_BOOM, PENETRATE_STICK, PENETRATE_BUCKET)) {
            if (++settledTicks >= PENETRATION_SETTLE_TICKS) changePhase(Phase.CUT_AND_CURL);
        } else {
            settledTicks = 0;
        }
        return controls;
    }

    private Controls cutAndCurl(Snapshot state) {
        Controls controls = target(state, 0.0F, CUT_BOOM, CUT_STICK, CUT_BUCKET, 0.0F);
        if (atTarget(state, 0.0F, CUT_BOOM, CUT_STICK, CUT_BUCKET)) {
            if (++settledTicks >= CUT_SETTLE_TICKS) changePhase(Phase.SCOOP_AND_CURL);
        } else {
            settledTicks = 0;
        }
        return controls;
    }

    private Controls scoopAndCurl(Snapshot state) {
        Controls controls = target(state, 0.0F, CUT_BOOM, CUT_STICK, HELD_BUCKET, 0.0F);
        if (atTarget(state, 0.0F, CUT_BOOM, CUT_STICK, HELD_BUCKET)) {
            if (++settledTicks >= SCOOP_SETTLE_TICKS) changePhase(Phase.LIFT_AND_SWING_RIGHT);
        } else {
            settledTicks = 0;
        }
        return controls;
    }

    private Controls liftAndSwingRight(Snapshot state) {
        Controls controls = target(
                state, RIGHT_DUMP_YAW, SAFE_BOOM, SAFE_STICK, HELD_BUCKET, 0.0F);
        if (atTarget(state, RIGHT_DUMP_YAW, SAFE_BOOM, SAFE_STICK, HELD_BUCKET)) {
            changePhase(Phase.DUMP_RIGHT);
        }
        return controls;
    }

    private Controls dumpRight(Snapshot state) {
        Controls controls = target(
                state, RIGHT_DUMP_YAW, SAFE_BOOM, SAFE_STICK, DUMP_BUCKET, 0.0F);
        if (state.storedUnits() == 0
                && atTarget(state, RIGHT_DUMP_YAW, SAFE_BOOM, SAFE_STICK, DUMP_BUCKET)) {
            this.reverseOrigin = state.position();
            changePhase(Phase.RESET_AND_REVERSE);
        }
        return controls;
    }

    private Controls resetAndReverse(Snapshot state) {
        if (reverseOrigin == null) reverseOrigin = state.position();
        Vec3 forward = forward(state.baseYaw());
        double reversed = reverseOrigin.subtract(state.position()).dot(forward);
        if (reversed >= REVERSE_DISTANCE) {
            completedSections++;
            changePhase(Phase.POSITION_FOR_CUT);
            reverseOrigin = null;
            return Controls.STOPPED;
        }
        return target(state, 0.0F, SAFE_BOOM, SAFE_STICK, HELD_BUCKET, REVERSE_THROTTLE);
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

    private void changePhase(Phase next) {
        this.phase = next;
        this.settledTicks = 0;
    }

    void startForTest(Phase phase, Vec3 reverseOrigin) {
        this.active = true;
        this.phase = phase;
        this.reverseOrigin = reverseOrigin;
        this.settledTicks = 0;
        this.completedSections = 0;
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

    public boolean prefersBucketIntake() {
        return active && (phase == Phase.PENETRATE_FOR_CUT
                || phase == Phase.CUT_AND_CURL
                || phase == Phase.SCOOP_AND_CURL);
    }
}
