# Performance & Complexity Analysis

## Computational Efficiency

Construction vehicles with moving arms can easily cause server lag if naive collision detection or volumetric scans are implemented. **Peterwolf's Groundworks Excavator** employs several algorithmic design choices to maintain constant-time ($O(1)$) per-tick overhead:

### 1. Closed-Form Forward Kinematics ($O(1)$)
All joint matrices and world-space positions (turntable, boom, stick, bucket, lip, 5 teeth) are calculated via closed-form trigonometric equations. No numerical solvers, iterative inverse kinematics, or physics engine rigid body hierarchies are evaluated.

### 2. Bounded Swept Volume Sampling ($O(1)$)
Instead of continuous constructive solid geometry (CSG) or dense chunk voxel queries:
- The cutting edge is represented by exactly $5$ teeth points.
- Only $4$ time subdivisions are interpolated between previous and current ticks ($20$ sample points per tick).
- Duplicate `BlockPos` entries are deduplicated using a `LinkedHashSet`. In a typical cutting tick, only $1$ to $4$ unique `BlockPos` cells are touched.

### 3. Early-Exit Gates
- **Stationary Check**: If the bucket has moved less than $0.008\text{ m}$, the swept calculation returns immediately.
- **Capacity Check**: If the bucket is full, terrain queries are bypassed entirely.
- **Direction Check**: If the teeth are not leading into the motion vector, terrain queries are skipped.
- **Empty Dump Check**: If the bucket is empty or upright, dumping calculations exit immediately.

### 4. Compact Single Authoritative Entity
Rather than decomposing the machine into separate child entities for each track link, cab, boom, and bucket (which would inflate entity tracker packets and server tick lists), the entire machine is managed by **one single entity**: `GroundworksExcavatorEntity`.

### 5. Network Traffic Optimization
- Client input packets are sent only when the operator's input state changes or every 5 ticks as a keepalive.
- Visual state updates use Minecraft's `SynchedEntityData` which only broadcasts values when they change.
