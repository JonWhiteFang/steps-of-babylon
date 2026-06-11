# Plan 27 — Polish & Visual Effects

**Status:** Complete
**Dependencies:** Plan 18 (Narrative Biome Progression)
**Layer:** `presentation/battle/` + `presentation/`

---

## Objective

Add visual polish to the battle renderer and UI: projectile trail effects, Ultimate Weapon spectacles, Overdrive aura animations, enemy death animations, wave transition effects, UI animations/transitions, and sound effects integration. This plan transforms the functional game into a visually satisfying experience.

---

## Task Breakdown

### Task 1: Projectile Trail Effects

Create `presentation/battle/effects/ProjectileTrailRenderer.kt`:
- Projectiles leave fading trail particles behind them
- Trail color matches biome theme or equipped cosmetic
- Crit hits: larger, brighter trail with flash on impact
- Bounce shots: trail persists through bounces

---

### Task 2: Ultimate Weapon Visual Spectacles

Create `presentation/battle/effects/UWEffectRenderer.kt`:
- Enhanced visuals for each UW activation:
  - Death Wave: expanding green shockwave ring with screen shake
  - Chain Lightning: animated arcs jumping between enemies with flash
  - Black Hole: swirling vortex with enemy pull animation
  - Chrono Field: blue overlay with slow-motion visual distortion
  - Poison Swamp: bubbling green ground with rising vapor particles
  - Golden Ziggurat: gold glow, coin particle rain, shimmer effect

---

### Task 3: Overdrive Aura Animations

Update `ZigguratEntity` overdrive rendering:
- Pulsing aura with particle emission:
  - Assault (red): fire particles, aggressive pulse
  - Fortress (blue): shield bubble, calm pulse
  - Fortune (gold): coin sparkles, warm glow
  - Surge (purple): energy crackle, electric arcs
- Aura intensity fades as timer approaches 0
- Screen-edge vignette in aura color

---

### Task 4: Enemy Death Animations

Create `presentation/battle/effects/DeathEffectRenderer.kt`:
- Enemies don't just disappear — death effects per type:
  - Basic: fade + small particle burst
  - Fast: streak dissolve (speed lines)
  - Tank: crumble/shatter effect
  - Ranged: projectile scatter on death
  - Boss: large explosion with screen shake
  - Scatter: split animation before children spawn
- Cash reward floating text (+X) rises from death position

---

### Task 5: Wave Transition Effects

Create `presentation/battle/effects/WaveTransitionRenderer.kt`:
- Wave number announcement: large text slides in "Wave 15" then fades
- Boss wave warning: "BOSS INCOMING" with red flash
- Cooldown phase: subtle screen dim with "Next Wave In: Xs" countdown

---

### Task 6: UI Animations

Update Compose screens:
- Workshop: upgrade purchase → card pulse animation, level counter increment
- Labs: research start → progress bar fill animation
- Cards: pack opening → card reveal animation (flip/glow)
- Home: step counter → smooth counting animation
- Navigation: screen transitions with slide/fade

---

### Task 7: Sound Effects Integration

Create `presentation/audio/SoundManager.kt`:
- `SoundPool`-based sound effect system
- Sound effects for:
  - Projectile fire, projectile hit
  - Enemy death (per type)
  - UW activation (per type)
  - Overdrive activation
  - Upgrade purchase
  - Cash earned
  - Wave start/end
  - Round end
  - Button taps
- Volume control in settings
- Mute toggle

---

### Task 8: Screen Shake System

Create `presentation/battle/effects/ScreenShakeSystem.kt`:
- Canvas offset oscillation for impact moments
- Triggers: boss death, UW activation, heavy damage taken
- Intensity and duration configurable
- Respects reduced motion setting

---

## File Summary

```
presentation/battle/effects/
├── ProjectileTrailRenderer.kt  (new)
├── UWEffectRenderer.kt         (new)
├── DeathEffectRenderer.kt      (new)
├── WaveTransitionRenderer.kt   (new)
└── ScreenShakeSystem.kt        (new)

presentation/audio/
└── SoundManager.kt             (new)

presentation/battle/entities/
├── ZigguratEntity.kt           (update — enhanced aura)
└── EnemyEntity.kt              (update — death animation hook)

presentation/ (various screens)
└── (updates for UI animations)
```

## Completion Criteria

- Projectile trails render smoothly without FPS drops
- All 6 UW types have distinct, impressive visual effects
- Overdrive auras pulse with correct colors and fade over time
- Enemy deaths have type-appropriate animations
- Wave transitions announce wave number and boss warnings
- UI animations feel responsive (upgrade purchase, pack opening, etc.)
- Sound effects play for all key game events
- Screen shake triggers on impactful moments
- All effects respect reduced motion accessibility setting
- Visual effects don't drop FPS below 30 on target devices
