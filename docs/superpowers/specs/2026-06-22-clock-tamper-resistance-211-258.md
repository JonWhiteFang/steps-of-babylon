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

**Threat model (the boundary this wave defends).** The adversary is a player changing the device clock
via **OS Settings → Date & time** (the cheap, ubiquitous exploit). Defending a **rooted / file-editing**
adversary — who can edit the plaintext `anti_cheat_prefs` baseline directly, which is *easier* than the
clock change it guards — is **out of scope**, consistent with the project's standing posture: root is
outside the Android threat model and `anti_cheat_prefs` is a by-design unencrypted `MODE_PRIVATE` store
(docs/external-reviews/2026-06-10; same residual-risk acceptance already applied to client-side IAP /
ad-reward integrity). The new time baseline correctly sits in the same trust tier as the entire existing
step-anti-cheat stack it extends.

**Goal (#211):** raise the time-axis bar proportionately for an offline single-player game, against the
OS-Settings clock adversary —
- **close** backward-rollback farming (login/streak/season-pass) against OS-level clock changes,
  reboot-durably;
- **close the in-session forward jump** on research (clock-forward while the app runs);
- with a small, well-bounded `TimeIntegrity` component + a baseline persisted in the existing
  `AntiCheatPreferences`.

**Goal (#258):** add the missing pre-v7 upgrade-crash note + floor rationale to `database-schema.md`.

**Non-goals (explicit, documented-as-accepted at issue-close):**
- **Rooted / file-editing adversary** — out of scope (see Threat model above).
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
   convention); callers (LabsViewModel/HomeViewModel/DailyStepManager) obtain the store via Hilt. The
   guard threads in **two different ways** (the seam is NOT uniform): the research use cases already have
   a `now: Long` param → pass the trusted-now there; `TrackDailyLogin.checkAndAward` has **no** `now`
   param → it gains a new `TimeVerdict` param (see §4.C).
5. **Single baseline owner.** Exactly one path (`DailyStepManager`, under its #120 mutex) `evaluate`s and
   persists the baseline; all other consumers are read-only. No two passes advance the floor (§4.C).

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
     *
     * In ALL branches (Trusted AND Rollback) the returned newBaseline sets lastWallClock =
     * reading.wallClock and lastElapsedRealtime = reading.elapsedRealtime (the checkpoint always
     * advances to "now" so the next delta is measured from here); only maxWallClockSeen is the
     * monotonic-max floor. This is benign for the forward-jump guard because trustedElapsedSince's
     * min(wallDelta, elapsedDelta) caps the result regardless — but pin it so the persisted baseline is
     * unambiguous.
     */
    fun evaluate(baseline: TimeBaseline?, reading: TimeReading): TimeVerdict

    /**
     * Trusted elapsed wall-time since the baseline, for the in-session forward-jump guard:
     * `min(wallClock - baseline.lastWallClock, elapsedRealtime - baseline.lastElapsedRealtime)`
     * clamped to >= 0. A forward wall jump can't claim more "real time" than the monotonic clock advanced
     * this session.
     *
     * Reboot fallback (HONEST consequence): when `elapsedRealtime - lastElapsedRealtime < 0` the device
     * rebooted (elapsedRealtime is since-boot), so there is no in-session monotonic delta to cap against
     * and this returns the FULL wall delta. The Rollback floor does NOT guard this — it only catches
     * *backward* jumps (`wallClock < maxWallClockSeen`). A forward jump that spans a reboot therefore
     * passes through uncapped → research completes. This is exactly the **accepted non-goal in §2**
     * (reboot-spanning forward jump on research); defending it needs the rejected boot-reconciliation
     * subsystem. Do NOT describe the floor as a backstop for the forward direction.
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

**Single baseline owner — NO double-advance (review-fix, was the highest-risk integration defect).**
Both `HomeViewModel.init` and the `DailyStepManager` daily pipeline call `TrackDailyLogin.checkAndAward`;
if each independently `evaluate`d + `writeTimeBaseline`d, two passes would race-advance the floor (and a
forward pass could persist a new floor that then makes the legitimate later pass look like a rollback).
**Resolution: exactly one owner evaluates+persists the baseline** — `DailyStepManager` (it already
serializes the daily pipeline under the #120 non-reentrant mutex; the baseline advance lives inside that
mutex). `HomeViewModel.init` is a **read-only** consumer: it reads the current baseline + a fresh reading,
derives a verdict for its own gating, but does **not** persist (does not advance the floor). The plan
pins this owner and the read-only contract; one `evaluate`→`writeTimeBaseline` per daily pipeline pass.

**`now`/verdict threading (review-fix — the seam differs per use case).** The three *research* use cases
(`CheckResearchCompletion.kt:10`, `CompleteResearch.kt:19`, `StartResearch.kt:27`) already expose a
`now: Long = System.currentTimeMillis()` param — pass the *trusted now* there (clean drop-in).
`TrackDailyLogin.checkAndAward` (`TrackDailyLogin.kt:21`) has **no** `now` param — it gains a new param
(the `TimeVerdict`, the natural carrier). So the wiring is "mirror the existing `now`" for research,
"add a new guard param" for login — not a uniform convention.

- **Backward-rollback guard (fully closed) — exhaustive Rollback semantics.** On a `Rollback` verdict the
  daily-login path performs **NO write for the tampered date** — an early no-credit return. Mapped to
  `TrackDailyLogin`'s actual writes (TrackDailyLogin.kt): (1) the gem+streak credit + season-pass +10-gem
  bonus + `updateStreak(..., todayDate)` last-login-date move (lines 43-50) — **suppressed**; (2) the
  `gemsClaimed = true` flag (line 52) + the `DailyLogin` row `upsert` (line 55) — **NOT written** (this is
  the load-bearing detail: if the rollback path set `gemsClaimed = true` for date D, a later *legitimate*
  arrival at D would be silently denied credit by the `!login.gemsClaimed` gate at line 32 — a
  denial-of-own-progress bug; the early-return avoids it); (3) the 1k-step power-stone grant (lines 26-29)
  is **step-gated, not time-gated** — it is OUTSIDE the rollback suppression (a rolled-back clock doesn't
  fake steps; the step anti-cheat owns that axis). Refuse-credit was chosen over a softer "cap at
  last-seen". A `TrackDailyLoginTest` rollback case asserts no `DailyLogin` row / no `gemsClaimed` is
  persisted for the tampered date.
- **In-session forward-jump guard (research) — anchoring invariant (review-fix; the guard is sound ONLY
  with the right anchor).** The completion gate uses *trusted now* =
  `baseline.lastWallClock + trustedElapsedSince(baseline, reading)`. **Invariant:** the `baseline` here
  MUST be the baseline captured at/before the current session's checkpoint and advanced only by the single
  owner — NOT re-read from a baseline the same forward-jump pass just advanced (which would fold the jump
  back into `lastWallClock` and re-accept it). Because the single owner advances the baseline under the
  mutex and the research read uses the pre-advance baseline for its delta, a within-session forward jump
  can't push `trustedNow` past `completesAt` faster than the monotonic clock moved. (Reboot-spanning
  forward jump remains accepted — §2; `trustedElapsedSince` returns the full wall delta on a reboot, by
  design.)

## 5. Component design (#258)

**This is a DOCUMENTATION-ONLY change — no code/config change.** The bracketed "add a general
`fallbackToDestructiveMigration`" alternative is **dropped**: that would be a behavioral DI change
(`DatabaseModule.kt:27` uses only `…OnDowngrade`) and would re-introduce exactly the bare destructive
fallback that `database-schema.md:281` correctly asserts is "not used anywhere." The floor-safety fact
is **verifiable, not uncertain** — the app first shipped to the Play internal track at schema ≥ v7
(CHANGELOG/release history; v1.0.0 was the first release), so no pre-v7 install exists in the wild to
upgrade. The plan re-confirms this from release history before asserting it; if it somehow proved false,
that would be a separate behavioral decision tracked apart from this doc gap-fill, NOT folded in here.

In `docs/database-schema.md`, **add** (no contradiction with the already-correct #237 content) an
explicit pre-v7 upgrade note near the migration-floor line (~250) / the Security fallback line (~281):
> **Pre-v7 upgrade behavior:** there is no v1→v7 upgrade path — those migrations were never written
> (floor assumed at v7). A surviving pre-v7 install hitting an upgrade would therefore throw Room's
> missing-migration `IllegalStateException` (a launch crash), **not** silently reset — because only
> `fallbackToDestructiveMigrationOnDowngrade` is configured, never the bare
> `fallbackToDestructiveMigration()`. The floor is safe because the app first shipped to the Play
> internal track at schema ≥ v7 — no public/pre-v7 install exists to upgrade.

## 6. Testing strategy

1. **`TimeIntegrityTest` (pure JVM — bulk of coverage), table-driven over `TimeBaseline`/`TimeReading`:**
   first-run (null → Trusted + seed); normal forward advance (Trusted, maxSeen advances); **backward
   rollback** (wall < maxSeen → Rollback, even with elapsedRealtime advancing — the reboot-durable
   streak-farm case); **in-session forward jump** (wall +30d, elapsed +5min → `trustedElapsedSince` ≈ 5min);
   boundary cases (wall == maxSeen → Trusted; elapsed reset to 0 across reboot with wall ≥ maxSeen →
   Trusted, NO false rollback; DST/timezone = unchanged epoch millis → Trusted).
2. **Use-case tests (extend existing, fakes):** `TrackDailyLoginTest` += rollback → not credited AND
   **no `DailyLogin` row / no `gemsClaimed` persisted for the tampered date** (the latched-credit-denial
   guard from §4.C); normal-advance → credited (regression; finally exercises the expiry boundary the old
   test couldn't); + a **latched-future-clock** case (a bogus future reading latches the floor; a
   subsequent legitimate-time pass is denied until real time exceeds the floor — asserts the accepted
   trade-off is deliberate). `CheckResearchCompletionTest`/`CompleteResearchTest` += in-session
   forward-jump → not complete; existing legitimate-completion tests stay green. Note the streak is keyed
   on `LocalDate` while the rollback floor compares epoch-millis — they're different axes, but the
   `LocalDate`-keyed credit is gated on the rollback verdict (§4.C), so a date-label rollback still
   refuses credit; a forward-only same-day date-label nudge is within the accepted forward-jump boundary.
3. **`TimeBaselineStore`** — SharedPreferences read/write. **Test convention (review-fix):**
   `AntiCheatPreferences` has NO direct JVM test (it's only mocked by its consumers), so mirror the
   *actual* JVM-lane SharedPreferences-test convention instead — **Robolectric**
   (`@RunWith(RobolectricTestRunner::class)` constructing the real class against
   `RuntimeEnvironment.getApplication()`), as in `OnboardingPreferencesTest` / `CrashBreadcrumbStoreTest`.
   The `SystemClock.elapsedRealtime()` read is a thin documented seam (Robolectric supplies a value;
   the pure tamper logic lives in `TimeIntegrity`, tested without Android).
4. **Guards stay green:** `DomainPurityTest` (TimeIntegrity has no Android import). Full
   `testDebugUnitTest lintDebug assembleDebug` BUILD SUCCESSFUL; headline test count rises.

## 7. Risks & mitigations

| Risk | Mitigation |
|---|---|
| False rollback on real reboot / DST / timezone | Rollback only on `wallClock < maxWallClockSeen`; reboot doesn't move wall-clock backward; DST/timezone preserve UTC epoch millis. Boundary tests pin no-false-positive. |
| Behavior change to the engagement economy | Only two intended effects (rollback → no credit; in-session forward jump → research not instant); all legitimate paths regression-tested. |
| Accepted gap (reboot-spanning forward jump on research) misread as a defect | Documented in spec §2 + §4.A `trustedElapsedSince` doc + security-model.md + at issue-close, with rationale. The reboot fallback returns the full wall delta BY DESIGN — the Rollback floor does NOT and is not claimed to guard the forward direction. |
| Latched-future-clock denial-of-own-progress | A single bogus *future* wall-clock reading latches `maxWallClockSeen` forward, then refuses legitimate streak/login/season credit until real time passes the bogus floor. Accepted trade-off (a forward-set is the player's own action; the alternative — trusting any backward move — reopens the farm). Recorded as a deliberate edge + a §6 test case, not discovered in production. (Westward timezone travel does NOT trip it: timezone changes preserve UTC epoch millis.) |
| Rooted / file-edit defeat of the baseline | Out of scope per the §2 threat model (rooted is outside the Android threat model; `anti_cheat_prefs` is a by-design plaintext store, same trust tier as the whole anti-cheat stack). |
| Scope creep into BillingManagerImpl season-pass authority | Out of scope (full-subsystem option); engagement use cases only. |
| Double-advance of the baseline across two callers | Single owner (`DailyStepManager` under #120 mutex) persists; `HomeViewModel` read-only (§3.5, §4.C). |
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
