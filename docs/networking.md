# Networking Architecture & Multiplayer Synchronization

## Authority Model

All physical simulation, vehicle dynamics, hydraulic joints, and terrain modifications are **100% server-authoritative**.

```text
Client (Operator)                      Server (GroundworksExcavatorEntity)
       │                                               │
       │─── ExcavatorInputPayload (Intent) ───────────▶│  (Throttle, Steer, Cab, Boom, Stick, Bucket)
       │                                               │  [Simulate Joint Articulation]
       │                                               │  [Simulate Track Kinematics]
       │                                               │  [Simulate Swept Terrain Digging]
       │                                               │  [Simulate Gravitational Dumping]
       │◀── SynchedEntityData (State) ─────────────────│  (Positions, Angles, Units, Material ID)
       ▼                                               ▼
[Render Model & Interpolate]                    [Commit Terrain Changes]
```

---

## Client Operator Payload: `ExcavatorInputPayload`

- **Channel**: `pw_groundworks_excavator:excavator_input`
- **Direction**: Client to Server (Serverbound Play)
- **Transmission Policy**: Transmitted only when the operator's inputs change or as a keepalive heartbeat every 5 ticks.

### Packet Structure (24 Bytes)
| Field | Type | Description |
|---|---|---|
| `throttle` | `float` (4B) | Track drive input ($-1.0 \dots +1.0$) |
| `steer` | `float` (4B) | Differential steering input ($-1.0 \dots +1.0$) |
| `upperYawInput` | `float` (4B) | Turntable cab rotation input ($-1.0 \dots +1.0$) |
| `boomInput` | `float` (4B) | Boom elevation input ($-1.0 \dots +1.0$) |
| `stickInput` | `float` (4B) | Stick extension input ($-1.0 \dots +1.0$) |
| `bucketInput` | `float` (4B) | Bucket curl input ($-1.0 \dots +1.0$) |

---

## Server State Synchronization: `SynchedEntityData`

The server synchronizes authoritative vehicle state using Minecraft's optimized data tracker:

| Data Accessor | Type | Purpose |
|---|---|---|
| `TRACK_LEFT_SPEED` | `Float` | Left track crawler speed for visual wheel/track animation |
| `TRACK_RIGHT_SPEED` | `Float` | Right track crawler speed for visual wheel/track animation |
| `UPPER_YAW` | `Float` | Turntable cab orientation relative to undercarriage |
| `BOOM_ANGLE` | `Float` | Main boom elevation angle |
| `STICK_ANGLE` | `Float` | Dipper stick angle |
| `BUCKET_ANGLE` | `Float` | Bucket curl angle |
| `MATERIAL_ID` | `Integer` | Groundworks material identifier inside bucket |
| `STORED_UNITS` | `Integer` | Exact integer units inside bucket ($0 \dots 256$) |
| `CAPACITY` | `Integer` | Maximum bucket capacity ($256$) |
| `VEHICLE_PITCH` | `Float` | Terrain slope pitch angle |
| `VEHICLE_ROLL` | `Float` | Terrain slope roll angle |
| `IS_DIGGING` | `Boolean` | Triggers client particle and audio effects |
| `IS_DUMPING` | `Boolean` | Triggers client pour stream effects |

---

## Client Interpolation

In Minecraft 26.3, `GroundworksExcavatorEntity` creates a `LinearInterpolationHandler` with 3 steps of smooth interpolation:
```java
@Override
protected InterpolationHandler createInterpolationHandler() {
    return LinearInterpolationHandler.create(this, 3);
}
```
This guarantees jitter-free visual rendering for spectators and operators alike even under fluctuating network latencies.
