# Constraints & Invariants

## Architecture invariants
- Clean Architecture: `presentation → domain ← data`. No shortcuts.
- `domain/` has zero Android imports — pure Kotlin only.
- Data layer implements domain repository interfaces via `@Inject constructor`.
- Presentation depends on domain, never on data directly.
- Hilt modules in `di/` wire data implementations to domain interfaces.
- Use cases are plain Kotlin classes — no Hilt annotations.

## Game design invariants
- Steps are earned ONLY from real-world walking/activity. Never generated passively in-game. Never purchasable.
  **Sole exception (passive-generation only):** the battle-step reward — flat per enemy kill, capped at
  2,000/day (`AwardBattleSteps.DAILY_BATTLE_STEP_CAP`), separate from the 50k walking ceiling, never
  multiplied by any in-round upgrade (ADR-0003). This is active play, not passive generation, and there is
  **no** exception to "never purchasable with money."
- Room database is the single source of truth for all game state.
- Upgrade cost formula: `baseCost * (scaling ^ level)` — no exceptions.
- Loadout caps: 3 Ultimate Weapons, 3 Cards.
- Cash resets each round. Steps/Gems/Power Stones are permanent.
- Rapid Fire (Workshop upgrade, R4-03): periodic mid-wave attack-speed burst. (Replaced the removed
  Step Overdrive mechanic — there is no once-per-round mid-round Step burn in v1.0.)

## Anti-cheat rules
- Rate limit: 200 steps/min maximum (250 burst for running).
- Step velocity analysis: detects shakers (constant rate) and spoofers (instant jumps). Penalty multiplier 0.5×/0.0×.
- Daily ceiling: 50,000 steps/day.
- Health Connect cross-validation: graduated response (4 offense levels: escrow → faster discard → cap at HC → cap minus 10%).
- Activity minute validation: rejects micro-sessions (<2min), truncates extreme (>4hr), caps at 5 activity types/day.
- Per-minute overlap deduction: an activity minute is credited only when sensor steps are **<50/min** that minute; at ≥50/min the sensor already captured the motion, so the activity minute is skipped (`ActivityMinuteConverter`, `MAX_SENSOR_STEPS_PER_MIN = 50`).
- Battle Steps cap: 2,000/day (`AwardBattleSteps.DAILY_BATTLE_STEP_CAP`), tracked on `DailyStepRecordEntity.battleStepsEarned`. Separate from the 50k walking ceiling — never additive. Flat per-enemy-type only; NOT multiplied by any in-round source (Cash Bonus upgrade or the Golden Ziggurat UW's `fortuneMultiplier`, which affects Cash only). See ADR-0003. (The original "no Fortune-overdrive multiplier" wording is moot since R4-01 removed Step Overdrive.)
- Step counting must work reliably when app is backgrounded or killed.

## Security
- SQLCipher encryption for Room database.
- Android Keystore for key management.
- R8 obfuscation for release builds.
- Network security config: cleartext blocked.

## Build & tooling
- All dependency versions in `gradle/libs.versions.toml` — never hardcode.
- KSP for annotation processing (not kapt).
- Use `./run-gradle.sh` in non-TTY environments (CI, etc.).
- Room schema exports to `app/schemas/` — commit these files.

## "Never do" list
- Never add Android imports to `domain/` layer.
- Never hardcode dependency versions in build.gradle.kts.
- Never generate Steps passively or allow Step purchase with real money. (The existing battle-step reward
  is the **one** sanctioned Step-credit path outside walking — bounded at 2,000/day via
  `AwardBattleSteps.DAILY_BATTLE_STEP_CAP`, ADR-0003. Do **not** treat it as a violation to remove, and do
  **not** add any *new* in-game Step-credit path — see the `ai-3` audit finding on machine-enforcing this.)
- Never use kapt — always KSP.
- Never skip reading the relevant plan file before implementing a feature.
