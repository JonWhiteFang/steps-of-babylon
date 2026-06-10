# Constraints & Invariants

## Architecture invariants
- Clean Architecture: `presentation → domain ← data`. No shortcuts.
- `domain/` has zero Android imports — pure Kotlin only.
- Data layer implements domain repository interfaces via `@Inject constructor`.
- Presentation depends on domain, never on data directly.
- Hilt modules in `di/` wire data implementations to domain interfaces.
- Use cases are plain Kotlin classes — no Hilt annotations.

## Game design invariants
- Steps are earned ONLY from real-world walking/activity. Never generated in-game. Never purchasable.
- Room database is the single source of truth for all game state.
- Upgrade cost formula: `baseCost * (scaling ^ level)` — no exceptions.
- Loadout caps: 3 Ultimate Weapons, 3 Cards.
- Cash resets each round. Steps/Gems/Power Stones are permanent.
- Step Overdrive: once per round, 60-second duration.

## Anti-cheat rules
- Rate limit: 200 steps/min maximum (250 burst for running).
- Step velocity analysis: detects shakers (constant rate) and spoofers (instant jumps). Penalty multiplier 0.5×/0.0×.
- Daily ceiling: 50,000 steps/day.
- Health Connect cross-validation: graduated response (4 offense levels: escrow → faster discard → cap at HC → cap minus 10%).
- Activity minute validation: rejects micro-sessions (<2min), truncates extreme (>4hr), caps at 5 activity types/day.
- Per-minute overlap deduction: sensor steps ≥50/min → credit only sensor steps, not activity minutes.
- Battle Steps cap: 2,000/day, tracked on `DailyStepRecordEntity.battleStepsEarned` and enforced by `AwardBattleSteps`. Separate from the 50k walking ceiling — never additive. Flat per-enemy-type only; NOT multiplied by Fortune overdrive, Cash Bonus upgrade, or Golden Ziggurat UW. See ADR-0003.
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
- Never generate Steps passively or allow Step purchase with real money.
- Never use kapt — always KSP.
- Never skip reading the relevant plan file before implementing a feature.
