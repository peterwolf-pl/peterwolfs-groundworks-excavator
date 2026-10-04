# Excavator Controls & Operation Manual

## Entering and Exiting

- **Enter Excavator**: Right-click the excavator with an empty hand.
- **Exit Excavator**: Press standard Sneak / Dismount key (`Left Shift`).

---

## Control Modes

The machine separates locomotive track driving from hydraulic arm operation:

### 1. Drive Controls (Tracks)

Differential crawler drive:
- **`W`**: Drive both tracks forward.
- **`S`**: Drive both tracks in reverse.
- **`A`**: Steer left (curves left while driving forward; pivot-spins left when stationary).
- **`D`**: Steer right (curves right while driving forward; pivot-spins right when stationary).

Pivot steering allows turning $360^\circ$ in place by running the left and right tracks in opposing directions.

---

### 2. Hydraulic Arm & Turntable Controls

Hydraulic articulation keys (configurable in **Options -> Controls -> Key Binds -> Groundworks Excavator**):

| Function | Default Key | Action |
|---|---|---|
| **Rotate Cab Left** | `Left Arrow` | Rotates turntable cab counter-clockwise |
| **Rotate Cab Right** | `Right Arrow` | Rotates turntable cab clockwise |
| **Raise Boom** | `Up Arrow` | Raises main boom arm up ($-28^\circ \dots +52^\circ$) |
| **Lower Boom** | `Down Arrow` | Lowers main boom arm down |
| **Extend Stick** | `R` | Pushes dipper arm outward |
| **Retract Stick** | `F` | Pulls dipper arm inward toward the cab |
| **Curl Bucket In** | `T` | Curls bucket teeth inward to scoop and hold material |
| **Dump Bucket** | `G` | Uncurls bucket teeth downward into dumping orientation |

---

## Operating Technique

### Digging Cycle
1. Position excavator facing the excavation trench or slope.
2. Rotate cab towards the dig face (`Left` / `Right Arrow`).
3. Extend the stick (`R`) and lower the boom (`Down Arrow`).
4. Lower the bucket teeth into the granular ground.
5. Pull the stick in (`F`) while simultaneously curling the bucket inward (`T`).
6. As the teeth slice through the ground, the swept cutting volume removes granular voxels and fills the bucket.

### Carrying Cycle
1. Raise the boom (`Up Arrow`) with the bucket curled up to clear the ground.
2. Rotate the turntable cab (`Left` / `Right Arrow`) towards the dump target (pile or truck).

### Dumping Cycle
1. Position the bucket above the desired pile location.
2. Uncurl the bucket outward (`G`).
3. When the bucket tilt passes $30^\circ$ downwards, soil gradually flows out of the bucket lip.
4. Granular material falls into Groundworks terrain and naturally relaxes into a stable conical heap.
