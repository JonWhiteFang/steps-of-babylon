# ADR-0036: Time-axis anti-cheat — clock-tamper resistance for time-gated mechanics (#211)

Status: Accepted (2026-06-22)

## Context
- Time-gated mechanics trusted the **unguarded device wall-clock** (`System.currentTimeMillis()`):
  Labs research auto-completion and the daily-login streak read raw `now`, and a player can move it
  freely via OS Settings → Date & time (the cheap, ubiquitous client-side exploit). Two concrete
  cheats followed (#211, TIME-1): (a) jump the clock **backward** to re-collect a daily-login streak/
  gem/season-bonus for an already-claimed date; (b) jump the clock **forward** to instantly finish
  in-flight research without waiting the real time-gate. The step-integrity stack guarded the *steps*
  axis but nothing guarded the *time* axis.
- v1.0 is offline-first with **no server backend** (`CONSTRAINTS.md`), so there is no authoritative
  remote clock to fall back to — any defense must be on-device and survive process death / reboot.

## Decision
- **Pure-domain decision core `domain/time/TimeIntegrity.kt`.** A `TimeIntegrity.evaluate(baseline,
  reading)` reasons over a persisted 4-slot `TimeBaseline(lastElapsedRealtime, lastWallClock,
  maxWallClockSeen, trustedWallClock)` plus a fresh `TimeReading(elapsedRealtime, wallClock)` and
  returns a `TimeVerdict.Trusted | Rollback` that **always carries the advanced baseline**. No Android
  imports (the Android clock reads live in the data layer), so it is fully JVM-testable
  (`DomainPurityTest` green).
- **Monotonic anti-rollback guard + reboot-durable max-wall-clock floor.** `maxWallClockSeen` is the
  highest wall-clock ever observed; `reading.wallClock < maxWallClockSeen` ⇒ `Rollback`. Because the
  floor is persisted, it survives reboot (a reboot doesn't legitimately move the wall clock backward),
  so the backward-rollback exploit is closed reboot-durably.
- **Order-independent `trustedWallClock` capped-accrual anchor.** The trusted "now" only ever advances
  by `min(wallDelta, elapsedDelta).coerceAtLeast(0)` — so an **in-session forward jump's excess is
  never folded in**. It is a persisted *anchor* (not a re-derivation): a read-only consumer
  re-evaluating against an owner-advanced baseline still reads the capped value and never re-accepts
  the jump. (This replaced an earlier `lastWallClock + trustedElapsedSince` derivation the plan-review
  found race-defeatable.) Reboot (`elapsedDelta < 0`) has no monotonic cap to apply, so the capped
  delta falls back to the full `wallDelta` by design — see the accepted boundary below.
- **Baseline stored in `AntiCheatPreferences`** (the existing `anti_cheat_prefs` plaintext
  MODE_PRIVATE SharedPreferences) behind the pure-domain `TimeBaselineSource` read seam
  (`readTimeBaseline()` / `currentTimeReading()`) + a concrete `writeTimeBaseline()`. No new Room
  schema or migration. Wiped by `DataDeletionManager`'s existing `anti_cheat_prefs` clear.
- **Single baseline owner = `DailyStepManager`.** It evaluates + **persists** the advanced baseline
  under its existing #120 non-reentrant mutex (`writeTimeBaseline` uses `apply()` — async, non-throwing
  — so it can't abort the credit). `HomeViewModel` / `LabsViewModel` are **read-only consumers**: they
  evaluate to derive the trusted-now / verdict but never persist.
- **Two closed exploits.**
  - *Backward rollback:* `TrackDailyLogin.checkAndAward(isRollback = true)` refuses **all** credit for
    the tampered date and writes nothing — no streak/gem/season-bonus advance, no `DailyLogin` row, no
    `gemsClaimed` latch.
  - *In-session forward jump on research:* `CheckResearchCompletion(now = trustedWallClock)` gates on
    the capped trusted-now, so a forward jump can't instantly auto-complete in-flight research.

## Scope boundary (accepted non-goals — documented honestly)
The adversary in scope is a player using **OS Settings → Date & time**. Explicitly **out of scope /
accepted residual risk** for this wave:
- **Rooted / file-editing adversary** — can edit the plaintext baseline directly. Same trust tier as
  the entire existing step-anti-cheat stack (also plaintext MODE_PRIVATE), and consistent with the
  standing posture that root is outside the Android threat model and client-side IAP/ad-reward
  integrity carries the same residual-risk acceptance.
- **Reboot-spanning forward jump on research** — `elapsedRealtime` resets across process death, so no
  in-session delta survives to cap the wall jump; the capped delta falls back to the full wall delta
  by design. The `Rollback` floor guards only the *backward* direction.
- **`RushResearch` gem rush-cost** — reads raw `now`, so a forward jump lowers the remaining-time
  fraction and makes the rush cheaper (floored at 50 gems). The headline auto-complete exploit IS
  guarded; the rush cost is a secondary surface left raw this wave.
- **`CompleteResearch`** — dead production code (no caller); its trusted-now contract is contract-only.
- **`freeRush`** (season-pass direct completion) accepted; `BillingManagerImpl` season-pass-expiry
  authority is unchanged (a full-subsystem-scope concern).

## Alternatives considered
- **Accept-and-document (no guard):** rejected — #211 is a `severity:major` data-integrity/game-design
  defect with two reachability-confirmed exploits; the cheap OS-Settings cheat warrants a real guard.
- **Full time-integrity subsystem** (boot-count / uptime reconciliation, multi-source clock voting,
  encrypted baseline): rejected for this wave — large, and the marginal coverage (reboot-spanning
  forward jumps, rooted edits) is exactly the residual risk we accept above; disproportionate for an
  offline single-player game. The pure-core + capped-accrual anchor is the minimal guard that closes
  the two headline exploits while leaving a clean extension seam.
- **Persist a re-derivable `lastWallClock + trustedElapsedSince` instead of an anchor:** rejected — the
  plan-review showed it is race-defeatable when a read-only consumer re-derives against an owner-moved
  baseline; the persisted capped-accrual anchor is order-independent.

## Consequences
- Positive: the two headline clock-tamper exploits are closed; the decision logic is a pure,
  exhaustively unit-tested core (`TimeIntegrityTest`), reusable without Android; no Room schema/
  migration churn (baseline rides existing SharedPreferences); single-owner persistence keeps the
  write path inside the established #120 mutex.
- Negative / tradeoffs: a documented residual-risk boundary a future reader must understand (rooted
  edit, reboot-spanning forward jump, rush-cost). One new persistence concern (4 SharedPreferences
  longs) and a read seam the VMs depend on.
- Follow-ups: the rush-cost surface and a reboot-spanning forward-jump defense are deferred; revisit if
  abuse is observed or if a server backend ever lands (an authoritative clock would subsume this).

## Links
- Issue: #211 (TIME-1). Schema-doc migration-floor gap-fill #258 (same PR; doc-only, committed earlier
  in this branch).
- Related ADRs: ADR-0020 / ADR-0027 (economy spend/claim atomicity — the invariant the daily-login
  refuse-credit path sits beside), ADR-0034 (domain→data dependency rule — the seam this honors),
  ADR-0003 / ADR-0005 (the same client-side residual-risk-acceptance posture).
