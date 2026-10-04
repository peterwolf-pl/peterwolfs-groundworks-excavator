# Excavator Controls & Operation Manual

## Entering and Exiting

- **Enter Excavator**: Right-click the excavator with an empty hand.
- **Exit Excavator**: Press standard Sneak / Dismount key (`Left Shift`).

---

## Dual Control Modes (Toggle with `X`)

Press **`X`** at any time while seated in the cab to instantly switch between **DRIVE MODE** and **ARM MODE**:

### 1. DRIVE MODE (`Tryb Jazdy`)
Optimized for traversing terrain and positioning the machine:
- **`W`**: Drive tracks forward.
- **`S`**: Drive tracks in reverse.
- **`A`**: Steer left (curves left while moving; spins left in place when stationary).
- **`D`**: Steer right (curves right while moving; spins right in place when stationary).
- **`Arrows`**: Optional boom elevation and cab rotation adjustment during transit.
- **`R` / `F`**: Extend / retract stick.
- **`T` / `G`**: Curl / dump bucket.

### 2. ARM MODE (`Tryb Ramienia`)
Tracks are safely locked stationary so full focus is given to excavation:
- **`W` / `S`** (or **`Up` / `Down Arrow`**): Raise / lower the heavy boom.
- **`A` / `D`** (or **`Left` / `Right Arrow`**): Rotate the turntable cab left / right.
- **`R` / `F`**: Extend / retract dipper stick.
- **`T` / `G`**: Curl / dump bucket.

---

## Complete Keybinding Reference

Configurable in **Options -> Controls -> Key Binds -> Groundworks Excavator**:

| Key | Default Key | Action in Drive Mode | Action in Arm Mode |
|---|---|---|---|
| **Toggle Mode** | `X` | Switch to Arm Mode | Switch to Drive Mode |
| **Move Forward** | `W` | Drive tracks forward | Raise boom |
| **Move Backward** | `S` | Drive tracks in reverse | Lower boom |
| **Move Left** | `A` | Steer / pivot tracks left | Rotate cab left |
| **Move Right** | `D` | Steer / pivot tracks right | Rotate cab right |
| **Rotate Cab Left** | `Left Arrow` | Rotate cab left | Rotate cab left |
| **Rotate Cab Right** | `Right Arrow` | Rotate cab right | Rotate cab right |
| **Raise Boom** | `Up Arrow` | Raise boom | Raise boom |
| **Lower Boom** | `Down Arrow` | Lower boom | Lower boom |
| **Extend Stick** | `R` | Extend stick | Extend stick |
| **Retract Stick** | `F` | Retract stick | Retract stick |
| **Curl Bucket In** | `T` | Curl bucket inward | Curl bucket inward |
| **Dump Bucket** | `G` | Curl bucket outward (dump) | Curl bucket outward (dump) |

---

## Operating Technique

### Digging Cycle
1. In **Drive Mode**, position the excavator facing the trench or slope.
2. Press **`X`** to switch to **Arm Mode**.
3. Rotate cab towards the dig face (`A` / `D` or `Left` / `Right Arrow`).
4. Extend the stick (`R`) and lower the boom (`S` or `Down Arrow`).
5. Lower the bucket teeth into the granular ground.
6. Pull the stick in (`F`) while curling the bucket inward (`T`).
7. As the teeth slice through the ground, the swept cutting volume removes granular voxels and fills the bucket.

### Carrying & Dumping Cycle
1. Raise the boom (`W` or `Up Arrow`) with the bucket curled up.
2. Rotate the cab (`A` / `D`) toward the dump target (pile or truck).
3. Uncurl the bucket outward (`G`) until tilt passes $30^\circ$.
4. Granular material pours out from the bucket lip into Groundworks terrain, forming a natural relaxed pile.
5. Press **`X`** to switch back to **Drive Mode** to reposition the tracks.
