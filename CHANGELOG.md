# Changelog

All notable changes to **Peterwolf's Groundworks Excavator** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.2+26.3] - 2026-10-09

### Added
- **Fleet AutoTrench Integration with Dump Truck**:
  - Excavator automatically spots and communicates with Peterwolf's Groundworks Dump Truck during trenching.
  - Bucket dynamically centers over dump truck bed during reverse swing.
  - Dynamic cab yaw calculation for smooth unloading into truck beds.
  - Automated dual horn signaling upon completing each station to command the dump truck to advance one block.
- **Visual & Lighting Polish**:
  - Restored full crawler track geometry and compact work lamp textures.
  - Added ruby-red rear tail lamps to the excavator counterweight.
- **Full Localization (i18n)**:
  - All HUD telemetry, status readouts, and commands localized in English (`en_us`) and Polish (`pl_pl`).

---

## [0.1.1+26.3] - 2026-10-08

### Added
- Multi-bucket attachment system: Standard Bucket, Large Bucket, and Pneumatic Hammer (press `Z`).
- In-cab heads-up display (HUD) showing carried material volume in $\text{m}^3$, fill gauge, and attachment state.

---

## [0.1.0+26.3] - 2026-10-06

### Added
- Initial release of **Peterwolf's Groundworks Excavator** for Minecraft 26.3 (Wilderness Bound) Fabric.
- True volumetric physical excavation with cutting-edge swept volume physics.
- Differential crawler track steering and dynamic terrain pitch conformity.
