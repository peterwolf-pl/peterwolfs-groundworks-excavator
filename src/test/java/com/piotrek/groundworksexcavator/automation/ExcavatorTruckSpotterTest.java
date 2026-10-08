package com.piotrek.groundworksexcavator.automation;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExcavatorTruckSpotterTest {

    @Test
    @DisplayName("Truck spotter initializes and resets to default transit pose values")
    void spotterResetsToDefaults() {
        ExcavatorTruckSpotter spotter = new ExcavatorTruckSpotter();

        assertEquals(-1, spotter.getFleetTargetEntityId());
        assertEquals(AutoTrenchController.REAR_DUMP_YAW, spotter.getFleetTargetYaw());
        assertEquals(AutoTrenchController.REAR_DUMP_CHECK_BOOM, spotter.getFleetTargetBoom());
        assertEquals(AutoTrenchController.DUMP_STICK, spotter.getFleetTargetStick());

        spotter.applyFleetDumpPose(new ExcavatorTruckSpotter.FleetDumpPose(
                120.0F, 46.0F, -40.0F, new Vec3(2.0, 65.0, -3.0), 0.1, 0.05
        ));

        assertEquals(120.0F, spotter.getFleetTargetYaw());
        assertEquals(46.0F, spotter.getFleetTargetBoom());
        assertEquals(-40.0F, spotter.getFleetTargetStick());

        spotter.reset();

        assertEquals(-1, spotter.getFleetTargetEntityId());
        assertEquals(AutoTrenchController.REAR_DUMP_YAW, spotter.getFleetTargetYaw());
        assertEquals(AutoTrenchController.REAR_DUMP_CHECK_BOOM, spotter.getFleetTargetBoom());
        assertEquals(AutoTrenchController.DUMP_STICK, spotter.getFleetTargetStick());
    }

    @Test
    @DisplayName("Null receiver produces null receiver center without exceptions")
    void estimateFleetReceiverCenterHandlesNullGracefully() {
        assertNull(ExcavatorTruckSpotter.estimateFleetReceiverCenter(null));
    }

    @Test
    @DisplayName("Fleet and rear search dimensions maintain required operational corridors")
    void searchGeometryConstants() {
        assertTrue(ExcavatorTruckSpotter.FLEET_TRUCK_SEARCH_RADIUS >= 10.0D);
        assertTrue(ExcavatorTruckSpotter.REAR_TRUCK_MAX_DISTANCE >= 8.0D);
        assertTrue(ExcavatorTruckSpotter.REAR_TRUCK_MAX_LATERAL >= 3.0D);
        assertTrue(ExcavatorTruckSpotter.FLEET_MIN_DUMP_BOOM < ExcavatorTruckSpotter.FLEET_MAX_DUMP_BOOM);
        assertTrue(ExcavatorTruckSpotter.FLEET_MIN_DUMP_STICK < ExcavatorTruckSpotter.FLEET_MAX_DUMP_STICK);
    }
}
