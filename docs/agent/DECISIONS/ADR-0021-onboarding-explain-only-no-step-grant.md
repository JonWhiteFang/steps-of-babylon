# ADR-0021: First-launch onboarding is explain-only — no Step grant; completion flag is device-local

**Status:** Accepted (2026-06-12)

## Context

The Gate-C work item (#24 / V1X-22) adds a first-launch tutorial so a brand-new player understands the
walk → spend → battle loop. Two design questions had to be settled before coding, both with
project-invariant implications:

1. **The "first taste" problem.** A new player lands with **0 Steps**, so they can be *told* about the
   spend step but cannot *do* it until they walk. The original V1X-22 scope proposed a **"100 free Steps
   welcome bonus"** to unlock the first Workshop upgrade immediately. That directly conflicts with a
   hard, load-bearing invariant repeated across `CONSTRAINTS.md` / `START_HERE.md` / `docs/product.md`:
   *Steps can **never** be generated passively in-game or purchased with real money.* This invariant is
   why the entire anti-cheat stack exists and is part of the store/privacy positioning. The #24 triage
   note explicitly flagged the welcome-bonus as an owner decision, "not something to slip in via an
   onboarding PR."

2. **Where the "has completed onboarding" flag lives.** `STATE.md` had tagged #24 as "(Gate C,
   **schema**)", implying a Room migration. But onboarding-complete is a device-local UI preference, not
   game state.

## Decision

1. **Onboarding is explain-only — it grants no currency of any kind.** The welcome-Steps bonus is
   **rejected**. The first-session call-to-action is "Go for a walk to earn your first Steps." No
   exception to the Steps-never-generated invariant is carved; the invariant stands unbroken. (A Gem or
   Cash grant — which are *not* constrained the way Steps are — was available as a non-violating
   alternative but was judged unnecessary for Gate C and out of scope.)

2. **The completion flag is device-local `SharedPreferences`** (`data/onboarding/OnboardingPreferences`,
   `@Singleton`, constructor-injected, no Hilt module — mirroring `MusicPreferences`), **not Room.** This
   **corrects the "(Gate C, schema)" tag — #24 needs no schema bump.** A device-local flag is correct
   because: (a) it must NOT sync if cloud save (#36) ever lands — otherwise a cross-device restore would
   suppress onboarding inconsistently; (b) on the current `allowBackup="false"` build a reinstall
   correctly re-shows the tutorial.

## Alternatives considered

- **A — Grant 100 free Steps (original V1X-22):** rejected; violates the hard invariant. Punching a hole
  in a load-bearing rule for a one-time bonus would weaken the anti-cheat/store positioning and require
  balance/anti-cheat tests to special-case it.
- **B — Grant Gems (not Steps) or seed the first upgrade pre-owned:** viable and invariant-safe, but adds
  product surface (a reward to balance, or a special-cased Workshop state) that Gate C doesn't need.
- **C — ADR-documented one-time Steps exception:** most faithful to original V1X-22, but deliberately
  breaches the invariant; not worth it for a first-session nicety.
- **Flag in Room (`PlayerProfile` column + migration):** rejected — wrong layer for a device-local UI
  pref, adds a migration + migration test for one boolean, and would wrongly sync under cloud save.

## Consequences

- **Positive:** the Steps-never-generated invariant remains absolute (no carve-out to defend later); no
  schema migration (less risk, no migration test); the flag correctly stays device-local.
- **Negative / tradeoffs:** a first session ends without a hands-on "spend" unless the player walks right
  then — accepted (the tutorial *teaches* the loop, which is what Gate C requires). The retention half of
  #24 (D2/D7 nudges, projected-reward estimates that would soften the empty-wallet moment) is deferred and
  pairs with telemetry #23.
- **Follow-ups:** when cloud save (#36 / V1X-12) lands, the onboarding gate must become
  `!hasCompletedOnboarding && totalStepsEarned == 0` so a progress restore doesn't re-onboard a returning
  player (noted in `plan-V1X-roadmap.md` under V1X-12). #24 stays OPEN for the deferred retention scope.

## Links
- Commit(s): branch `feat/onboarding-gate-c` (onboarding feature commits `9111ec8`..`dce73cc`).
- Spec / plan: `docs/superpowers/specs/2026-06-11-onboarding-gate-c-design.md`,
  `docs/superpowers/plans/2026-06-11-onboarding-gate-c.md`.
- Related ADRs: none directly; reinforces the Steps invariant documented in `CONSTRAINTS.md`.
