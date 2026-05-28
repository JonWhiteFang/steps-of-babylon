# ADR-0012: Simulation Extraction (Phase 1 — Pure Math Helpers)

**Status:** Accepted
**Date:** 2026-05-28
**Supersedes:** None
**Superseded by:** None

## Context

The `presentation/battle/engine/GameEngine.kt` file had grown to 1004 lines mixing pure simulation logic (HP healing math, damage modifiers, threshold-crossing accumulators) with Android Canvas/SurfaceView entity rendering. This violates the project's Clean Architecture layering rule (`domain/` has zero Android imports) and forces all simulation tests to use Robolectric reflection patterns (`GameEngineTest.kt` is 700+ lines of reflective helpers).

The V1X-09 sub-plan in `plan-V1X-roadmap.md` proposes a full extraction of simulation logic to `domain/battle/engine/Simulation.kt` (~400 LOC pure Kotlin) with renderer delegation, but this is a 3-4 day refactor with significant regression risk across the entire battle subsystem.

## Decision

Adopt the plan's **Option C: Partial extraction** — Phase 1 lifts only the safest pure-math helpers into a new `domain/battle/engine/SimulationMath.kt` `object` and has GameEngine delegate to them. Entity rendering, collision system, wave spawning, and UW lifecycle remain in the presentation layer pending future phases.

Functions extracted in Phase 1:

- `recoveryPulseAmount(level, maxHp)` — Recovery Packages heal-per-pulse formula
- `chronoMultiplier(active, slowFactor)` — CHRONO_FIELD UW enemy-slow factor
- `thornReflectionDamage(rawDamage, thornPercent, conditionMultiplier)` — THORN_DAMAGE reflection
- `lifestealHealAmount(damageDealt, lifestealPercent)` — LIFESTEAL heal-per-hit
- `tickLifestealAccumulator(accumulator, healAmount)` — sub-1-HP threshold-crossing tracker
- `clampHp(candidateHp, maxHp)` — defensive HP clamp helper

Constants moved alongside (`RECOVERY_INTERVAL_SECONDS`, `RECOVERY_PERCENT_PER_LEVEL`, `RECOVERY_PERCENT_PER_PULSE_CAP`, `CHRONO_SLOW_FACTOR_DEFAULT`, `LIFESTEAL_CAP`).

## Consequences

### Positive

- New `SimulationMathTest.kt` with 27 pure-JVM tests directly exercising the extracted math (no Robolectric needed).
- GameEngine's `applyThorn`, `applyLifesteal`, and `tickRecoveryPackages` reduced to ~3-line delegating wrappers.
- Domain layer now contains *some* simulation math, establishing the package structure for future Phase 2 extraction.
- Closes the dead-tree problem where pure-math functions were trapped in a Canvas-using class.

### Negative

- Two parallel locations now contain "simulation math": the new `SimulationMath` object and the still-presentation-layer entity update logic. Deferred items (RapidFire timer state, GOLDEN_ZIGGURAT × overdrive fortuneMultiplier stacking, projectile movement, UW activation) remain in GameEngine.
- Tests in GameEngineTest still exercise the old reflection-heavy pattern for the bits that didn't move.
- Phase 1 alone does not enable V1X-27 (simulation tooling for headless balance simulation) — Phase 2 is required for that.

### Neutral

- `floor` import removed from GameEngine; `min` retained (used elsewhere).
- `RECOVERY_*` companion-object constants in GameEngine deleted; no external references found.
- No behavior change. All 760 existing JVM tests continue to pass.

## Future Phases

**Phase 2 (V1X-09b, deferred):** Extract entity update logic. Each `presentation/battle/entities/*` file splits into:
- `domain/battle/entity/<Name>State.kt` — pure data class + update(deltaTime, world) logic
- `presentation/battle/entities/<Name>Renderer.kt` — Canvas-using render() impl

Estimated effort: 3-4 days. Requires V1X-08 (instrumented tests) as a safety net.

**Phase 3 (V1X-09c, deferred):** Extract `GameEngine.update()` loop into `domain/battle/engine/Simulation.kt`. Migrate `BattleViewModel` from polling `engine.uiSnapshot` to collecting `simulation.events: Flow<SimulationEvent>`.

Estimated effort: 2 days.

## References

- `domain/battle/engine/SimulationMath.kt` — extracted pure-math helpers
- `app/src/test/java/com/whitefang/stepsofbabylon/domain/battle/engine/SimulationMathTest.kt` — 27 pure-JVM tests
- `presentation/battle/engine/GameEngine.kt` — refactored to delegate (`applyThorn`, `applyLifesteal`, `tickRecoveryPackages`)
- `docs/plans/plan-V1X-roadmap.md` § V1X-09 — full sub-plan spec including Phase 2/3 details
