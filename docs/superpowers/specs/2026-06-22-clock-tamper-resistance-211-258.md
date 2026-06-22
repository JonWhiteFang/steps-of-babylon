# Design Spec — Clock-tamper resistance (#211) + schema-doc gap-fill (#258)

**Date:** 2026-06-22
**Issues:** #211 (`severity:major`, data-integrity/game-design — "Time-gated mechanics trust the unguarded
device clock; Labs/season-pass/streak cheatable", TIME-1) · #258 (`severity:major`, documentation —
"database-schema.md stale on the migration floor")
**Status:** Draft — pending Adversarial Review Gate (spec stage)
**Scope decisions (developer):** #211 = **monotonic anti-rollback guard + reboot-durable max-wall-clock
floor** (NOT the full time-integrity subsystem; NOT accept-and-document). The reboot-spanning *forward*
jump on research is documented-as-accepted. #258 = a **small gap-fill** (the headline staleness was
already resolved by #237).

---

## 1. Problem

### #211 — the time axis is unguarded
Every time-gated mechanic resolves against the raw device wall-clock, so a player can:
- set the clock **forward** → instantly finish Labs research (the real-time-gated progression pillar)
  and floor the gem rush-cost;
- set the clock **backward** → re-farm daily-login rewards / streaks / the season-pass daily gem bonus.

The step anti-cheat is strong, but nothing guards the *time* axis. Grounded surface (verified):
- `domain/time/TimeProvider` exposes only `now(): Instant` / `today(): LocalDate` — **no monotonic clock**;
  it was a testability seam, not anti-cheat. Only battle/missions paths inject it; the engagement use
  cases read the wall-clock raw.
- Research persists an **absolute `completesAt` wall-clock timestamp** (`LabResearchEntity.completesAt`),
  set at start (`StartResearch` `now + durationMs`); completion gates are `now >= completesAt`
  (`CheckResearchCompletion.kt:10`, `CompleteResearch.kt:19`).
- `TrackDailyLogin.kt:46` reads `System.currentTimeMillis()` raw for the season-pass day-bonus gate; the
  streak is keyed on device `LocalDate`.
- **No persisted monotonic baseline or last-seen wall-clock exists anywhere.** `AntiCheatPreferences`
  (the `anti_cheat_prefs` SharedPreferences wrapper) holds only step-anti-cheat counters — it is the
  natural home for a time baseline.

### #258 — schema doc (mostly already resolved)
The issue (filed 2026-06-18) cited `database-schema.md:243-248,273` claiming the bare
`fallbackToDestructiveMigration()` was in use and omitting the migration floor. **The data-integrity
wave #237 (PR #276, `0f32ac6`) + the #282 doc sweep already fixed the headline staleness:** the doc now
documents `MIGRATION_FLOOR = 7`, the `MigrationChainTest` guard, and line 281 explicitly states the bare
`fallbackToDestructiveMigration()` is "**not** used anywhere" (only `…OnDowngrade`). **One #258 ask
remains unaddressed:** an explicit statement of the *forward-upgrade-from-pre-v7* runtime consequence — a
surviving pre-v7 install would **throw a missing-migration `IllegalStateException` on upgrade, NOT
silently reset** — plus the floor-safety rationale.

## 2. Goal & non-goals

**Goal (#211):** raise the time-axis bar proportionately for an offline single-player game —
- **fully close** backward-rollback farming (login/streak/season-pass), reboot-durably;
- **close the in-session forward jump** on research (clock-forward while the app runs);
- with a small, well-bounded `TimeIntegrity` component + a baseline persisted in the existing
  `AntiCheatPreferences`.

**Goal (#258):** add the missing pre-v7 upgrade-crash note + floor rationale to `database-schema.md`.

**Non-goals (explicit, documented-as-accepted at issue-close):**
- **Reboot-spanning forward jump on research.** Force-stop → jump clock forward → relaunch still
  completes research: `elapsedRealtime` resets across process death, so no in-session delta survives to
  cap the wall jump. Full defense needs persisted boot-count/uptime reconciliation — the rejected
  "full subsystem" option. Backward rollback IS reboot-durable (via the max-wall-clock floor).
- **Migrating `BillingManagerImpl`'s season-pass expiry authority** (`isSeasonPassActive()` /
  `purchaseTime + 30d`) onto the guarded clock — that is the full-subsystem scope; this wave touches the
  engagement use cases only.
- **Re-modelling research as monotonic accrual** (deeper change to the entity + RushResearch math).
- No new Room schema/migration (the baseline lives in SharedPreferences, not Room).

## 3. Invariants & constraints

1. **Domain purity.** `TimeIntegrity` is pure Kotlin in `domain/time/` — zero Android imports
   (`DomainPurityTest`). The `SystemClock.elapsedRealtime()` read lives in the data layer
   (`TimeBaselineStore`/`AntiCheatPreferences`).
2. **No false positives on legitimate play.** A real reboot does NOT move the wall-clock backward, so it
   must NOT trip the rollback guard. DST / timezone changes preserve the UTC epoch-millis the guard
   compares, so they must NOT trip it either. These are pinned by boundary tests.
3. **Behavior change is limited to the two intended effects:** (a) a rolled-back clock earns no
   streak/login/season-bonus credit; (b) an in-session forward jump doesn't instantly complete research.
   Every *legitimate* engagement path stays behavior-identical (regression-tested).
4. **Plain-Kotlin use cases.** The engagement use cases stay `@Inject`-free plain Kotlin (project
   convention); the guard threads through their constructors/params exactly as the existing `now`
   parameter does. Callers (LabsViewModel/HomeViewModel/DailyStepManager) obtain the store via Hilt.

## 4. Component design (#211)

### A. `TimeIntegrity` — pure-domain decision core (`domain/time/TimeIntegrity.kt`)
```kotlin
/** Persisted tamper baseline (three Long slots). */
data class TimeBaseline(
    val lastElapsedRealtime: Long,   // SystemClock.elapsedRealtime() at last checkpoint (since-boot)
    val lastWallClock: Long,         // System.currentTimeMillis() at last checkpoint
    val maxWallClockSeen: Long,      // highest wall-clock ever observed — the reboot-durable rollback floor
)

/** Current clock readings, taken together at one instant. */
data class TimeReading(val elapsedRealtime: Long, val wallClock: Long)

sealed interface TimeVerdict {
    val newBaseline: TimeBaseline    // always advance the persisted baseline (maxWallClockSeen monotonic)
    data class Trusted(override val newBaseline: TimeBaseline) : TimeVerdict
    data class Rollback(override val newBaseline: TimeBaseline) : TimeVerdict   // wallClock < maxWallClockSeen
}

object TimeIntegrity {
    /**
     * Update the baseline from a fresh reading and classify the time axis.
     * - baseline == null (first run): Trusted; seed maxWallClockSeen = reading.wallClock.
     * - reading.wallClock < baseline.maxWallClockSeen: Rollback (backward jump — reboot-durable).
     * - else: Trusted. maxWallClockSeen = max(prev, reading.wallClock) in every branch.
     */
    fun evaluate(baseline: TimeBaseline?, reading: TimeReading): TimeVerdict

    /**
     * Trusted elapsed wall-time since the baseline, for the in-session forward-jump guard:
     * `min(wallClock - baseline.lastWallClock, elapsedRealtime - baseline.lastElapsedRealtime)`
     * clamped to >= 0. A forward wall jump can't claim more "real time" than the monotonic clock advanced
     * this session. Returns the full wall delta when elapsedRealtime is unavailable/reset is detected
     * (elapsed delta < 0 → treat as reboot → fall back to wall delta, which the Rollback floor still guards).
     */
    fun trustedElapsedSince(baseline: TimeBaseline, reading: TimeReading): Long
}
```

### B. `TimeBaselineStore` — data-layer baseline (extends `AntiCheatPreferences`)
- Three new `Long` keys in the existing `anti_cheat_prefs` SharedPreferences: `tamper_last_elapsed`,
  `tamper_last_wall`, `tamper_max_wall`.
- `readTimeBaseline(): TimeBaseline?` — returns `null` until first written (sentinel: a `has_time_baseline`
  flag or `tamper_max_wall == 0L` meaning unset).
- `writeTimeBaseline(b: TimeBaseline)` — persists the three slots (called with `verdict.newBaseline`).
- Reads `SystemClock.elapsedRealtime()` + `System.currentTimeMillis()` to build a `TimeReading`
  (the one Android-coupled seam; thin, documented).
- **DataDeletionManager** must clear these three keys (the file is already wiped on data deletion — verify
  the three new keys are covered by the existing `anti_cheat_prefs` clear).

### C. Wiring into the engagement paths
- **Backward-rollback guard (fully closed):** the daily-login/streak credit path (`TrackDailyLogin` +
  its caller `DailyStepManager`/`HomeViewModel`) takes the `TimeVerdict`. On `Rollback`: **skip the
  credit** — do not advance the streak, do not grant the season-pass day bonus, do not move the
  last-login date forward. (Refuse to reward a rolled-back clock; softer "cap at last-seen" was
  considered and rejected in favor of refuse-credit.)
- **In-session forward-jump guard (research):** `CheckResearchCompletion`/`CompleteResearch` evaluate
  completion using monotonically-defensible time. Concretely, the caller computes a *trusted now* =
  `baseline.lastWallClock + trustedElapsedSince(baseline, reading)` and the completion gate uses that
  trusted now instead of the raw wall-clock, so a within-session forward jump can't push `trustedNow`
  past `completesAt` faster than real elapsed time. (Reboot-spanning forward jump remains accepted — §2.)
- The guard is evaluated once per relevant engagement cycle (app foreground / daily pipeline tick), the
  baseline advanced via `writeTimeBaseline(verdict.newBaseline)`. Exact checkpoint cadence pinned in the
  plan; the principle: one evaluate→persist per engagement-resolution pass.

## 5. Component design (#258)

In `docs/database-schema.md`, **add** (no contradiction with the already-correct #237 content) an
explicit pre-v7 upgrade note near the migration-floor line (250) / the Security fallback line (281):
> **Pre-v7 upgrade behavior:** there is no v1→v7 upgrade path — those migrations were never written
> (floor assumed at v7). A surviving pre-v7 install hitting an upgrade would therefore throw Room's
> missing-migration `IllegalStateException` (a launch crash), **not** silently reset — because only
> `fallbackToDestructiveMigrationOnDowngrade` is configured, never the bare
> `fallbackToDestructiveMigration()`. The floor is assumed safe because the app shipped to the Play
> internal track at schema ≥ v7; no public/pre-v7 install exists to upgrade. [Confirm this rationale is
> accurate at implementation; if the floor is genuinely uncertain, the alternative is to add a general
> `fallbackToDestructiveMigration` and document that instead — developer decision.]

## 6. Testing strategy

1. **`TimeIntegrityTest` (pure JVM — bulk of coverage), table-driven over `TimeBaseline`/`TimeReading`:**
   first-run (null → Trusted + seed); normal forward advance (Trusted, maxSeen advances); **backward
   rollback** (wall < maxSeen → Rollback, even with elapsedRealtime advancing — the reboot-durable
   streak-farm case); **in-session forward jump** (wall +30d, elapsed +5min → `trustedElapsedSince` ≈ 5min);
   boundary cases (wall == maxSeen → Trusted; elapsed reset to 0 across reboot with wall ≥ maxSeen →
   Trusted, NO false rollback; DST/timezone = unchanged epoch millis → Trusted).
2. **Use-case tests (extend existing, fakes):** `TrackDailyLoginTest` += rollback → not credited +
   normal-advance → credited (regression; finally exercises the expiry boundary the old test couldn't).
   `CheckResearchCompletionTest`/`CompleteResearchTest` += in-session forward-jump → not complete;
   existing legitimate-completion tests stay green.
3. **`TimeBaselineStore`** — SharedPreferences read/write covered on the plain JVM lane (mirror the
   existing `AntiCheatPreferences` test pattern); the `elapsedRealtime()` read is a thin documented seam.
4. **Guards stay green:** `DomainPurityTest` (TimeIntegrity has no Android import). Full
   `testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL; headline test count rises.

## 7. Risks & mitigations

| Risk | Mitigation |
|---|---|
| False rollback on real reboot / DST / timezone | Rollback only on `wallClock < maxWallClockSeen`; reboot doesn't move wall-clock backward; DST/timezone preserve UTC epoch millis. Boundary tests pin no-false-positive. |
| Behavior change to the engagement economy | Only two intended effects (rollback → no credit; in-session forward jump → research not instant); all legitimate paths regression-tested. |
| Accepted gap (reboot-spanning forward jump on research) misread as a defect | Documented in spec §2 + security-model.md + at issue-close, with rationale. |
| Scope creep into BillingManagerImpl season-pass authority | Out of scope (full-subsystem option); engagement use cases only. |
| #258 floor-safety rationale is wrong | Plan confirms the pre-v7 history before asserting; alternative (add general fallback) offered. |

## 8. Acceptance criteria

- `TimeIntegrity` pure-domain core + `TimeBaselineStore` baseline (3 keys in `anti_cheat_prefs`,
  cleared on data deletion); `DomainPurityTest` green.
- Backward-rollback farming closed reboot-durably (login/streak/season-bonus not credited on a clock
  set behind the max-seen floor); in-session research forward-jump closed.
- The documented-accepted boundary (reboot-spanning forward jump on research) recorded in
  security-model.md + at #211 close.
- `database-schema.md` states the pre-v7 missing-migration-crash consequence + floor rationale (#258).
- Full `testDebugUnitTest lintDebug assembleDebug` green; headline count updated. ADR added (anti-cheat
  time-axis design decision + scope boundary).
- #211 + #258 closeable (#258 noting #237 pre-resolved the headline staleness).
