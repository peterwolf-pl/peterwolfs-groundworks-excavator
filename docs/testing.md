# Testing & Quality Assurance

## Automated Test Suite

The mod includes an automated JUnit 5 test suite covering all core domain logic without requiring a graphical client.

Execute the test suite:
```bash
./gradlew test
```

### Test Coverage

1. **`BucketContainerTest`**
   - `testEmptyBucket`: Confirms initial state ($0 / 256$ units, empty material, fill ratio 0.0).
   - `testCapacityEnforcement`: Inserting 300 units into a 256-unit bucket accepts 256 and rejects 44; subsequent additions are rejected.
   - `testMaterialConservation`: Excavation of 64 units adds exactly 64 units to the bucket.
   - `testDepositExtraction`: Depositing 32 units from 128 leaves exactly 96; subsequent extraction of 100 extracts the remaining 96 and transitions the container to empty.
   - `testRejectMaterialMismatch`: Verifies that a bucket containing dirt strictly rejects sand or gravel additions.
   - `testSaveAndLoadRoundtrip`: Verifies 26.3 `TagValueOutput` / `TagValueInput` serialization and deserialization preserves unit counts and material type (183 gravel units roundtrip).

2. **`ArmKinematicsTest`**
   - `testForwardKinematicsGeometry`: Validates 3D coordinates, cutting edge, lip, normal, and verifies that the 5 teeth points span across the full 0.8m bucket width.
   - `testDiggingDepthReach`: Asserts that when the arm is extended downward, the cutting edge reaches $\ge 3.0\text{ blocks}$ below track level.
   - `testForwardReach`: Asserts that the excavator forward reach is $\ge 5.0\text{ blocks}$.
   - `testTurntableRotation`: Verifies that rotating the turntable $90^\circ$ correctly transforms the arm heading into world coordinates.
   - `testDriverSeatTurntableOffset`: Asserts that the operator seat rigidly rotates with the cab turntable.
   - `testDumpTiltAngle`: Asserts that curled/level bucket angles do not trigger dumping ($< 30^\circ$), whereas downward-tilted angles ($\ge 30^\circ$) trigger dump flow.
   - `testArmCollisionSamples`: Checks dense, finite steel samples and verifies that cutting teeth are not part of the blocking arm volume.

3. **`ArmTerrainContactControllerTest`**
   - Rejects new or unchanged steel penetration.
   - Allows movement that reduces existing penetration.
   - Rejects lateral and vertical-tooth drag while teeth are embedded.
   - Checks deterministic one-cell surface-push direction.

4. **`SweptVolumeTest`**
   - `testStationaryBucket`: Verifies that a stationary bucket generates zero excavation volume.
   - `testTeleportationDiscontinuity`: Verifies that sudden jumps ($> 2.5\text{ m}$) are discarded to protect terrain integrity.
   - `testForwardCuttingSweep`: Confirms that forward motion of the teeth produces valid swept candidate block positions.

5. **`DifferentialSteeringTest`**
   - `testForwardThrottle`: Asserts equal track speeds and zero yaw delta during straight driving.
   - `testPivotTurn`: Asserts opposing track speeds and positive angular velocity during in-place pivot turns.
   - `testBraking`: Confirms smooth deceleration to a complete halt when inputs are released.
   - `testGranularContactStopsTrackMotion`: Confirms immediate track and yaw stop for blocked embedded-tooth movement.

---

## Visual Client GameTests (`runClientGameTest`)

The mod features automated visual regression tests executing directly in Minecraft's rendering pipeline via Fabric Client GameTests:

```bash
./gradlew runClientGameTest
```

This task launches an automated graphical singleplayer test instance, constructs a clean testing arena, spawns the excavator entity, moves the camera to deterministic perspectives, articulates the arm, and saves regression screenshots into `visual-tests/current/`.

The `excavator_08_surface_push_mound.png` scene also executes a real Groundworks surface displacement. It fails the GameTest if no gravel moves or if source-plus-destination material changes.

1. `excavator_01_profile_isometric.png`: Front-left isometric view of the full vehicle.
2. `excavator_02_cab_and_beacon.png`: Close-up of operator cab and the flashing yellow warning beacon.
3. `excavator_03_counterweight_hazard.png`: Rear counterweight with safety hazard stripes and exhaust stack.
4. `excavator_04_tracks_and_rollers.png`: Crawler tracks with drive sprockets and guide rollers.
5. `excavator_05_player_in_glass_cab.png`: Operator seated in the cab.
6. `excavator_06_in_cab_work_area_view.png`: Operator view through the windshield.
7. `excavator_07_large_bucket_digging.png`: Large-bucket digging posture.
8. `excavator_08_surface_push_mound.png`: Conserved gravel displacement and small mound.

---

## Manual Acceptance Scenario

To perform manual acceptance testing in game:

1. **Setup**:
   - Generate a flat world with Groundworks granular terrain (e.g. flat dirt).
2. **Placement**:
   - Give player `pw_groundworks_excavator:excavator`.
   - Right-click ground to place the machine.
3. **Mount**:
   - Right-click the cab. Confirm operator sits inside the cab facing the arm.
4. **Mobility**:
   - Press `W`/`S` to drive forward/backward.
   - Press `A`/`D` while stationary to verify in-place pivot rotation.
5. **Articulation**:
   - Rotate cab with `Left` / `Right Arrow`.
   - Raise/lower boom with `Up` / `Down Arrow`.
   - Articulate stick with `R` / `F`.
   - Curl bucket with `T` / `G`.
6. **Digging**:
   - Lower teeth into dirt and curl inward (`T`) while pulling stick in (`F`).
   - Observe terrain microvoxels physically vanishing and bucket filling up on the HUD overlay.
7. **Granular Contact**:
   - Try to lower the boom or stick body into gravel. Confirm that the steel stops before entering it.
   - Insert only the teeth, then try to rotate the cab or pivot the tracks sideways. Confirm that lateral drag stops.
   - Pull the teeth back along their cutting plane. Confirm that extraction remains possible.
   - Skim the teeth horizontally across the top. Confirm that the bucket stays empty and a small mound forms ahead.
8. **Carrying & Dumping**:
   - Raise boom and rotate cab $90^\circ$.
   - Uncurl bucket (`G`) until tilt passes $30^\circ$.
   - Confirm granular material pours out at the bucket lip and forms a conical relaxing pile on the ground.
   - Run `/excavator debug` to verify zero duplicated or lost material units.
9. **Engine audio**:
   - Mount the excavator and confirm the positional diesel loop starts.
   - Drive and pivot. Confirm pitch and volume rise smoothly with track speed.
   - Dismount after the tracks stop. Confirm the loop stops.
   - Move away and confirm linear distance attenuation.
