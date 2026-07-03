# ADR-0038: Enforcing the concurrency/economy review invariants (#371 ai-3, #372 ai-2)

Status: Accepted (2026-07-02)

## Context
- Two of the codebase's most safety-critical invariants were **prose-only** — an AI agent (or a human)
  could violate either with a fully green `testDebugUnitTest` + detekt + ktlint + lint:
  1. **"Steps are never generated in-game"** (the #1 design rule; ADR-0003). The only sanctioned credit is
     the bounded battle-step reward. Nothing stopped a new card/UW/mission reward path from crediting Steps
     off in-round state.
  2. **The battle-engine lock discipline** — the acyclic `entitiesLock → effectsLock` order and
     "collaborators hold no monitor of their own" (`CLAUDE.md` Battle Renderer note). These are the exact
     bug classes the project was burned by (#118 / #191 CME class).
- The 2026-07-02 tooling-gap audit (`docs/reviews/tooling-gap-assessment.md`) filed these as `ai-3` (#371)
  and `ai-2` (#372): the highest-value AI-safety additions, because they are the rules an agent is most
  likely to trip and the guardrails would not currently catch it.

## Decision
Enforce in three layers, strongest-first:

1. **Build-gated source-scan tests** (JVM lane, dependency-free, modeled on `DomainPurityTest` —
   comment-stripped, qualified-call scans, working dir = `app/`):
   - `architecture/StepCreditAllowlistTest` (#371) pins the **full `currentStepBalance` write surface** of
     `PlayerProfileDao`, not just `adjustStepBalance`: `.addSteps(` callers == {ClaimSupplyDrop,
     StepCrossValidator, DailyStepManager}; positive `.adjustStepBalance(` == {PlayerRepositoryImpl,
     DailyStepDao}; `.updateStepBalance(` has zero callers; `PlayerProfileEntity(` construction confined to
     PlayerRepositoryImpl; a DAO `currentStepBalance =` write-count pin (==3); plus a **baked negative
     fixture** proving each matcher goes red on a rogue site (guards against the #228 "guard that never
     fails" rot).
   - `architecture/BattleEngineLockScanTest` (#372) asserts the engine collaborators
     {UWController, CombatResolver, BuffTickers, BattleRenderer} declare no monitor of their own
     (`synchronized(`/`ReentrantLock`/`= Any()`/`= Object()`), comment-stripped, **excluding** `GameEngine`
     (the sole sanctioned `entitiesLock` owner) and `BattleHosts` (its `synchronized(entitiesLock)` is in a KDoc).
2. **Deterministic PreToolUse advisory** (`.claude/hooks/guard-sensitive-edits.sh` tier 4): nudges that the
   `concurrency-reviewer` lane is mandatory on edits under `presentation/battle/engine|effects/**`,
   `data/local/*Dao.kt`, or `data/repository/PlayerRepositoryImpl.kt`. Fires **regardless of ultracode
   state**, unlike the CLAUDE.md protocol.
3. **Protocol** (CLAUDE.md Adversarial Review Gate): the mandatory `concurrency-reviewer` review lane on the
   same surface (which includes the currency-moving repo/use-case surface where the #371 credit paths live).

## Rejected / deferred
- **detekt custom rule for nested lock acquisition.** Would build-gate the lock *order* (not just the
  collaborator-monitor case the `BattleEngineLockScanTest` fallback covers). Deferred: the repo has no detekt
  custom-rule module today (`app/build.gradle.kts` applies detekt as a bare plugin — no `detektPlugins`
  dependency), and detekt is `2.0.0-alpha.5` (unstable `RuleSetProvider` API). Authoring it needs a new
  Gradle module + dependency. Tracked as follow-up issue **#396** (carries the two "unblock when"
  conditions: a stable custom-rule API on a Kotlin-2.3.x-compatible detekt line + standing up a rule module).

## Consequences
- A new Steps-credit site (any of the three write primitives) or a collaborator that takes its own monitor
  now **fails the build**, not just review.
- The lock-*order* check (as opposed to collaborator-monitor) remains review-driven, not build-gated — the
  deferred detekt rule would close that.
- Test count: +7 JVM (StepCreditAllowlistTest 6 + BattleEngineLockScanTest 1) → 1301.
