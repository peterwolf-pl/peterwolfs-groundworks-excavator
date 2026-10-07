package com.piotrek.groundworksexcavator.vehicle;

import com.piotrek.groundworksexcavator.arm.ArmKinematics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CabSeatFacingTest {

    @Test
    void firstSeatSnapsTorsoAndLookToTheCabFront() {
        CabSeatFacing.Look look = CabSeatFacing.align(40.0F, Float.NaN, 180.0F, true);
        assertEquals(40.0F, look.bodyYaw());
        assertEquals(40.0F, look.headYaw());
    }

    @Test
    void cabTurnCarriesTheLookAndKeepsTheBodyOnTheCab() {
        CabSeatFacing.Look look = CabSeatFacing.align(25.0F, 10.0F, 20.0F, false);
        assertEquals(25.0F, look.bodyYaw());
        assertEquals(35.0F, look.headYaw(), 0.01F);
    }

    @Test
    void headCannotSpinTheTorsoOffTheCabFront() {
        CabSeatFacing.Look look = CabSeatFacing.align(0.0F, 0.0F, 120.0F, false);
        assertEquals(0.0F, look.bodyYaw());
        assertEquals(CabSeatFacing.MAX_HEAD_OFFSET, look.headYaw(), 0.01F);
    }

    @Test
    void cabFacingFollowsTheUpperBody() {
        float straight = ArmKinematics.getCabFacingYaw(0.0F, 0.0F, 0.0F, 0.0F);
        float turned = ArmKinematics.getCabFacingYaw(0.0F, 0.0F, 0.0F, 90.0F);
        assertEquals(0.0F, straight, 0.1F);
        assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(turned - straight)) > 80.0F);
    }

    @Test
    void lateCabPacketIsSpreadAcrossStepsInsteadOfOneJump() {
        float yaw = 0.0F;
        yaw = CabSeatFacing.stepToward(yaw, 9.0F, ArmKinematics.CAB_TURN_SPEED, 40.0F);
        assertEquals(ArmKinematics.CAB_TURN_SPEED, yaw, 0.01F);
        yaw = CabSeatFacing.stepToward(yaw, 9.0F, ArmKinematics.CAB_TURN_SPEED, 40.0F);
        yaw = CabSeatFacing.stepToward(yaw, 9.0F, ArmKinematics.CAB_TURN_SPEED, 40.0F);
        assertEquals(9.0F, yaw, 0.01F);
    }

    @Test
    void largeCabGapSnapsInsteadOfCrawling() {
        assertEquals(90.0F, CabSeatFacing.stepToward(0.0F, 90.0F, 3.0F, 40.0F), 0.01F);
    }
}
