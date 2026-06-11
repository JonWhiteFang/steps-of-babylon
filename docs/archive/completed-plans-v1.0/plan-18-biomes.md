# Plan 18 — Narrative Biome Progression

**Status:** Not Started
**Dependencies:** Plan 13 (Tier System & Progression)
**Layer:** `presentation/battle/` + `presentation/`

---

## Objective

Implement the 5 narrative biomes mapped to tier ranges. Each biome changes the battlefield environment art, enemy visual themes, ziggurat appearance, and triggers a transition cinematic. This plan transforms the battle screen from a static arena into a visual journey.

Reference: GDD §6.3 for biome definitions.

---

## Task Breakdown

### Task 1: Biome Asset Definitions

Create `presentation/battle/biome/BiomeTheme.kt`:
- Data class holding visual config per biome:
  - `backgroundColor: Color` (gradient top/bottom)
  - `groundColor: Color`
  - `particleColor: Color`
  - `zigguratColors: List<Color>` (layer tints)
  - `enemyTint: Color`
  - `ambientParticleType: String` (leaves, sand, snow, embers, stars)
- Companion object mapping `Biome` enum → `BiomeTheme`

Biome color palettes:
- Hanging Gardens (T1–3): green/gold
- Burning Sands (T4–6): orange/amber
- Frozen Ziggurats (T7–8): blue/white
- Underworld of Kur (T9–10): purple/teal
- Celestial Gate (T11+): multi-chromatic

---

### Task 2: Battlefield Background Renderer

Create `presentation/battle/biome/BackgroundRenderer.kt`:
- Renders biome-specific background on the SurfaceView canvas
- Gradient sky, ground plane, and ambient particles
- Particles: floating leaves (Gardens), drifting sand (Sands), falling snow (Frozen), rising embers (Kur), drifting stars (Celestial)
- Parallax scrolling effect for depth

---

### Task 3: Ziggurat Appearance per Biome

Update `ZigguratEntity`:
- Ziggurat visual changes per biome:
  - Gardens: mud-brick with green terraces
  - Sands: fired-brick with orange brazier glow
  - Frozen: ice-encrusted with blue-glazed tiers
  - Kur: obsidian with glowing purple wards
  - Celestial: translucent with crackling energy
- Apply `BiomeTheme.zigguratColors` to layer rendering

---

### Task 4: Enemy Visual Themes

Update `EnemyEntity`:
- Apply `BiomeTheme.enemyTint` to enemy rendering
- Enemy shape/silhouette variations per biome (color shifts, not full resprites)
- Boss enemies get biome-specific accent effects

---

### Task 5: Biome Transition Cinematic

Create `presentation/biome/BiomeTransitionScreen.kt`:
- Full-screen cinematic shown when player first reaches a new biome
- Shows ziggurat "ascending" animation
- Displays total steps walked to reach this biome
- Biome name reveal with thematic styling
- "Continue" button to dismiss
- Triggered on first round start at a new biome's tier range

---

### Task 6: Biome Unlock Tracking

Update `PlayerProfileEntity`:
- Add `unlockedBiomes: String` (JSON set of biome names, default `["HANGING_GARDENS"]`)
- Migration for new column

Create `domain/usecase/CheckBiomeUnlock.kt`:
- Given current tier, checks if player has entered a new biome
- Returns new `Biome` if unlocked for the first time, null otherwise

---

### Task 7: Home Screen Biome Theme

Update `HomeScreen`:
- Background color/gradient matches current biome
- Biome name displayed alongside tier

---

## File Summary

```
presentation/battle/biome/
├── BiomeTheme.kt               (new)
└── BackgroundRenderer.kt       (new)

presentation/biome/
└── BiomeTransitionScreen.kt    (new)

presentation/battle/entities/
├── ZigguratEntity.kt           (update — biome appearance)
└── EnemyEntity.kt              (update — biome tint)

presentation/battle/
├── GameEngine.kt               (update — use BackgroundRenderer)
└── BattleViewModel.kt          (update — biome check on round start)

presentation/home/
└── HomeScreen.kt               (update — biome-themed background)

domain/usecase/
└── CheckBiomeUnlock.kt         (new)

data/local/
├── PlayerProfileEntity.kt      (update — add unlockedBiomes)
└── AppDatabase.kt              (update — migration)
```

## Completion Criteria

- All 5 biomes render with distinct color palettes and ambient particles
- Ziggurat appearance changes per biome
- Enemy tinting matches biome theme
- Biome transition cinematic plays on first entry to a new biome
- Total steps walked displayed in cinematic
- Home screen background reflects current biome
- Biome unlocks tracked and persisted
- Correct biome selected based on current tier via `Biome.forTier()`
