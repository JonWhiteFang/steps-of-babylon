# Compose UI Tests — Critical Screens (#253)

## Goal

Close issue #253 by adding Compose UI tests for the 7 critical screens with
purchase/claim/state-transition logic. After this work, every screen that
handles currency spend, claim flows, or loadout management has rendered-behaviour
verification on the JVM test lane.

## Pattern

All tests follow the established pattern from `HomeScreenTest` / `CardsScreenTest` /
`OnboardingScreenTest`:

- Robolectric (`@RunWith(RobolectricTestRunner::class)`)
- `@GraphicsMode(GraphicsMode.Mode.NATIVE)` + `@Config(sdk = [34], application = android.app.Application::class)`
- `createComposeRule()` (backed by `ui-test-manifest` on `debugImplementation`)
- `Dispatchers.setMain(UnconfinedTestDispatcher())` so `viewModelScope` + `WhileSubscribed` run eagerly
- Real ViewModel wired with the existing `test/fakes/` — no Hilt graph, no mocks (except where noted)
- `composeRule.waitForIdle()` after `setContent` to let the composition observe seeded fake state

## Screens & Test Cases

### 1. WorkshopScreenTest (3 tests)

**File:** `app/src/test/java/com/whitefang/stepsofbabylon/presentation/workshop/WorkshopScreenTest.kt`

**ViewModel deps:** `WorkshopRepository`, `PlayerRepository`, `MissionRepository`, `SavedStateHandle`
**Fakes:** `FakeWorkshopRepository`, `FakePlayerRepository`, `FakeMissionRepository(FakeDailyMissionDao())`, `SavedStateHandle()`

| Test | Asserts |
|------|---------|
| `renders step balance and upgrade list` | Balance text (stringResource with step count) visible; at least one upgrade type display name (from `UpgradeType.entries`) rendered |
| `purchase button disabled when balance insufficient` | Seed balance=0; the first upgrade's purchase affordance is not enabled |
| `successful purchase increments level` | Seed high balance; click purchase; verify level text updates (e.g. "Lv 1" → "Lv 2") |

---

### 2. StoreScreenTest (3 tests)

**File:** `app/src/test/java/com/whitefang/stepsofbabylon/presentation/store/StoreScreenTest.kt`

**ViewModel deps:** `PlayerRepository`, `BillingManager`, `CosmeticRepository`
**Fakes:** `FakePlayerRepository`, `FakeBillingManager`, `FakeCosmeticRepository`

| Test | Asserts |
|------|---------|
| `renders gem balance and section headers` | Gem count visible; "Gem Packs", "Premium", "Cosmetics" headers exist |
| `Buy buttons present for gem packs` | At least one "Buy" button node exists |
| `ad-removed state shows Purchased` | Seed `adRemoved=true` in fake billing state; "Purchased" text exists; no Buy button next to Ad Removal |

---

### 3. LabsScreenTest (3 tests)

**File:** `app/src/test/java/com/whitefang/stepsofbabylon/presentation/labs/LabsScreenTest.kt`

**ViewModel deps:** `LabRepository`, `PlayerRepository`, `MissionRepository`, `TimeBaselineSource`
**Fakes:** `FakeLabRepository`, `FakePlayerRepository`, `FakeMissionRepository(FakeDailyMissionDao())`, `FakeTimeBaselineSource`

| Test | Asserts |
|------|---------|
| `renders balances and lab slot count` | Step balance visible; "Lab Slots: X/Y" text exists |
| `Start button disabled when balance insufficient` | Seed balance=0; "Start" button is not enabled |
| `active research shows progress and Rush button` | Seed an active research in the fake; progress indicator exists; "Rush" text exists |

---

### 4. MissionsScreenTest (3 tests)

**File:** `app/src/test/java/com/whitefang/stepsofbabylon/presentation/missions/MissionsScreenTest.kt`

**ViewModel deps:** `MissionRepository`, `MilestoneRepository`, `StepRepository`, `PlayerRepository`, `CosmeticRepository`, `TimeProvider`
**Fakes:** `FakeMissionRepository(FakeDailyMissionDao())`, `FakeMilestoneRepository(FakeMilestoneDao())`, `FakeStepRepository`, `FakePlayerRepository`, `FakeCosmeticRepository`, `FakeTimeProvider`

| Test | Asserts |
|------|---------|
| `renders Daily Missions header and mission cards` | "Daily Missions" text exists; mission description text from seeded fake visible |
| `Claim button appears when mission is complete but unclaimed` | Seed a completed-unclaimed mission; "Claim" button exists |
| `milestones section renders with step progress` | "Walking Milestones" header exists; a milestone's `displayName` text visible |

---

### 5. UltimateWeaponScreenTest (3 tests)

**File:** `app/src/test/java/com/whitefang/stepsofbabylon/presentation/weapons/UltimateWeaponScreenTest.kt`

**ViewModel deps:** `UltimateWeaponRepository`, `PlayerRepository`
**Fakes:** `FakeUltimateWeaponRepository`, `FakePlayerRepository`

| Test | Asserts |
|------|---------|
| `renders power stones balance and weapon list` | "Power Stones: N" text exists; at least one UW name (e.g. "Chain Lightning") visible |
| `Unlock button shows cost and disabled when cant afford` | Seed powerStones=0; Unlock button with cost label exists; button is not enabled |
| `equipped 3 of 3 cap message when three equipped` | Seed 3 equipped weapons; "Equipped: 3/3 — unequip one to swap" text exists |

---

### 6. UnclaimedSuppliesScreenTest (3 tests)

**File:** `app/src/test/java/com/whitefang/stepsofbabylon/presentation/supplies/UnclaimedSuppliesScreenTest.kt`

**ViewModel deps:** `WalkingEncounterRepository`, `PlayerRepository`, `CardRepository`
**Fakes:** `FakeWalkingEncounterRepository`, `FakePlayerRepository`, `FakeCardRepository`

| Test | Asserts |
|------|---------|
| `renders supply drop list with Claim buttons` | Seed 2 drops; "Claim" button exists (count ≥ 1) |
| `empty state shows no drops message` | Seed empty drops; "No supply drops yet — keep walking!" text exists |
| `Claim All button present when drops exist` | Seed drops; "Claim All" text exists |

---

### 7. BattleControlRailTest (2 tests)

**File:** `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleControlRailTest.kt`

**Approach:** Test the extracted `BattleControlRail` composable directly (pure presentational,
takes state + callbacks, no ViewModel). This avoids wiring BattleViewModel's ~15 deps and
the untestable `GameSurfaceView`.

| Test | Asserts |
|------|---------|
| `renders speed buttons and highlights current speed` | Pass `speedMultiplier=2f`; all three speed labels ("1x", "2x", "4x") exist; content descriptions present |
| `pause button toggles callback` | Pass `isPaused=false`; click pause button; verify callback invoked |

---

## Out of Scope

- **SurfaceView / GameEngine rendering** — Robolectric cannot run the GL-backed game loop.
- **Navigation assertions** — fragile under Robolectric; already guarded by `OnboardingRoutingTest`.
- **Snackbar timing** — race-prone with `UnconfinedTestDispatcher`.
- **Display-only screens** (Stats, Settings, Help, CurrencyDashboard) — these have no
  purchase/claim/state-transition logic; coverage here is lower-value.

## New Fakes Required

None. All ViewModel dependencies already have fakes in `app/src/test/java/com/whitefang/stepsofbabylon/fakes/`.

## Test Count Impact

~20 new tests → headline moves from **1234 → ~1254 JVM tests**.

## Acceptance Criteria

1. All 7 test files compile and pass on `./run-gradle.sh testDebugUnitTest`.
2. Issue #253 can be closed (the "zero Compose UI tests" claim is no longer accurate — every
   critical screen with user-facing interactions has rendered-behaviour verification).
3. No production code changes required.
