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
- **`RushResearch` gem-rush cost** (`RushResearch.calculateRushCost` reads raw `now`; a forward jump
  lowers the remaining-time fraction → cheaper rush, floored at 50 gems). The auto-complete path
  (`CheckResearchCompletion`, the headline exploit) IS guarded by trusted-now; the rush *cost* is a
  secondary surface left raw this wave — documented-accepted in the ADR. (`CompleteResearch` is dead
  production code — no caller — so its guard is contract-only; the real manual path is `RushResearch` +
  the season-pass `freeRush` which calls `completeResearch` directly, both out of scope here.)
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
/** Persisted tamper baseline (FOUR Long slots). */
data class TimeBaseline(
    val lastElapsedRealtime: Long,   // SystemClock.elapsedRealtime() at last checkpoint (since-boot)
    val lastWallClock: Long,         // System.currentTimeMillis() at last checkpoint (raw — to compute the next wallDelta)
    val maxWallClockSeen: Long,      // highest wall-clock ever observed — the reboot-durable rollback floor
    val trustedWallClock: Long,      // capped-accrual anchor: only ever advances by min(wallDelta, elapsedDelta).
                                     // The trusted "now" — a forward jump's excess is NEVER folded in (see below).
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
     * Update the baseline from a fresh reading and classify the time axis. The returned
     * `newBaseline.trustedWallClock` IS the trusted "now" callers use to gate research.
     * - baseline == null (first run): Trusted; seed maxWallClockSeen = trustedWallClock = reading.wallClock.
     * - reading.wallClock < baseline.maxWallClockSeen: Rollback (backward jump — reboot-durable).
     * - else: Trusted.
     *
     * In ALL branches the new baseline advances lastWallClock/lastElapsedRealtime to "now",
     * `maxWallClockSeen = max(prev, reading.wallClock)`, and **`trustedWallClock` advances by the CAPPED
     * delta**: `newTrusted = prev.trustedWallClock + cappedDelta`, where
     * `cappedDelta = if (elapsedDelta < 0) wallDelta.coerceAtLeast(0)   // reboot: no monotonic cap (accepted §2 gap)
     *                else min(wallDelta, elapsedDelta).coerceAtLeast(0)` and
     * `wallDelta = reading.wallClock - prev.lastWallClock`, `elapsedDelta = reading.elapsedRealtime - prev.lastElapsedRealtime`.
     *
     * **Why a persisted capped-accrual anchor (replaces the earlier `lastWallClock + trustedElapsedSince`
     * derivation, which was race-defeatable):** `trustedWallClock` only ever moves by the capped delta, so
     * an in-session forward jump's EXCESS is never absorbed — independent of ordering between the single
     * owner (which persists) and a read-only consumer (which just reads `baseline.trustedWallClock`). The
     * old derivation re-read `lastWallClock`; if the owner advanced it to the jumped value first, the
     * consumer's `min(wallDelta≈0, …)` collapsed to 0 and `trustedNow ≈ jumped wall-clock` → research
     * completed early (the critical bug the plan-review caught). With the anchor, consumers read the
     * already-capped value and never see the excess.
     *
     * Reboot caveat (HONEST, accepted §2 gap): on `elapsedDelta < 0` the device rebooted, so cappedDelta
     * falls back to the full wallDelta — a forward jump spanning a reboot advances trustedWallClock fully
     * → research completes. The Rollback floor does NOT guard the forward direction (it catches only
     * backward jumps). Defending this needs the rejected boot-reconciliation subsystem; out of scope.
     */
    fun evaluate(baseline: TimeBaseline?, reading: TimeReading): TimeVerdict
    // Consumers gate research on `evaluate(...).newBaseline.trustedWallClock` (read-only consumers do NOT
    // persist; the single owner persists). No separate `trustedElapsedSince` function — the anchor is the API.
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
- **DataDeletionManager** must clear these keys (the file is already wiped on data deletion — verify the
  new keys are covered by the existing `anti_cheat_prefs` clear; they ride it, no `PREFS_NAMES` change).
- **`TimeBaselineSource` test seam (committed, not optional — plan-review fix).** The Home/Labs VM tests
  are plain-JVM JUnit Jupiter (no Robolectric) and CANNOT construct the real `Context`-backed
  `AntiCheatPreferences`. So define a tiny pure-domain interface
  `TimeBaselineSource { fun readTimeBaseline(): TimeBaseline?; fun currentTimeReading(): TimeReading }`
  in `domain/time/` (Android-free — `DomainPurityTest` stays green); `AntiCheatPreferences` implements it;
  the VMs inject `TimeBaselineSource` (Hilt binds it to `AntiCheatPreferences`), and a
  `FakeTimeBaselineSource` backs the VM tests. `DailyStepManager` (which already holds the concrete
  `AntiCheatPreferences` and also persists) can use the concrete type directly. NOTE the 4-slot
  `TimeBaseline` (now incl. `trustedWallClock`) and `TimeReading` are the round-trip shapes the store
  reads/writes.

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
- **In-session forward-jump guard (research) — order-independent capped-accrual anchor (review-fix for
  the CRITICAL anchoring flaw).** The completion gate uses *trusted now* = the persisted
  **`trustedWallClock`** anchor, obtained as `TimeIntegrity.evaluate(readBaseline(), currentReading()).newBaseline.trustedWallClock`.
  Because `trustedWallClock` advances ONLY by the capped delta (§4.A), a forward jump's excess is never
  folded in — **regardless of whether the single owner persisted first or a read-only consumer reads
  first.** This replaces the earlier `lastWallClock + trustedElapsedSince` derivation, which the
  plan-review found race-defeatable: if the owner advanced `lastWallClock` to the jumped value before a
  consumer read it, the consumer's delta collapsed to ~0 and `trustedNow ≈ jumped clock` → research
  completed early. With the anchor: the single owner (DailyStepManager) persists the advanced
  `trustedWallClock`; read-only consumers (Home/Labs) call `evaluate` purely to DERIVE the trusted-now
  from the current persisted anchor (they do NOT `writeTimeBaseline`), and the value they get is already
  capped no matter the interleaving. (Reboot-spanning forward jump still accepted — §2; the capped delta
  falls back to the full wall delta on a reboot, by design.) The research use cases are unchanged — they
  already take `now`; the caller just passes the anchor as `now`.

## 5. Component design (#258)

**This is a DOCUMENTATION-ONLY change — no code/config change.** The bracketed "add a general
`fallbackToDestructiveMigration`" alternative is **dropped**: that would be a behavioral DI change
(`DatabaseModule.kt:27` uses only `…OnDowngrade`) and would re-introduce exactly the bare destructive
fallback that `database-schema.md:281` correctly asserts is "not used anywhere." The floor-safety fact
is **verifiable, not uncertain** — confirmed against the dated CHANGELOG internal-track rollout history:
schema reached v9 (Phase C.5 PR 1, 2026-05-11) BEFORE the first internal-track distribution (Plan 31
walk-through, 2026-05-13/14; v2/v3 rolled out 2026-05-15; "AAB v9 verified on internal track" 2026-05-23).
The pre-v9 dated entries are all pre-distribution development, so the first build ever distributed to a
tester was at schema v9 (≥ v7) — no pre-v7 install exists to upgrade. The plan verifies against those
specific dated entries (NOT a keyword grep). A STOP condition remains only for the genuine refutation
(any CHANGELOG entry showing a build at schema < v7 distributed to a tester); if that somehow held, it's
a separate behavioral decision tracked apart from this doc gap-fill, NOT folded in here.

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
