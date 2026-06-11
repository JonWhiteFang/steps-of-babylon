# Plan 08 — Battle Renderer: Game Loop & Ziggurat

**Status:** Complete
**Dependencies:** Plan 06 (Home Screen & Navigation)
**Layer:** `presentation/battle/` — Custom SurfaceView renderer

---

## Objective

Build the custom SurfaceView battle renderer with a dedicated game loop thread, fixed timestep, ziggurat entity, health bar, and basic projectile system. This is the foundation for all battle gameplay — enemies and combat come in Plans 09–10.

Reference: `docs/architecture.md` §Game Loop Architecture.

---

## Task Breakdown

### Task 1: GameSurfaceView

Create `presentation/battle/GameSurfaceView.kt`:
- Extends `SurfaceView` implements `SurfaceHolder.Callback`
- Creates and manages the game loop thread
- Handles surface created/changed/destroyed lifecycle
- Passes touch events to the game engine

---

### Task 2: Game Loop Thread

Create `presentation/battle/GameLoopThread.kt`:
- Dedicated thread running the update-render loop
- Fixed timestep: 60 updates/sec (16.67ms per tick)
- Accumulator pattern to decouple update rate from render rate
- Speed multiplier support (1x/2x/4x) — multiplies tick rate
- Measures and exposes FPS for debug overlay

---

### Task 3: Game Engine

Create `presentation/battle/engine/GameEngine.kt`:
- Central coordinator: holds game state, entity lists, and game clock
- `update(deltaTime: Float)` — updates all entities
- `render(canvas: Canvas)` — draws all entities
- Manages entity lifecycle (add/remove)
- Holds reference to current `RoundState`

---

### Task 4: Entity Base

Create `presentation/battle/engine/Entity.kt`:
- Abstract base class for all game entities
- Properties: `x`, `y`, `width`, `height`, `isAlive`
- Abstract `update(deltaTime: Float)` and `render(canvas: Canvas)`

---

### Task 5: Ziggurat Entity

Create `presentation/battle/entities/ZigguratEntity.kt`:
- Extends `Entity`
- Positioned center-bottom of screen
- Renders as a layered ziggurat shape (rectangles stacked, narrowing upward)
- Holds current HP and max HP
- Auto-attack logic: fires projectiles at nearest enemy at attack speed interval
- Attack range circle (debug drawable)

---

### Task 6: Projectile Entity

Create `presentation/battle/entities/ProjectileEntity.kt`:
- Extends `Entity`
- Moves from ziggurat toward target position at fixed speed
- On reaching target or hitting enemy → apply damage → destroy self
- Basic rendering: small circle or line

---

### Task 7: Health Bar Renderer

Create `presentation/battle/ui/HealthBarRenderer.kt`:
- Draws ziggurat health bar at top of screen
- Green → yellow → red gradient based on HP percentage
- Shows numeric HP value

---

### Task 8: Battle Screen Compose Wrapper

Create `presentation/battle/BattleScreen.kt`:
- Compose screen that hosts the `GameSurfaceView` via `AndroidView`
- Overlay Compose UI elements: wave counter, speed controls, pause button
- Speed control buttons (1x/2x/4x) that update `GameLoopThread` speed multiplier
- Back/exit button to end round and navigate away

---

### Task 9: BattleViewModel

Create `presentation/battle/BattleViewModel.kt`:
- `@HiltViewModel` injecting `WorkshopRepository`, `PlayerRepository`
- Loads workshop upgrade levels and player tier on round start
- Creates initial `RoundState`
- Exposes game state to Compose overlay (wave count, HP, cash)
- Handles round end → navigates back

---

## File Summary

```
presentation/battle/
├── GameSurfaceView.kt          (new)
├── GameLoopThread.kt           (new)
├── BattleScreen.kt             (new)
├── BattleViewModel.kt          (new)
├── engine/
│   ├── GameEngine.kt           (new)
│   └── Entity.kt               (new)
├── entities/
│   ├── ZigguratEntity.kt       (new)
│   └── ProjectileEntity.kt     (new)
└── ui/
    └── HealthBarRenderer.kt    (new)
```

## Completion Criteria

- SurfaceView renders at stable 60 FPS with fixed timestep
- Ziggurat draws center-bottom and has visible health bar
- Projectiles fire from ziggurat toward a test target position
- Speed controls (1x/2x/4x) change game tick rate
- Game loop thread starts/stops cleanly with surface lifecycle
- BattleScreen integrates SurfaceView with Compose overlay
- Navigation from Home → Battle → back works
