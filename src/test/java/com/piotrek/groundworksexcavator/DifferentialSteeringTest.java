package com.piotrek.groundworksexcavator;

import com.piotrek.groundworksexcavator.vehicle.TrackMovementController;
import com.piotrek.groundworksexcavator.vehicle.TrackMovementController.TrackState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DifferentialSteeringTest {

    @Test
    @DisplayName("Straight forward throttle produces equal left and right track speeds")
    void testForwardThrottle() {
        TrackMovementController controller = new TrackMovementController();
        Vec3 pos = new Vec3(0, 10, 0);

        // Run several ticks of forward input to reach cruising speed
        TrackState state = null;
        for (int i = 0; i < 15; i++) {
            state = controller.tick(null, pos, 0.0F, 1.0F, 0.0F, true);
        }

        assertNotNull(state);
        assertTrue(state.leftSpeed() > 0.0F);
        assertTrue(state.rightSpeed() > 0.0F);
        assertEquals(state.leftSpeed(), state.rightSpeed(), 0.001F,
                "Both tracks must move forward with identical speed");
        assertEquals(0.0F, state.yawDeltaDegrees(), 0.001F,
                "Yaw delta must be zero during straight driving");
    }

    @Test
    @DisplayName("Pivot turn (steer without throttle) spins tracks in opposite directions")
    void testPivotTurn() {
        TrackMovementController controller = new TrackMovementController();
        Vec3 pos = new Vec3(0, 10, 0);

        TrackState state = null;
        for (int i = 0; i < 15; i++) {
            // Steering right (steer = +1.0, throttle = 0.0)
            state = controller.tick(null, pos, 0.0F, 0.0F, 1.0F, true);
        }

        assertNotNull(state);
        // Left track forward, right track backward
        assertTrue(state.leftSpeed() < 0.0F, "Left track should drive backward for right pivot");
        assertTrue(state.rightSpeed() > 0.0F, "Right track should drive forward for right pivot");
        assertTrue(state.yawDeltaDegrees() > 0.0F, "Must produce positive yaw angular velocity");
    }

    @Test
    @DisplayName("Granular contact can stop sideways track motion immediately")
    void testGranularContactStopsTrackMotion() {
        TrackMovementController controller = new TrackMovementController();
        controller.setTrackSpeeds(-0.08F, 0.08F);

        controller.stopMotion();
        TrackState state = controller.tick(null, Vec3.ZERO, 0.0F, 0.0F, 0.0F, true);

        assertEquals(0.0F, state.leftSpeed(), 0.001F);
        assertEquals(0.0F, state.rightSpeed(), 0.001F);
        assertEquals(0.0F, state.yawDeltaDegrees(), 0.001F);
    }

    @Test
    @DisplayName("Neutral throttle and steering decelerates tracks to zero")
    void testBraking() {
        TrackMovementController controller = new TrackMovementController();
        controller.setTrackSpeeds(0.08F, 0.08F);

        // Apply no inputs for 10 ticks
        TrackState state = null;
        for (int i = 0; i < 10; i++) {
            state = controller.tick(null, Vec3.ZERO, 0.0F, 0.0F, 0.0F, true);
        }

        assertNotNull(state);
        assertEquals(0.0F, state.leftSpeed(), 0.001F);
        assertEquals(0.0F, state.rightSpeed(), 0.001F);
    }
}
