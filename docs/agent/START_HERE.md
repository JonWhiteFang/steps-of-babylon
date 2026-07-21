# START HERE (Agent Contract)

## What this project is
- Steps of Babylon: Android idle tower defense where real-world walking drives all progression.
- Target user: Mobile gamers who want fitness motivation through gameplay.
- Primary goal: v1.0 release on Google Play Store.

## Non-negotiable constraints
- Steps can NEVER be generated passively in-game or purchased with real money. **One** bounded exception
  to passive-generation: the battle-step reward (flat per-kill, 2,000/day cap — `AwardBattleSteps.DAILY_BATTLE_STEP_CAP`,
  ADR-0003), which is active play. Purchase-with-money has **no** exception.
- Domain layer (`domain/`) must have zero Android imports — pure Kotlin only.
- Room database is the single source of truth for all game state.
- Anti-cheat: 200 steps/min rate limit, 50,000 steps/day ceiling, Health Connect cross-validation.
- All monetization is cosmetic/convenience only.
- No multiplayer, no server backend for v1.0.

## How to work in this repo
- Build: `./run-gradle.sh assembleDebug` (use run-gradle.sh in non-TTY, not ./gradlew)
- Test: `./run-gradle.sh test`
- Lint: `./run-gradle.sh lint`
- All deps in `gradle/libs.versions.toml` — never hardcode versions.
- Annotation processing uses KSP (not kapt).
- Check the relevant plan file in `docs/plans/` before implementing any feature.
- Every design spec, implementation plan, and final implementation passes the **Codex Review Gate**
  before the next stage (see CLAUDE.md → Agent protocol; runs via the codex MCP server, ADR-0043).

## Where project memory lives
- STATE: docs/agent/STATE.md
- Constraints: docs/agent/CONSTRAINTS.md
- Run history: docs/agent/RUN_LOG.md
- Decisions: docs/agent/DECISIONS/
