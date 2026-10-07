# Peterwolf's Groundworks Excavator

A high-fidelity, server-authoritative hydraulic tracked excavator mod for **Minecraft Java 26.3 (Wilderness Bound) Fabric**, designed to work hand-in-hand with **Peterwolf's Groundworks**.

---

## Overview

Unlike standard Minecraft vehicle mods that simulate digging by deleting full blocks or spawning loose item drops under a cosmetic arm, **Peterwolf's Groundworks Excavator** treats terrain removal, carrying, and dumping as an authoritative, volumetric physical process:

```text
PLAYER INPUT
    │
    ▼
HYDRAULIC JOINT MOTION
    │
    ▼
BUCKET WORLD MOTION
    │
    ▼
CUTTING EDGE SWEPT VOLUME
    │
    ▼
GROUNDWORKS EXCAVATION (ExcavationApi)
    │
    ▼
BUCKET MATERIAL CONTAINER (Integer Unit Conservation)
    │
    ▼
BUCKET DUMPING (Tilt & Flow Rate)
    │
    ▼
GROUNDWORKS DEPOSITION (DepositApi)
    │
    ▼
REAL DEFORMABLE GRANULAR PILE (Volumetric Relaxation)
```

---

## Key Features

1. **True Groundworks Integration**
   - No fake digging or phantom block drops.
   - All excavation and deposition queries go directly through the `GroundworksExcavationAdapter`.
   - Material conservation: $1\text{ block} = 512\text{ Groundworks units} = 1.000\text{ m}^3$.
   - Bucket capacity: 256 units ($0.500\text{ m}^3$).

2. **Differential Track Steering & Physics**
   - Independent left and right crawler tracks.
   - In-place pivot turns, progressive acceleration, heavy torque response, and strong braking.
   - Real-time ground height sampling conforming the excavator's chassis pitch and roll to granular terrain slopes.

3. **Hierarchical 3D Arm Kinematics**
   - Rotating upper structure / turntable ($360^\circ$ continuous cab rotation).
   - Articulated two-stage heavy boom ($-28^\circ \dots +52^\circ$).
   - Dipper stick arm ($-95^\circ \dots +30^\circ$).
   - Heavy scoop bucket with 5 cutting teeth ($-65^\circ \dots +90^\circ$).
   - Operator seat rigidly attached to the rotating cabin deck.

4. **Swept Cutting Volume Excavation**
   - Digging only occurs when the bucket cutting edge is actively moving through granular soil in a cutting direction.
   - Stationary buckets or dragging the rear heel of the scoop does not erase terrain.
   - High-speed teleportation guard prevents accidental subterranean tunnels.

5. **Gravity-Assisted Gradual Dumping**
   - Material exits the bucket lip when the bucket's downward pitch exceeds the dumping threshold ($30^\circ$).
   - Flow rate dynamically ramps from 8 to 32 units/tick depending on inclination angle.
   - Rejected overflow remains in the bucket; zero material loss.

6. **Interchangeable Pneumatic Breaker Attachment**
   - Press `Z` to cycle Standard Bucket -> Large Bucket -> Pneumatic Hammer.
   - With a bucket installed, `C` operates the excavator horn.
   - With the pneumatic hammer installed, hold `C` for momentary hammering; double-tap `C` to latch continuous operation.
   - Each impact crushes 128 Groundworks units, exactly one quarter of a block.
   - Stone is crushed into granular cobblestone and displaced beside the struck block instead of becoming an item drop.
   - The hammer can continue breaking and pushing loose cobblestone, but it never stores material in the bucket container.
   - Active hammering produces a fixed 36% machine load.
   - The animated chisel tip and server contact point share the same calibrated arm transform.

7. **In-Cab Telemetry HUD & Diagnostics**
   - Real-time in-cab heads-up display showing machine status, stored material, volume in $\text{m}^3$, fill bar, attachment state, and joint angles.
   - `/excavator debug` diagnostic command.

8. **Dump-Truck AutoTrench Workflow**
   - `/excavator autotrench dumptruck start <depth> <cycles> [expandLeftBlocks] [expandCycles]`
   - Digging geometry and station progression stay identical to normal AutoTrench.
   - Before unloading, the bucket is raised high and the upper structure rotates 180 degrees to dump into a Groundworks world container behind the excavator.
   - Leaving the cab does not stop this AutoTrench mode.
   - Removing the dump truck from behind the excavator pauses the automation until a compatible truck returns.
   - A quick double horn press on `C` asks the nearest compatible dump truck to advance exactly one block.

---

## Requirements & Compatibility

- **Minecraft**: `26.3`
- **Fabric Loader**: `>=0.19.5`
- **Fabric API**: `>=0.160.7+26.3`
- **Java**: `25`
- **Required Dependency**: `Peterwolf's Groundworks` (`pw_groundworks >= 0.1.0`)
- The hammer requires a Groundworks build with granular `cobblestone` and `stone -> cobblestone` conversion support.

---

## Documentation

Detailed technical architecture and guides are available in the `docs/` folder:

- [`docs/architecture.md`](docs/architecture.md) — System design, entity lifecycle, and transform hierarchy.
- [`docs/controls.md`](docs/controls.md) — Control modes, keybindings, and driving manual.
- [`docs/groundworks-integration.md`](docs/groundworks-integration.md) — Adapter contract and material volume mechanics.
- [`docs/bucket-excavation.md`](docs/bucket-excavation.md) — Swept cutting volume, teeth discretization, and dump flow.
- [`docs/networking.md`](docs/networking.md) — Client-to-server intent payloads and state sync.
- [`docs/testing.md`](docs/testing.md) — Automated JUnit suite, verification matrix, and manual test scenario.
- [`docs/performance.md`](docs/performance.md) — Performance safeguards and computational complexity.
