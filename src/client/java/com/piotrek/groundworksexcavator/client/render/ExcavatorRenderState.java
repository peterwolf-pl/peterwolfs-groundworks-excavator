package com.piotrek.groundworksexcavator.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Render state container extracted each frame for excavator rendering.
 */
public class ExcavatorRenderState extends EntityRenderState {

    public float baseYaw;
    public float basePitch;
    public float baseRoll;
    public float upperYaw;
    public float boomAngle;
    public float stickAngle;
    public float bucketAngle;

    public float leftTrackSpeed;
    public float rightTrackSpeed;
    public float trackScroll;

    public int materialId;
    public int storedUnits;
    public int capacity;
    public float fillRatio;
    public int bucketType;

    public boolean isDigging;
    public boolean isDumping;
    public boolean isHammering;
    public float hammerStroke;

    public boolean isOperating;
    public float beaconSpin;
    public float machineLoad;
}
