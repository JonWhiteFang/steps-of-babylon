# Plan 06 — Home Screen & Navigation

**Status:** Complete
**Dependencies:** Plan 03 (Repository Layer)
**Layer:** `presentation/` — Compose UI

---

## Objective

Build the Compose navigation graph, bottom navigation bar, and the Home/Dashboard screen. The Home screen is the player's hub — showing today's step count, spendable balance, current tier/biome, best wave record, and a quick-launch battle button. After this plan, the app has real navigation between screens.

Reference: GDD §12.1 for screen descriptions.

---

## Task Breakdown

### Task 1: Navigation Graph

Create `presentation/navigation/NavGraph.kt`:
- Define sealed class/object `Screen` with routes: `Home`, `Workshop`, `Battle`, `Labs`, `Stats`
- Set up `NavHost` with composable destinations
- Home as start destination
- Placeholder composables for Workshop, Battle, Labs, Stats (filled in later plans)

---

### Task 2: Bottom Navigation Bar

Create `presentation/navigation/BottomNavBar.kt`:
- Bottom navigation with items: Home, Workshop, Battle, Labs, Stats
- Icons and labels for each destination
- Highlight current route
- Integrated into `MainActivity`'s `Scaffold`

---

### Task 3: HomeViewModel

Create `presentation/home/HomeViewModel.kt`:
- `@HiltViewModel` injecting `PlayerRepository`, `WorkshopRepository`, `StepRepository`
- Exposes `StateFlow<HomeUiState>` with:
  - `todaySteps: Long`
  - `stepBalance: Long`
  - `gems: Long`
  - `powerStones: Long`
  - `currentTier: Int`
  - `currentBiome: Biome`
  - `bestWave: Int` (best wave for current tier)
  - `isLoading: Boolean`
- Collects from repository Flows and combines into single UI state

---

### Task 4: HomeScreen UI

Update `presentation/home/HomeScreen.kt`:
- Biome-themed background (color gradient based on current biome)
- Step counter card: "Today: X steps"
- Balance display: Step balance, Gems, Power Stones
- Tier/Biome indicator: "Tier 6 — The Burning Sands"
- Best wave record: "Best Wave: 87"
- Battle button: large, prominent, navigates to Battle screen
- Pull-to-refresh or auto-update via Flow collection

---

### Task 5: Update MainActivity

Update `presentation/MainActivity.kt`:
- Replace placeholder content with `Scaffold` containing `BottomNavBar` and `NavGraph`
- Handle navigation state
- Ensure `ensureProfileExists()` and `ensureUpgradesExist()` are called on first launch (via a startup ViewModel or Application init)

---

## File Summary

```
presentation/
├── navigation/
│   ├── NavGraph.kt             (new)
│   └── BottomNavBar.kt         (new)
├── home/
│   ├── HomeViewModel.kt        (new)
│   ├── HomeScreen.kt           (update)
│   └── HomeUiState.kt          (new)
└── MainActivity.kt             (update)
```

## Completion Criteria

- Bottom navigation bar with 5 destinations renders correctly
- Navigation between screens works (placeholder screens for non-Home)
- HomeScreen displays live step count, balance, tier, biome, and best wave from Room
- HomeViewModel collects repository Flows and exposes combined StateFlow
- Battle button navigates to Battle screen route
- Default player profile and workshop data seeded on first launch
