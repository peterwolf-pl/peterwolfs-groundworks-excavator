# Excavator Architecture & Transform Hierarchy

## System Architecture

The mod is structured into clear architectural domains:

```text
com.piotrek.groundworksexcavator
 ├── entity
 │    └── GroundworksExcavatorEntity   <-- Authoritative Minecraft vehicle entity
 ├── vehicle
 │    └── TrackMovementController       <-- Differential track dynamics & terrain slopes
 ├── arm
 │    └── ArmKinematics                 <-- Forward kinematics, joint limits & transforms
 ├── excavation
 │    ├── SweptBucketVolume             <-- Cutting volume calculation between poses
 │    ├── BucketExcavationController    <-- Terrain cutting & volume transfer
 │    └── BucketDumpingController       <-- Lip position & gravitational pour flow
 ├── material
 │    └── BucketMaterialContainer       <-- Strict integer volumetric inventory
 ├── integration.groundworks
 │    └── GroundworksExcavationAdapter  <-- Service layer interfacing Groundworks API
 ├── network
 │    └── ExcavatorInputPayload         <-- Client operator intent packet
 └── client
      ├── input                         <-- KeyMappings & input loop
      ├── model                         <-- 26.3 ExcavatorModel (hierarchical parts)
      ├── render                        <-- ExcavatorRenderer & In-Cab HUD Overlay
      └── GroundworksExcavatorClient    <-- Client entry point
```

---

## Transform Hierarchy

The excavator is modeled and animated as a single hierarchical kinematic tree:

```text
Undercarriage (Base Pos [X, Y, Z], Yaw, Pitch, Roll)
 │
 ├── Left Track (offset X = -1.05m, animated by leftTrackSpeed)
 ├── Right Track (offset X = +1.05m, animated by rightTrackSpeed)
 │
 └── Turntable Pivot (offset Y = +0.75m, rotated around Y by upperYaw)
      ├── Operator Seat (offset X = -0.75m, Y = +0.85m, Z = +0.35m)
      ├── Engine Compartment & Counterweight (rear ballast)
      │
      └── Boom Base Pivot (offset X = +0.35m, Y = +0.45m, Z = +0.30m)
           │  (Pitched around local X-axis by boomAngle, range: -28° .. +52°)
           │
           └── Stick Pivot (offset along boom by 3.60m)
                │  (Pitched around local X-axis by stickAngle, range: -95° .. +30°)
                │
                └── Bucket Pivot (offset along stick by 2.40m)
                     │  (Pitched around local X-axis by bucketAngle, range: -65° .. +90°)
                     │
                     ├── Bucket Cavity & Dynamic Fill Level
                     ├── Cutting Edge & 5 Teeth (length 1.20m, width 0.80m)
                     └── Bucket Lip (deposition pour point)
```

### Forward Kinematics Computation

The forward kinematics function in `ArmKinematics.computeBucketPose(...)` computes world-space vectors in closed form:
1. `turntableCenter = basePos + (0, 0.75, 0)`
2. `totalYaw = baseYaw + upperYaw`
3. `boomBase = turntableCenter + rotate(upperYaw, BOOM_MOUNT_OFFSET)`
4. `stickPivot = boomBase + boomVector(length = 3.6m, pitch = boomAngle, yaw = totalYaw)`
5. `bucketPivot = stickPivot + stickVector(length = 2.4m, pitch = boomAngle + stickAngle, yaw = totalYaw)`
6. `cuttingEdge = bucketPivot + bucketVector(length = 1.2m, pitch = boomAngle + stickAngle + bucketAngle, yaw = totalYaw)`
7. `lip = cuttingEdge + offset`

---

## Operator Seat Binding

When a player mounts the excavator:
- The controlling passenger is positioned dynamically using `getPassengerRidingPosition(passenger)`.
- The seat position is calculated using `ArmKinematics.getDriverSeatWorldPosition(...)`.
- As the operator rotates the turntable cab, the seat rotates in world space rigidly with the cabin deck.
- On dismount, the passenger is placed to the safe side of the tracks via `getDismountLocationForPassenger(...)`.
