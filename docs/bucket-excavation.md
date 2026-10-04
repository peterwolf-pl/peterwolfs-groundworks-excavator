# Bucket Excavation & Dumping Mechanics

## Swept Cutting Volume

Excavation does not occur simply because the bucket intersects terrain geometry. Instead, it is governed by bucket dynamics:

$$\vec{v}_{\text{edge}} = \vec{p}_{\text{current}} - \vec{p}_{\text{previous}}$$

### Cutting Preconditions
1. **Movement Threshold**:
   $\|\vec{v}_{\text{edge}}\| \ge 0.008\text{ m/tick}$. A stationary bucket resting on soil will not dig.
2. **Cutting Alignment**:
   The cutting edge must lead into the motion:
   $$\frac{\vec{v}_{\text{edge}}}{\|\vec{v}_{\text{edge}}\|} \cdot \vec{d}_{\text{cutting}} \ge -0.35$$
   Pushing or dragging the backside/heel of the bucket through dirt will not cut.
3. **Capacity Availability**:
   The bucket must have room ($\text{storedUnits} < \text{capacity}$).
4. **Discontinuity Guard**:
   If $\|\vec{v}_{\text{edge}}\| > 2.5\text{ m/tick}$ (e.g. teleportation, chunk reload, entity spawn), the swept volume is discarded to prevent instantaneous subterranean ravines.

---

## Teeth Discretization

The cutting edge has a physical width of $0.80\text{ m}$ and is discretized into 5 cutting points:

$$p_i = \vec{c}_{\text{edge}} + \vec{u}_{\text{right}} \cdot \left(-\frac{W}{2} + i \cdot \frac{W}{4}\right), \quad i \in \{0, 1, 2, 3, 4\}$$

During each tick, 4 intermediate sub-steps interpolate between the previous teeth positions and current teeth positions:

$$p_i(\alpha) = (1 - \alpha) p_i^{(t-1)} + \alpha p_i^{(t)}, \quad \alpha \in \left\{0, \frac{1}{3}, \frac{2}{3}, 1\right\}$$

This swept point cloud maps directly to candidate `BlockPos` targets, ensuring a seamless trench without skipped voxels.

---

## Granular Contact Response

The boom and stick use dense steel-contact samples. The bucket and cutting teeth are working tools, so they are excluded from the blocking arm volume and may enter material during a valid digging stroke.

A boom, stick, cabin, or chassis candidate is accepted only when it introduces no new boom/stick steel contact with granular terrain. If arm steel is already inside material after loading an old world, only movements that reduce penetration are accepted.

When teeth are embedded:

- turntable rotation and differential steering cannot drag them sideways;
- chassis movement that is mainly lateral to the cutting direction stops immediately;
- boom, stick, and bucket hydraulics remain available for the digging stroke;
- motion along the cutting direction and extraction motion remain available.

A shallow surface skim does not fill the bucket. It displaces at most `32` integer units per tick, in batches of at most `8` units per contacted cell, into the next cell in the travel direction. Groundworks relaxation turns this displaced material into a small local mound.

Deep tooth contact keeps the existing scoop behavior. The standard bucket can take up to `32` units per tick. The large bucket keeps its deeper secondary bite.

---

## Dumping Flow Mechanics

Material leaves the bucket via gravity flow when the bucket orientation is tilted downward past the dump threshold:

$$\theta_{\text{dump}} = \arcsin(-\vec{d}_{\text{bucket}} \cdot \hat{y}) \ge 30^\circ$$

### Variable Flow Rate
The pour rate dynamically scales from a trickle to a torrent based on how steep the bucket is inverted:

$$\text{FlowRate}(\theta) = \text{round}\left(\text{lerp}\left(\min(1, \frac{\theta - 30^\circ}{45^\circ}), 8, 32\right)\right) \text{ units/tick}$$

- At $30^\circ$ tilt: $\sim 8\text{ units/tick}$ ($0.016\text{ m}^3/\text{tick}$)
- At $45^\circ$ tilt: $\sim 16\text{ units/tick}$ ($0.031\text{ m}^3/\text{tick}$)
- At $75^\circ+$ tilt: $\sim 32\text{ units/tick}$ ($0.063\text{ m}^3/\text{tick}$)

### Pour Point
Material leaves at the **bucket lip**:

$$\vec{p}_{\text{pour}} = \vec{p}_{\text{cutting}} + \vec{d}_{\text{bucket}} \cdot 0.12 + \hat{y} \cdot 0.08$$

The material enters Groundworks at `BlockPos.containing(lip)`, where Groundworks' upward overflow and granular relaxation algorithms construct a realistic cone-shaped soil pile.
