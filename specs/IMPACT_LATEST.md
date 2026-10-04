# Impact: granular terrain contact

## Target

- `GroundworksExcavatorEntity` hydraulic joint updates
- `ArmKinematics` physical arm sampling
- `BucketExcavationController` surface-contact behavior
- `GroundworksExcavationAdapter` exact occupancy queries and conservative displacement

## Dependents (6)

- Client input drives the six server-authoritative control values.
- Entity rendering reads the synchronized joint angles.
- Bucket excavation consumes consecutive bucket poses.
- Bucket dumping consumes the accepted current pose.
- Track movement can rotate an embedded cutting edge.
- Groundworks owns all removed and deposited integer material units.

## Test coverage

- Existing kinematics, alignment, swept-volume, bucket-volume, and steering tests.
- Add pure contact-constraint and push-direction tests.
- Extend Client GameTest with granular-contact scenes and server assertions.

## Risk: High

The change gates authoritative arm motion and moves material between terrain cells. A defect can lock the controls, desynchronize the model and hit geometry, or lose material.

## Recommended action

Proceed test-first. Keep Groundworks authoritative, preserve exact integer mass, reject only motion that increases arm penetration, and keep tooth-aligned cutting available.
