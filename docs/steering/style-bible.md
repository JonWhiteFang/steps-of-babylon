# Style Bible — Steps of Babylon

The single reference for the game's **visual art direction**. It is the human companion to the code anchor
`presentation/battle/biome/BattlePalette.kt` (the art-colour source of truth) and the brand tokens in
`presentation/ui/theme/Color.kt`. Authored for #421 (#391 free / code-drawable lane).

> **Why this exists.** The battle renderer draws 100% procedurally (Canvas primitives — zero raster
> sprites), which makes art *deterministic and centralisable*. Before #421 the palette was ~50 anonymous
> `0xFF…` ints scattered across three files with no shared vocabulary. This bible + `BattlePalette` give
> the art system one governed source, so every biome/enemy/ziggurat reads as one system.

See also: GDD §12.2 (Visual Style), §6.3 (Narrative Biome Progression), §10 (Enemy Types).

---

## 1. Art direction

Blend a **clean, minimalist game aesthetic** with **ancient Mesopotamian art direction** (GDD §12.2):
cuneiform-inspired UI elements, lapis-lazuli accents, and Babylonian geometric motifs. Each biome drives
the art with a distinct palette, particle system, and environmental mood — the player should feel they are
*travelling through a world powered by their footsteps* (Design Pillar: Journey of Discovery).

## 2. Brand palette (identity anchors)

The five brand colours (source: `Color.kt`). Use for fills, brand accents, primary actions. These are the
identity — the biome palettes below are *variations that live alongside* them, not replacements.

| Token | Hex | Role |
|---|---|---|
| Gold | `#D4A843` | Lifeblood accent; Steps currency; ziggurat apex; primary buttons |
| LapisLazuli | `#26619C` | Deep brand blue (fills/containers; use `LapisLight #A7C7E7` for text on dark) |
| SandStone | `#C2B280` | Warm neutral; rarity tier-0 |
| DeepBronze | `#6B3A2A` | Dark surface / window background |
| Ivory | `#FFF8E7` | Primary body text on bronze |

Contrast-critical role tokens (OnGold, LapisLight, TextPrimary/Secondary) are documented in `Color.kt` and
guarded by `ContrastTest`. **Do not** hand-pick a new text-on-surface colour without checking contrast there.

## 3. Biome palettes (art colours)

Five narrative biomes tied to tier ranges (GDD §6.3). Each is a coherent mood built from a sky gradient
(top→bottom `LinearGradient`), a ground colour, a 5-stop ziggurat ramp (base→apex), an enemy tint (blended
30% into enemy base colours), and an ambient particle system. **All values live in `BattlePalette.forBiome`.**

| Biome | Tiers | Sky (top→bottom) | Ground | Mood | Particles |
|---|---|---|---|---|---|
| Hanging Gardens | 1–3 | `#2E5D3A → #4A7C59` (verdant green) | `#3B5E2B` | Lush, welcoming; the starting garden | Soft green motes, gentle downward drift |
| Burning Sands | 4–6 | `#B85C1E → #D4943A` (amber desert) | `#C2A060` | Harsh heat; fast horizontal sand | Sandy drift, strong X-wind |
| Frozen Ziggurats | 7–8 | `#1A3A5C → #4682B4` (steel blue) | `#B0C4DE` | Cold, crystalline; steel-blue ramp | Dense white snow, downward |
| Underworld of Kur | 9–10 | `#1A0A2E → #2D1B4E` (deep violet) | `#1A1A2E` | Oppressive dark; embers rise | Orange embers drifting *up* |
| Celestial Gate | 11+ | `#0A0A2A → #1A1A4A` (near-black night) | `#15153A` | Cosmic finale; royal-blue→gold ramp | Bright starfield, near-static |

*Ziggurat ramps run base (earthy/cool) → apex (warm/gold or bright), reinforcing "ascension."* Exact 5-stop
ramps are in `BattlePalette`; `BiomeThemeTest` + `BattlePaletteTest` pin them so a value can't silently drift.

## 4. Enemy visual language (GDD §10)

Six enemy types, each a distinct **silhouette + base colour** so they read instantly at speed. Shapes are
functional identity (do not re-skin arbitrarily); the biome `enemyTint` blends 30% over the base for
environmental cohesion. Base colours live in `BattlePalette.enemyBaseColors` (repointed by C2, #422).

| Type | Silhouette | Base colour | Size | Read |
|---|---|---|---|---|
| BASIC | Circle | `#E53935` red | 20 | The default threat |
| FAST | Triangle (point up) | `#FF9800` orange | 16 | Small, sharp, quick |
| TANK | Square | `#8B0000` dark red | 28 | Heavy, blocky, slow |
| RANGED | Diamond | `#9C27B0` purple | 20 | Stands off, fires |
| BOSS | Circle (large) | `#4A0000` near-black red | 40 | Rare, imposing |
| SCATTER | Circle | `#4CAF50` green | 20 | Splits on death |

## 5. Ziggurat

Five stacked `drawRect` layers, narrowing base→apex (`ZigguratEntity`), with a gold origin marker at the
firing point. The default ramp (`BattlePalette.zigguratDefaultLayers`) runs bronze `#8B7355` → sandstone →
gold `#D4A843` (apex). Biomes override the ramp via their `zigguratColors` (§3). Repointed by C3 (#423).

## 6. Particle / effect vocabulary

Each biome carries a named `BattlePalette.ParticleConfig` (`color`, `driftX`, `driftY`, `count`) — the
ambient-emitter vocabulary (#424, C4), consumed by `BackgroundRenderer`. Drift direction is a mood lever:
downward (gardens/snow), horizontal +X (sand-wind), upward -Y (rising embers). `BiomeTheme.particles` is the
canonical field; the flat `particleColor`/`particleDriftX/Y`/`particleCount` accessors delegate to it (kept
so existing readers — `GameEngine`, `BackgroundRenderer` — are untouched).

> **Scope note (C4).** `EffectEngine`/`ParticlePool` are a *generic* effect system and do NOT consume the
> per-biome config — only `BackgroundRenderer` does. C4 deliberately did **not** touch the `EffectEngine`
> `effectsLock` regions (fragile zone); the "feed EffectEngine" framing in the original spec was descoped as
> unnecessary. Any future work there must keep the lock discipline.

## 7. Functional palette (UI signal — NOT art, NOT in BattlePalette)

These colours encode **gameplay state**, not art direction. They are deliberately kept inline at their
consumption sites and are **excluded from `BattlePalette`** (and allowlisted in the `BattleArtPaletteTest`
guard, #426). Do not fold them into a biome palette — a biome re-tune must never change what a health bar means.

| Signal | Colour(s) | Where |
|---|---|---|
| HP-bar ratio | green `#4CAF50` (>0.6) / yellow `#FFEB3B` (>0.3) / red `#F44336` | `EnemyEntity.render`, health-bar renderers |
| HP-bar background | `#2A1A10` | `EnemyEntity.render` |
| Armor charge | cyan stroke `#5500BCD4` | `EnemyEntity` armor ring |
| Ziggurat attack range | fill `#22FFFFFF` / stroke `#44FFFFFF` | `ZigguratEntity.render` |
| Ziggurat origin marker | gold `#FFD700` | `ZigguratEntity.render` |

## 8. Line weights & motifs

- Ambient/UI hairlines ~1.5f (range stroke, HP frames); entity outlines via ANTI_ALIAS fills.
- Babylonian geometric motifs, cuneiform-inspired UI framing, lapis accents (GDD §12.2) — apply sparingly so
  the minimalist read stays clean.

---

## Maintenance

- **`BattlePalette` is the single art-colour source of truth.** New biome/enemy/ziggurat art colours go there,
  named — not as an inline `0xFF…` literal (enforced by `BattleArtPaletteTest`, #426).
- Functional-signal colours (§7) stay inline + allowlisted — keep the art/functional split.
- A *deliberate* re-tune is fine, but note it in the PR and eyeball it on-device (no pixel-snapshot harness).
