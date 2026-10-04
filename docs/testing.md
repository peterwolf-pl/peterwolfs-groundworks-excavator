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

3. **`SweptVolumeTest`**
   - `testStationaryBucket`: Verifies that a stationary bucket generates zero excavation volume.
   - `testTeleportationDiscontinuity`: Verifies that sudden jumps ($> 2.5\text{ m}$) are discarded to protect terrain integrity.
   - `testForwardCuttingSweep`: Confirms that forward motion of the teeth produces valid swept candidate block positions.

4. **`DifferentialSteeringTest`**
   - `testForwardThrottle`: Asserts equal track speeds and zero yaw delta during straight driving.
   - `testPivotTurn`: Asserts opposing track speeds and positive angular velocity during in-place pivot turns.
   - `testBraking`: Confirms smooth deceleration to a complete halt when inputs are released.

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
7. **Carrying & Dumping**:
   - Raise boom and rotate cab $90^\circ$.
   - Uncurl bucket (`G`) until tilt passes $30^\circ$.
   - Confirm granular material pours out at the bucket lip and forms a conical relaxing pile on the ground.
   - Run `/excavator debug` to verify zero duplicated or lost material units.
