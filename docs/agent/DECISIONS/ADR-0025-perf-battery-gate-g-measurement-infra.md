# ADR-0025: Perf/battery (Gate G) measurement infra — multi-module benchmark tooling on AGP-9, + safe GC-churn fixes

**Status:** Accepted — 2026-06-16 (merged to `main` via PR #184, squash `8f3c2ee`). Issue #26 (V1X-23), Gate G.

## Context

Gate G (Closed-Test Readiness) asks for Baseline Profiles, startup profiling, a battery audit, and OEM
device validation. Two structural facts shaped the decision:

1. **Half of #26 is not repo-closable.** Overnight idle-drain and OEM background-process behaviour
   (Samsung/Xiaomi/OnePlus/Pixel) need physical devices and developer judgment. Only the *measurement
   infrastructure* + already-identified *safe runtime fixes* are code-addressable.
2. **The benchmark toolchain collided with AGP 9.0.1** (very new). The stable `androidx.baselineprofile`
   1.4.1 plugin **throws at plugin-apply** on AGP 9.0.1 (`Module :app is not a supported android module`),
   *before* the documented `newDsl = false` workaround is even reachable. AGP-9 support only exists in the
   `1.5.0-alpha` line. This contradicts the project's #33 stance (moved Health Connect off alpha onto
   stable) — but #33 was about *load-bearing shipped* code, not dev tooling.

## Decision

**Scope:** ship the in-repo slice only — measurement infra + behaviour-preserving GC-churn fixes + docs.
Defer the device-measured half (battery/OEM/startup-numbers) as an explicit `[deferred]` Gate-G line.

**Measurement infra (multi-module):** add two `com.android.test` modules, `:baselineprofile` (generates
the committed Baseline Profile) and `:macrobenchmark` (`StartupBenchmark` + `JourneyBenchmark`), plus
`androidx.profileinstaller` on `:app`. The project is now multi-module (`:app` + 2 dev-tooling modules).

**Version pin:** benchmark/baselineprofile **1.5.0-alpha06** + uiautomator **2.4.0-beta02**, confined to
the two **non-shipping** test modules. `profileinstaller` stays **stable 1.4.1** — it is the only artifact
that enters the AAB. (User-approved override of #33 for dev tooling.)

**AGP-9 plugin wiring:** declare `android.test` + `androidx.baselineprofile` `apply false` in the ROOT
`build.gradle.kts` (pin the version once — a per-module version clashes with AGP already on the classpath).
Do **not** apply `org.jetbrains.kotlin.android` to a `com.android.test` module — AGP 9 provides built-in
Kotlin and applying it errors.

**#124 license-guard reconciliation:** the baselineprofile plugin auto-generates `benchmarkRelease` /
`nonMinifiedRelease` variants whose `*Release` task names would false-trip the fail-closed license-key
guard. Narrow the guard to a **per-task** predicate: keep the broad `^(bundle|assemble|package).*Release$`
match, add `!name.contains("Benchmark") && !name.contains("NonMinified")`. Per-task (not whole-graph) so a
combined `bundleRelease`+benchmark graph still hard-fails on a blank key — the #124 invariant is preserved.

**Safe GC-churn fixes (behaviour-preserving, TDD):**
- **A28** — replace 3 per-frame `filterIsInstance().filter{}` collision-sweep allocations with engine-owned
  reusable scratch buffers, filled in one partition pass under `entitiesLock` (#118). One `enemyScratch`
  fill serves the whole sweep (matches the old single snapshot; #146 corpse-guard intact; NOT the #125
  cross-frame cache — `getAliveEnemies()` untouched).
- **A31** — cache the CHRONO_FIELD overlay `Paint` instead of allocating per render frame.
- **A29** — `distinctUntilChanged` after the `.map{}` on `observeProfile/Wallet/Tier` to suppress no-op
  re-emissions to every screen ViewModel (safe: `PlayerProfile`/`PlayerWallet` are data classes; no
  consumer uses these as a bare trigger).

**Excluded:** audit A11/A18 (`getAliveEnemies` cross-frame caching) — its recommended fix violates the
#125 invariant; left as a dedicated follow-up. A30 was already fixed (`StatsViewModel` QUARTER branch).

## Alternatives considered

- **Stable 1.4.1 + `newDsl = false`** (original plan) — REJECTED: empirically throws at plugin-apply on
  AGP 9.0.1 before `newDsl` is read. Not viable.
- **Defer the benchmark modules entirely** — REJECTED: weakens the "Baseline Profiles implemented"
  acceptance criterion when AGP-9-compatible tooling exists (1.5.0-alpha).
- **Downgrade project AGP to satisfy stable 1.4.1** — REJECTED: AGP is a project-wide fragile dependency;
  downgrading it for a non-shipping dev tool risks the entire build/release lane.
- **Capture startup-timing numbers now** — DEFERRED: needs a non-debuggable `benchmark` build type on the
  fragile `app/build.gradle.kts` (interacts with the #124 guard) + a physical device for trustworthy
  numbers (emulator timings are non-gating per the spec). Not worth a fragile-zone change for advisory
  emulator numbers; deferred to the device pass. (User-confirmed.)

## Consequences

- **Positive:** Baseline Profile generated + committed (18,804 rules / 1,114 app-specific) — the
  load-bearing Gate-G deliverable. Repeatable Macrobenchmark harness in place. Measurable GC-churn removed
  from the 60fps loop + render path + the profile-Flow re-query storm — all behaviour-preserving (1045→1052
  JVM, full suite green). Battery process + wake-source inventory documented.
- **Negative / tradeoffs:** an alpha dependency now exists in the build — contained to two non-shipping
  modules (the AAB is unaffected; `profileinstaller` is stable). Startup-timing *numbers* are not yet
  captured. The generator currently captures cold-launch + Home only (deeper nav is an on-device TODO).
- **Follow-ups:** device pass (overnight idle-drain + OEM matrix + startup numbers + a non-debuggable
  `benchmark` build type), recomposition profiling, the A11/A18 corpse-safe snapshot refactor, the candidate
  cadence tunings (notification 30→60 s, HC sync 15→30 min) once device-measured. Revisit the benchmark
  pin when 1.5.x reaches stable.

## Links

- Commits: `36dea10`..`8d485f7` (branch `feat/26-perf-battery-gate-g`).
- Spec: `docs/superpowers/specs/2026-06-16-perf-battery-gate-g-design.md`; plan:
  `docs/superpowers/plans/2026-06-16-perf-battery-gate-g.md`.
- Docs: `docs/performance/battery-audit.md`, `docs/performance/startup-baseline.md`; gate:
  `docs/plans/plan-FORWARD.md` (Gate G).
- Related ADRs: ADR-0005 (#124 billing signature verification — the guard this narrows), ADR-0012
  (Simulation extraction — the engine A28 touches), ADR-0018 (CI), ADR-0015 (#33-adjacent alpha stance).
