# Implementation Plan — #421 (C1): Style Bible + BattlePalette

**Date:** 2026-07-09 · **Issue:** #421 (child of #391) · **Spec:**
`docs/superpowers/specs/2026-07-09-asset-pipeline-free-lane-design.md` · **PR:** own PR (bible-first).

## Objective

Create the missing **single source of truth** for the battle art palette, in two forms: a human doc
(`style-bible.md`) and a code anchor (`BattlePalette.kt`). **Zero visual change** — every ARGB value stays
byte-identical; this PR only *names and centralises* what's currently anonymous. Repointing consumers is
Children 2–4. Optionally repoint **`BiomeTheme` only** as proof-of-use (decision point below).

## Guardrails / invariants (must not break)

- **No behaviour change.** `BiomeThemeTest.kt` (all 5 biome palettes, ziggurat colors, particles) and
  `ContrastTest` must stay green untouched. If `BiomeTheme` is repointed (option B), the constants MUST equal
  the current literals exactly — `BiomeThemeTest` is the proof.
- **Functional vs art palette (the key review finding).** Do NOT fold UI-signal colors into the art palette:
  HP-bar ratio thresholds, armor cyan stroke, ziggurat range-circle alphas (`0x22FFFFFF`/`0x44FFFFFF`),
  origin gold. The bible documents these in a *separate "functional palette" section*; `BattlePalette` holds
  only the **art** colors (biome sky/ground/ziggurat ramps, enemy base colors).
- **Layer respect.** `BattlePalette` is presentation-layer (`presentation/battle/biome/`); it may use
  `android.graphics.Color`. It must NOT be referenced from `domain/` (DomainPurityTest).
- **No lock-region / game-logic edits.** This PR touches color constants + a doc + a test only.

## Steps

### 1. Author `docs/steering/style-bible.md`
- **Brand anchor:** reference the five `Color.kt` tokens verbatim (Gold `#D4A843`, LapisLazuli `#26619C`,
  SandStone `#C2B280`, DeepBronze `#6B3A2A`, Ivory `#FFF8E7`) as the identity anchors.
- **Per-biome section (5):** for each biome (Hanging Gardens, Burning Sands, Frozen Ziggurats, Underworld of
  Kur, Celestial Gate) capture the current sky-top/bottom, ground, 5-stop ziggurat ramp, enemy tint, particle
  color/drift/count *and the mood rationale* (why these hues — GDD §12.2 Mesopotamian direction + §6.3 biome
  progression). These are the values in `BiomeTheme.kt` today — document, don't change.
- **Enemy shape/color vocabulary:** the 6-enemy silhouette language (circle BASIC/BOSS/SCATTER, triangle
  FAST, square TANK, diamond RANGED) + current base colors, framed as "cohesive enemy visual language."
- **Ziggurat:** 5-layer geometry + `DEFAULT_COLORS` ramp (bronze→gold), origin marker.
- **Functional palette (SEPARATE section):** HP-bar thresholds (green >0.6 / yellow >0.3 / red), armor
  stroke, range-circle fill/stroke alphas — flagged as *UI signal, not free art; excluded from BattlePalette
  and allowlisted in the guard*.
- **Line weights / motifs:** stroke widths in use, cuneiform/lapis/Babylonian-geometric guidance from GDD.
- Cross-link GDD §6.3 + §12.2 and `presentation/ui/theme/Color.kt`.

### 2. Create `presentation/battle/biome/BattlePalette.kt`
- An `object BattlePalette` exposing the **art** colors as named `const`/`val Int` (ARGB), grouped:
  - Per-biome nested groups (e.g. `HangingGardens.skyTop`, `.ziggurat` = `List<Int>`, `.enemyTint`,
    `.particle`, `.particleDriftX/Y`, `.particleCount`) mirroring `BiomeTheme`'s current values exactly.
  - `Enemy` group: the 6 `BASE_COLORS` values by `EnemyType`.
  - `Ziggurat.defaultLayers` = the `DEFAULT_COLORS` list.
- **Excludes** functional colors (they stay inline where they are, documented in the bible).
- Values copied byte-for-byte from the current sources (verified against `BiomeTheme.kt`/`EnemyEntity.kt`/
  `ZigguratEntity.kt` — see spec §0).

### 3. Decision point — repoint `BiomeTheme` now, or defer to C3?
- **Option A (leaner):** ship `BattlePalette` + bible only; no consumer repointed. Smallest diff; C3 repoints.
- **Option B (proof-of-use):** repoint `BiomeTheme.forBiome` to read `BattlePalette`, leaving enemy/ziggurat
  for C2/C3. Proves the anchor is wired; `BiomeThemeTest` proves value-equality.
- **Recommendation: Option B for `BiomeTheme` only** — a source-of-truth with zero call sites is easy to
  drift; one wired consumer + the existing `BiomeThemeTest` gives immediate regression protection. Confirm at
  implementation.

### 4. Test — pin the anchor
- `presentation/battle/biome/BattlePaletteTest.kt` (JVM): assert `BattlePalette` exposes the expected
  constants/lists (value-pins), and — if Option B — that `BiomeTheme.forBiome(x)` returns palette-sourced
  values for all 5 biomes. (Complements, doesn't duplicate, `BiomeThemeTest`.)
- The `BattleArtPaletteTest` **guard** (#426) is a *separate* deliverable — plan-time call whether it rides
  C1 or C3. Leaning C3 (guard is most meaningful once consumers are repointed, so it has a real allowlist to
  validate). Note this in the PR.

### 5. Doc sync (PR Task-List Convention — BEFORE the STATE/RUN_LOG step)
- `docs/steering/source-files.md`: add `style-bible.md` (steering ref) + `BattlePalette.kt` + its test;
  update the `BiomeTheme.kt` line if repointed (Option B).
- `CLAUDE.md`: bump the headline test count by the tests added; if the guard does NOT ride C1, no Testing/
  fragile-zone edit yet (add a one-line fragile-zone note that `BattlePalette` is the single art-color source).
- `CHANGELOG.md`: add a `[Unreleased]` entry.
- No schema/tech/lib/README change.

### 6. STATE.md + RUN_LOG.md
- STATE: note C1 shipped, #391 free-lane in progress, C2–C4 next.
- RUN_LOG: append the session entry.

### 7. Build + commit
- `./run-gradle.sh :app:testDebugUnitTest :app:detekt` + `./lint-kotlin.sh`; `:app:assembleDebug` for safety.
- Branch off `main`, commit, open PR referencing #421 + #391.

## Verification

- `BiomeThemeTest` + `ContrastTest` green (no value drift).
- New `BattlePaletteTest` green.
- detekt/ktlint/assembleDebug green.
- Diff review: confirm **no** ARGB value changed vs the pre-PR sources (grep the old literals still resolve to
  identical values through the palette).

## Out of scope (this PR)

- Repointing `EnemyEntity`/`ZigguratEntity` (C2/C3).
- Particle config vocabulary (C4).
- The `BattleArtPaletteTest` guard, unless the impl chooses to ride it on C1.
- Any visual re-tune — if the bible surfaces a wanted change, it's a *deliberate* follow-up in C2/C3, noted
  + eyeballed on-device, never smuggled into this consolidation PR.
