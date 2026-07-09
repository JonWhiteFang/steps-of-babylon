# ADR-0042: Battle art palette is single-sourced in BattlePalette (#391 free lane)

**Status:** Accepted (2026-07-09) · **Issue:** #391 free / code-drawable lane (children #421–#426)

## Context
- #391 (research/backlog epic) concluded the highest-value, zero-cost, zero-licensing asset lane is
  *code-drawable* art governed by a single source of truth — "a stochastic image generator fights
  consistency, a Kotlin function does not."
- But that single source did not exist. The battle art palette was ~50 anonymous raw `0xFF…` ARGB ints
  fragmented across three uncoordinated sites with no shared vocabulary: `BiomeTheme.kt` (5 biomes × ~9
  colours), `EnemyEntity.BASE_COLORS`, and `ZigguratEntity.DEFAULT_COLORS`. Only `presentation/ui/theme/
  Color.kt` (the Compose brand palette) had design intent + guards.
- A palette with no code anchor drifts; a doc-only "style guide" rots. We needed both a code anchor and a
  human reference, plus a tripwire so the consolidation can't silently re-fragment.

## Decision
- Introduce **`presentation/battle/biome/BattlePalette.kt`** as the single source of truth for the battle
  **art** palette: per-biome `BiomeColors` (sky/ground/ziggurat-ramp/enemy-tint), `enemyBaseColors`,
  `zigguratDefaultLayers`, and a named `ParticleConfig` (`color`/`driftX`/`driftY`/`count`) ambient-emitter
  vocabulary. `BiomeTheme`/`EnemyEntity`/`ZigguratEntity`/`BackgroundRenderer` all derive from it.
- **Split ART colour from FUNCTIONAL-signal colour.** UI-signal colours (HP-bar ratio thresholds + bg,
  armor stroke, ziggurat origin gold, attack-range-circle alphas) encode *gameplay state*, not art
  direction. They stay inline at their consumption sites and are explicitly **excluded** from
  `BattlePalette` — a biome re-tune must never change what a health bar means.
- **Guard it:** `architecture/BattleArtPaletteTest` (dependency-free source-scan, `StepCreditAllowlistTest`
  idiom) fails the build on a NEW raw `0x…` ARGB literal in the three art-colour consumers not sourced from
  `BattlePalette`; a `functionalColorAllowlist` (keyed `FileName:hexLiteral`, each justified) exempts the
  functional-signal colours. Includes a negative-fixture test so the guard can't degrade to a no-op.
- **Human references:** `docs/steering/style-bible.md` (visual) + `docs/steering/tone-bible.md` (voice).
- **Behaviour-preserving:** every ARGB value is byte-identical to the literal it replaced (pinned by
  `BiomeThemeTest` + `BattlePaletteTest`); zero visual change across the whole lane.

## Alternatives considered
- **A: Doc-only style bible, no code anchor.** Rejected — no enforcement; the palette would drift and the
  bible would rot.
- **B: Fold everything (incl. functional colours) into one palette.** Rejected — conflates art with
  gameplay signal; a biome re-tune could silently change HP-bar/range semantics.
- **C: Feed the per-biome `ParticleConfig` into `EffectEngine`/`ParticlePool` (C4 original framing).**
  Rejected/descoped — `EffectEngine`/`ParticlePool` are a *generic* effect system that does not consume
  per-biome config (only `BackgroundRenderer` does). Touching the `effectsLock` fragile zone would add
  risk for zero benefit. `concurrency-reviewer` confirmed the config-only refactor is SAFE.

## Consequences
- **Positive:** one governed place for battle art colour; new art colours are named, not anonymous;
  re-fragmentation fails the build; art/functional split is explicit + guarded; two human references keep
  future art/copy coherent. Delivered as 5 small sequential-merge PRs (#427–#431), each zero-visual-change
  / docs-only. Test count 1332 → 1339.
- **Negative / tradeoffs:** the flat `particleColor`/`particleDriftX/Y`/`particleCount` accessors on
  `BiomeColors`/`BiomeTheme` are kept as delegating getters (mild redundancy) to avoid churning readers
  incl. the fragile `GameEngine`. The guard is a substring/line scan (comment-stripped), not a compiler
  check — a determined author could still inline a colour via an intermediate `val`, but the tripwire
  catches the common regression.
- **Follow-ups:** #391 stays open for the deferred **paid** lanes — raster (icon/feature graphic/
  screenshots; needs a paid image tool + human-edit copyright pass; FLUX.1-dev is non-commercial) and
  audio (ElevenLabs; the "Studio Games" clause must be resolved before shipping new audio in a monetized
  build).

## Links
- Commits: `0e64754` (C1), `c7d1d10` (C2 merge), `d522569` (C3+guard merge), `6d1f83f` (C4 merge),
  `139d495` (C5 merge). PRs #427/#428/#429/#430/#431.
- Related ADRs: ADR-0014 (i18n / locale-readiness — the tone bible respects it) · ADR-0022 (design tokens
  + de-emoji, the `Color.kt` brand palette) · ADR-0038 (concurrency-reviewer mandatory lane — run on C4).
- Spec/plan: `docs/superpowers/specs/2026-07-09-asset-pipeline-free-lane-design.md`,
  `docs/superpowers/plans/2026-07-09-asset-pipeline-c1-style-bible.md`.
