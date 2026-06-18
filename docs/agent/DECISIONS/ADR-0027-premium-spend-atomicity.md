# ADR-0027: Premium-currency spend+grant atomicity (card packs + UW unlocks)

**Status:** Accepted — 2026-06-18 (shipped on branch `fix/reliability-236-195-193`, `[Unreleased]`).
Amends/extends ADR-0020 (economy spend/claim atomicity).

## Context

The 2026-06-18 complete-app review filed #236 (`severity:major`, `data-integrity`, adversarially
confirmed 3✓/0~/0✗): the two premium-currency spend flows debited a permanent currency and granted in a
**separate** DB write, so a crash / coroutine cancellation between them permanently lost the currency
with nothing delivered and — unlike billing — no reconciliation record to recover from:

- `OpenCardPack` — `playerRepository.spendGems(...)` then a loop of `addCard`/`incrementCopyCount`. Gems
  are real-money-purchasable.
- `UnlockUltimateWeapon` — `playerRepository.spendPowerStones(...)` then `uwRepository.unlockWeapon(...)`.

ADR-0020 / #122 had already made the *deduct* guarded (gate the grant on rows-affected), closing the
TOCTOU double-spend, but the deduct and the grant remained two independent SQLite transactions. CLAUDE.md
/ CONSTRAINTS explicitly require currency spends to go through the atomic guarded-deduct pattern **inside
a `@Transaction`** — these two flows were the holdouts (Steps/milestones/battle-steps/billing already had it).

## Decision

**Extend the established `MilestoneDao.claimMilestoneAtomic` cross-DAO `@Transaction` pattern to both
premium flows.** All relevant tables (`player_profile`, `card_inventory`, `ultimate_weapon_state`) live in
the single `AppDatabase`, and Room scopes its transaction tracker to the `RoomDatabase` (not the DAO
instance), so a `@Transaction` default method on one DAO that calls a sibling `PlayerProfileDao` (passed
as a parameter) shares one SQLite transaction.

- **`CardDao.openCardPackAtomic(gemCost, cardTypeNames, playerDao): List<Boolean>?`** — guarded
  `spendGemsAtomic` first (0 ⇒ free pack, no deduct); `null` return ⇒ insufficient (no card rows
  written). Then per-name `getByType` decides insert (isNew=true) vs `incrementCopyCount` (false),
  returned in order.
- **`UltimateWeaponDao.unlockWeaponAtomic(weaponType, powerStoneCost, playerDao): Boolean`** —
  already-unlocked re-check **inside** the transaction and **before** the deduct (so a double-tap that
  both pass the use case's stale `owned` snapshot can't each pay), then guarded `spendPowerStonesAtomic`,
  then insert-or-`markUnlocked`.

**Expose via the existing repository ports, NOT by leaking DAOs into the domain layer.**
`CardRepository.openCardPackAtomic` / `UltimateWeaponRepository.unlockWeaponAtomic` are new interface
methods; the impls inject `PlayerProfileDao` (Hilt already provides it) and delegate. `ClaimMilestone`
sets a precedent of injecting DAOs straight into a use case, but #227/#228 actively flag that as debt, so
we deliberately did **not** add a new domain→data dependency. Consequence: `OpenCardPack` and
`UnlockUltimateWeapon` no longer need `PlayerRepository` at all (the deduct moved behind the
card/UW repository), so that constructor dependency was dropped from both.

**Rarity rolling stays in `OpenCardPack`** (pure + seeded `Random` — must remain unit-testable; the
`R35` empty-bucket guards depend on `pickCardType`). Only the DB write set (deduct + 3 card writes) is
made atomic; the use case passes pre-rolled type names to the DAO and zips the returned isNew flags back
into `CardResult`s.

## Alternatives rejected

- **Domain-layer transaction (sequential repository calls in the use case).** Impossible — two Kotlin
  repository calls are two SQLite transactions by construction; atomicity must live in the data layer.
- **Inject DAOs into the use cases (the `ClaimMilestone` shape).** Works, but adds a domain→data leak the
  codebase is trying to remove. The repository-port route keeps the domain clean at the cost of two
  small interface additions.
- **A persistent Gem/Power-Stone refund/reconciliation ledger (the billing approach).** Over-engineered
  for an in-app currency spend; atomicity removes the partial-failure window entirely, so there is
  nothing to reconcile.

## Consequences

- The partial-failure window for both premium flows is closed; a crash mid-pack/mid-unlock now rolls back
  the deduct too.
- New fragile-zone surface (see STATE.md): the two atomic methods are the authoritative spend+grant paths;
  the use cases' cheap pre-checks (`gems < cost`, `owned…isUnlocked`, `powerStones < cost`) stay as
  fast-paths but are NOT the guard — the guarded deduct's `null`/`false` return is.
- Guarded by `PremiumSpendDaoTest` (real-Room: deduct+grant atomicity, rollback-on-insufficient, free
  pack, existing-row flip, no-double-deduct-on-re-unlock) + atomic-path call-count assertions in
  `OpenCardPackTest` / `UnlockUltimateWeaponTest`. Fakes gained a `linkedPlayer` wallet seam mirroring
  `FakeMilestoneDao`. No schema change (only new `@Transaction` default methods). 1081 → 1093 JVM tests.
