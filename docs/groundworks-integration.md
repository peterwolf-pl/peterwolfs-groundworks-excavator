# Groundworks API Integration & Material Conservation

## Architectural Boundary

All communication between the Excavator mod and Peterwolf's Groundworks is encapsulated in:

```text
com.piotrek.groundworksexcavator.integration.groundworks.GroundworksExcavationAdapter
```

The excavator mod maintains no separate terrain data, voxel grids, or falling-block entities.

---

## Unit Conservation Standard

Groundworks defines terrain in integer microvoxels:

$$\text{Full Block} = 512\text{ units} = 1.000\text{ m}^3$$

$$\text{Excavator Bucket Capacity} = 256\text{ units} = 0.500\text{ m}^3$$

$$\text{One Microvoxel} = 1\text{ unit} \approx 0.001953\text{ m}^3$$

### Invariant
Material is strictly conserved during all operations:

$$\Delta V_{\text{terrain}} = \Delta V_{\text{bucket}}$$

- If Groundworks removes $N$ units:
  The bucket receives **exactly** $N$ units.
- If the bucket has room for only $M < N$ units:
  The excavator requests only $\min(N, M)$ units from Groundworks.
- If Groundworks accepts $K$ units during deposit:
  The bucket extracts **exactly** $K$ units.
- If deposit space is constrained (e.g. hitting bedrock ceiling):
  Rejected units remain safely in the bucket. Zero units are destroyed.

---

## Adapter Contract Methods

```java
// Query whether a position is convertible or existing granular terrain
boolean isDiggable(ServerLevel level, BlockPos pos);

// Spherical brush excavation at exact world hit coordinates
ExcavationResult excavateAt(ServerLevel level, BlockPos pos, Vec3 hitLocation, int maxUnits);

// Top-down excavation from cell
ExcavationResult excavate(ServerLevel level, BlockPos pos, int maxUnits);

// Upward-overflow deposition into terrain
DepositResult deposit(ServerLevel level, BlockPos pos, GranularMaterial material, int units);

// Sample microvoxel surface height (0..7) within a granular column
int getSurfaceHeight(ServerLevel level, BlockPos pos, int localX, int localZ);
```

---

## Material Compatibility Rules

1. **Material Purity**:
   An excavator bucket can contain only one material type at a time (e.g. `dirt`, `sand`, or `gravel`).
2. **Mismatch Handling**:
   If a bucket contains dirt and the cutting edge strikes sand, the bucket refuses to accept the sand until emptied. No silent material transmutation occurs.
