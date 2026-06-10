# ADR-0020: Economy spend/claim atomicity — guarded deduct gates the grant

**Status:** Accepted
**Date:** 2026-06-10
**Supersedes:** None
**Superseded by:** None

## Context

The 2026-06-10 reachability-aware audit (report findings #4, #5, #9, #10; GitHub issue
[#122](../../../issues/122)) found a recurring shape across the economy layer: a guarded atomic DAO
existed (the V1X-10 `spendGemsAtomic` / `spendPowerStonesAtomic` and the established
`WorkshopDao.purchaseUpgradeAtomic` / `MilestoneDao.claimMilestoneAtomic` pattern), **but the
caller ignored its result**, so the grant was decoupled from the deduct actually succeeding.

Four concrete defects:

1. **#4 — `StartResearch`** was the only Step-spend NOT migrated to the guarded pattern. It checked
   a stale `wallet.stepBalance` snapshot, then called the unguarded `spendSteps` → `adjustStepBalance`
   (`MAX(0, balance - cost)`), which silently clamps an under-payment to 0. A concurrent Step-spend
   on another screen could drain the balance between the snapshot and the spend → research started
   for less than its cost (or free).
2. **#5 — `spendGems` / `spendPowerStones`** in `PlayerRepositoryImpl` called the guarded atomic DAOs
   but discarded the `Int` rows-affected (interface returned `Unit`). Every consuming use case
   (`UnlockUltimateWeapon`, `UpgradeUltimateWeapon`, `UnlockLabSlot`, `OpenCardPack`, `RushResearch`,
   `StoreViewModel.purchaseCosmetic`) granted the item unconditionally. On a stale snapshot the
   guarded deduct no-ops while the grant still fires → free item. The UW screen (no `_processing`
   guard) is the most reachable double-tap vector.
3. **#9 — `ClaimSupplyDrop`** credited the reward and THEN called an unconditional
   `markClaimed WHERE id` (no `AND claimed = 0`). A double-tap re-entered with the same
   `claimed == false` snapshot and credited twice.
4. **#10 — `MissionsViewModel.claimMission`** had the identical shape (credit-then-unconditional-mark).

## Decision

**Make the affordability-gated currency spends return success, and gate every grant/claim on it.**

1. **Spend returns `Boolean`.** `PlayerRepository.spendGems` / `spendPowerStones` now return
   `rowsAffected > 0` (propagated from the existing guarded atomic DAOs). Each use case grants the
   item only when the deduct returns `true`, otherwise returns its `Insufficient*` / `false` variant.

2. **Steps keep the clamp; add a guarded variant.** `spendSteps` stays `Unit` + `MAX(0,…)` clamp —
   it is used by `StepCrossValidator`'s **escrow clawback**, which intentionally deducts a disputed
   `excess` that may exceed the balance (the clamp is the desired behaviour there). A new
   `spendStepsIfSufficient(amount): Boolean` (backed by the existing
   `PlayerProfileDao.adjustStepBalanceIfSufficient`) is the affordability-gated path; `StartResearch`
   uses it.

3. **Claims become idempotent at the DAO.** `WalkingEncounterDao.markClaimed` and
   `DailyMissionDao.markClaimed` gain `AND claimed = 0` and return `Int` rows-affected.
   `WalkingEncounterRepository.claimDrop` returns `Boolean`. `ClaimSupplyDrop` is reordered to
   **mark-first** (credit only when the guarded claim returns true). The mission claim is extracted
   into a new `ClaimMission` use case (mirrors `ClaimMilestone`) so the guard is JVM-testable outside
   `MissionsViewModel`'s `while(true)` ticker.

## Consequences

- **No schema change / no migration.** The `claimed` columns already exist; adding `AND claimed = 0`
  to existing UPDATEs and changing Kotlin return types does not alter the Room schema hash, so the
  schema-drift CI gate stays green.
- **Blast radius is the `PlayerRepository` / `WalkingEncounterRepository` interface return types** —
  rippled to `PlayerRepositoryImpl`, `FakePlayerRepository`, `FakeWalkingEncounterRepository`,
  `FakeDailyMissionDao` (all now mirror the guarded semantics: no mutation + `false`/`0` on
  insufficient/already-claimed). `CurrencyGuardTest` was updated from the old "clamp to 0" assertions
  to the new guarded contract.
- **Claim-exactly-once, not a single transaction.** Room can't share one statement between an
  `@Query` UPDATE and the cross-table wallet writes, so the credit is a follow-up call. Mark-first
  ordering still guarantees credit-exactly-once; the residual partial-failure window (crash between
  mark and credit) drops a reward rather than duplicating it — the safe direction for the
  player-economy invariant.
- **Tests:** +17 JVM (871 → 888), including a real-SQLite `GuardedClaimDaoTest` validating the
  guarded queries, per-use-case "stale snapshot does not grant for free" tests, and double-claim
  tests for supply drops + missions. The `StartResearch` TOCTOU test was confirmed RED against the
  reverted gate.
