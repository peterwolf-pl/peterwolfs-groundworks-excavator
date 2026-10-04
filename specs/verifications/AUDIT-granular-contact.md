---
type: verification
context: granular-contact-physics
---

# Code audit: granular contact physics

## Result

PASS

## Checklist

- ✓ Supply chain: no dependency changes; no secret-like values in the diff.
- ✓ Security: server-authoritative movement and terrain mutation; no user data, authentication, external command, or deserialization changes.
- ✓ Scope: changes are limited to steel/granular contact, embedded-tooth motion, surface displacement, tests, and documentation.
- ✓ Correctness: joint and chassis candidates cannot introduce new steel penetration; an existing penetration can only decrease.
- ✓ Conservation: rejected surface displacement is restored; failed restoration throws instead of deleting units.
- ✓ Determinism: no random source was added; push direction depends only on authoritative movement.
- ✓ Tests: 27 JUnit tests pass. Client GameTest verifies blocked joint contact, blocked chassis contact, extraction, surface displacement, and mass conservation.
- ✓ Visual verification: `visual-tests/current/excavator_08_surface_push_mound.png` shows the resulting gravel mound.
- ✓ Build: `./gradlew build` passes.
- ✓ Hygiene: `git diff --check` passes; no dead or commented-out implementation was added.
- ✓ Documentation: excavation, Groundworks integration, and testing guides describe the new behavior.

## Refactoring smells reviewed

- No new dependency, middle-man, or primitive-obsession issue requires action.
- `GroundworksExcavatorEntity` remains large, but splitting the entity lifecycle is outside this focused physics change.
- Contact thresholds remain centralized in their owning controllers rather than duplicated across entity code.

## Red flags and exceptions

- This repository has no `CONVENTIONS.md`; the existing Gradle/Java style and architecture were used.
- No security-review artifact was required because the change does not process credentials, external input formats, or network trust boundaries.
- The collision model is sample-based rather than triangle-continuous. Dense samples and low per-tick joint speeds make it suitable for the current discrete Groundworks terrain, but it is not a rigid-body solver.
