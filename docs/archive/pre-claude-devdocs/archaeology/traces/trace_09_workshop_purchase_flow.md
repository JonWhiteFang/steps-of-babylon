# Trace 09 — Workshop purchase: tap Buy → Room → reactive UI

*Phase 3 Deep Trace. Ground truth:
`presentation/workshop/WorkshopScreen.kt`,
`presentation/workshop/WorkshopViewModel.kt`,
`domain/usecase/PurchaseUpgrade.kt`,
`domain/usecase/CalculateUpgradeCost.kt`,
`data/repository/PlayerRepositoryImpl.kt`,
`data/repository/WorkshopRepositoryImpl.kt`,
`data/local/PlayerProfileDao.kt`,
`data/local/WorkshopDao.kt`. Exemplar of the "four-layer trace" that all
non-battle screens follow.*

## 1. Entry Point

- User opens `WorkshopScreen` (from bottom nav or Home Screen CTA).
- `hiltViewModel()` resolves `WorkshopViewModel` (cached per
  back-stack entry).
- ViewModel exposes `StateFlow<WorkshopUiState>`; Compose observes it
  via `collectAsState()`.
- User taps the "Buy" button on an `UpgradeCard`, which calls
  `viewModel.purchase(type: UpgradeType)`.

Alternatively, user taps the Quick Invest FAB → `viewModel.quickInvest()`
which picks the cheapest affordable upgrade and invokes the same
internal purchase helper.

## 2. Execution Path

### 2.1 ViewModel construction — the reactive pipe

```kotlin
val uiState: StateFlow<WorkshopUiState> = combine(
    workshopRepository.observeAllUpgrades(),   // Flow<Map<UpgradeType, Int>>
    playerRepository.observeWallet(),           // Flow<PlayerWallet>
    _selectedCategory,
    _processing,
    _userMessage,
) { upgrades, wallet, category, processing, message ->
    allUpgrades = upgrades
    val stats = resolveStats(upgrades)          // pure; computed every emit
    val filtered = upgrades.filter { (type, _) ->
        type.category == category && type !in hiddenUpgrades
    }
    WorkshopUiState(
        upgrades = filtered.map { (type, level) ->
            UpgradeDisplayInfo(
                type = type, level = level,
                cost = if (isMaxed) 0L else calculateCost(type, level),
                isMaxed = …,
                canAfford = !isMaxed && wallet.stepBalance >= cost,
                description = type.config.description,
                statValue = statValueFor(type, stats),
            )
        },
        stepBalance = wallet.stepBalance,
        selectedCategory = category,
        isLoading = false,
        isProcessing = processing,
        userMessage = message,
    )
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkshopUiState())
```

Two Room Flow sources feed the state:

- `WorkshopRepository.observeAllUpgrades()` maps
  `WorkshopDao.getAll()` to `Map<UpgradeType, Int>`.
- `PlayerRepository.observeWallet()` maps
  `PlayerProfileDao.get()` (Flow<PlayerProfileEntity?>) via
  `filterNotNull().map { it.toDomain().toWallet() }`.

Both re-emit automatically whenever their underlying tables mutate.
`combine` ensures `uiState` is recomputed as soon as *either* source
emits.

### 2.2 Purchase tap — write path

```kotlin
fun purchase(type: UpgradeType) {
    if (_processing.value) return                                    // debounce
    viewModelScope.launch {
        _processing.value = true
        try {
            val level = allUpgrades[type] ?: 0
            val maxLevel = type.config.maxLevel
            if (maxLevel != null && level >= maxLevel) {
                _userMessage.value = "Already at max level"
                return@launch
            }
            val wallet = playerRepository.observeWallet().first()    // one-shot read
            val cost = calculateCost(type, level)
            val success = purchaseUpgrade(type, level, wallet)       // use case
            if (success) {
                try {
                    val today = LocalDate.now().toString()
                    val missions = dailyMissionDao.getByDateOnce(today)
                    val m = missions.find {
                        it.missionType == DailyMissionType.SPEND_5000_WORKSHOP.name
                        && !it.claimed && !it.completed
                    }
                    if (m != null) {
                        dailyMissionDao.updateProgress(m.id, m.progress + cost.toInt(),
                            m.progress + cost.toInt() >= m.target)
                    }
                } catch (_: Exception) {}
            } else {
                _userMessage.value = "Not enough Steps"
            }
        } finally {
            _processing.value = false
        }
    }
}
```

### 2.3 Inside `PurchaseUpgrade.invoke(type, currentLevel, wallet)`

```kotlin
val maxLevel = type.config.maxLevel
if (maxLevel != null && currentLevel >= maxLevel) return false

val cost = calculateCost(type, currentLevel)   // baseCost * (scaling ^ level)
if (wallet.stepBalance < cost) return false

playerRepository.spendSteps(cost)              // PlayerProfileDao.adjustStepBalance(-cost)
workshopRepository.setUpgradeLevel(type, currentLevel + 1)
return true
```

- **`playerRepository.spendSteps(cost)`** =
  `PlayerProfileDao.adjustStepBalance(-cost)` =
  `UPDATE player_profile SET currentStepBalance = MAX(0, currentStepBalance + -cost), totalStepsEarned = CASE WHEN -cost > 0 THEN ... END WHERE id = 1`.
  The `MAX(0, ...)` clamp is the reason we "cannot go negative":
  even if two concurrent purchases race, the second atomic update
  floors at 0.
- **`workshopRepository.setUpgradeLevel(type, level+1)`** upserts
  `WorkshopUpgradeEntity(upgradeType = type.name, level = level+1)`.

Both writes trigger Room Flow re-emissions on their respective DAOs.
The `combine` in §2.1 wakes up twice (once per write), but
`stateIn(WhileSubscribed(5000))` collapses identical emissions and
batches at the Main dispatcher. The UI recomposes with the new
`UpgradeDisplayInfo` — higher level, higher cost, possibly
`canAfford=false` now.

## 3. Resource Management

| Concern | How |
|---|---|
| Threading | UI on Main; the purchase coroutine runs on `viewModelScope` (Main by default, with internal suspends on Room's dispatcher). |
| Debounce | `_processing: MutableStateFlow<Boolean>` gated by the first line of `purchase`. Atomic-ish: `if (_processing.value) return` + `_processing.value = true`. On multi-tap, subsequent taps within the same `launch` body see `true` and early-return. Note: without a compare-and-set, two near-simultaneous calls on the Main dispatcher can both pass the check (see §9). |
| State re-emission | `combine` + `stateIn(WhileSubscribed(5000))` — after 5 s with no observers, the upstream Flow unsubscribes. On next subscriber, Room re-emits. |
| Atomicity | No database transaction around `spendSteps` + `setUpgradeLevel`. See §9. |
| Currency clamp | `MAX(0, ...)` in SQL prevents negative balance even on race. |
| Mission side effect | Best-effort swallow (`try/catch (_: Exception) {}`). Consistent with the follow-on pipeline (trace 04). |

## 4. Error Path

- **Already maxed** — `PurchaseUpgrade` returns false; VM displays
  "Already at max level". No side effects.
- **Insufficient balance** — returns false; VM displays "Not enough
  Steps". No side effects.
- **`adjustStepBalance` throws** — propagates to the `viewModelScope`
  default handler; `_processing` is released via `finally`. User
  sees the button become tappable again; no error message. In
  practice Room calls rarely throw.
- **`setUpgradeLevel` throws** — `spendSteps` has already succeeded;
  the player has lost Steps without gaining the level. **There is no
  compensating refund.** See §9.
- **`getByDateOnce(today)` throws** — swallowed; mission progress
  simply doesn't advance this tick.
- **VM cleared mid-purchase** — `viewModelScope` cancels. Any
  suspension point aborts. If `spendSteps` suspended first, the
  balance may or may not have been deducted depending on where
  cancellation lands.

## 5. Performance Characteristics

- `purchase()` invocation: 1 DAO read (`wallet.first()`) + 2 atomic
  updates + optional 1 read + 1 update for missions. < 10 ms total.
- `combine` re-computation: iterates 23 upgrade entries and builds
  23 `UpgradeDisplayInfo` instances. ~1 ms.
- Cost calc: `baseCost * (scaling ^ level)` — one `Math.pow` call
  per upgrade. Constant per emit.
- `ResolveStats.invoke(upgrades)` runs on every emit; iterates the
  23 upgrades to produce `ResolvedStats`. Used only to populate the
  "X dmg / Y/s" text on the cards. Computational waste if the user
  is idle on Workshop; `WhileSubscribed(5000)` mitigates when
  navigated away.
- Notification: zero — Workshop purchases fire no notifications.

## 6. Observable Effects

- **Room `player_profile.id=1`**: `currentStepBalance -= cost`,
  `totalStepsEarned` unchanged (cost is negative; the CASE branch in
  the SQL skips increment).
- **Room `workshop_upgrade` table**: upgrade row's `level` advances
  by 1 (upsert).
- **Flow cascade**:
  1. `WorkshopDao.getAll()` re-emits → `WorkshopRepository.observeAllUpgrades` → `combine` → `uiState`.
  2. `PlayerProfileDao.get()` re-emits → `PlayerRepository.observeWallet` → `combine` → `uiState`.
  3. All other VMs observing wallet (Home, Labs, Cards, Store,
     Economy) recompute on their next `WhileSubscribed(5000)`
     cycle.
- **UI**: the `UpgradeCard` shifts visual state (level label
  increments, cost label updates, potentially `canAfford=false`
  now dimming the Buy button).
- **Processing indicator**: `isProcessing` flips true then false.
  Current UI doesn't show a spinner but could.
- **Mission progress**: may advance `SPEND_5000_WORKSHOP` mission.

## 7. Why This Design

- **Reactive by default.** Two independent Flow sources (wallet,
  upgrades) with `combine` is the canonical pattern. Any other
  screen that cares about either will see this update automatically.
- **Cost formula in one pure function**
  (`CalculateUpgradeCost(type, level)`) — easy to test, no
  Android dependencies, shared with Quick Invest and SmartReminder.
- **`purchaseUpgrade(type, level, wallet)` is an explicit parameter
  list** rather than reading wallet internally, because the VM has
  already observed it via `combine`. Passing it down avoids a
  duplicate read… except the VM then does a `playerRepository.observeWallet().first()`
  inside `purchase()` to get a fresh read. Defensive against the
  race between observing and spending.
- **Debounce** via `_processing` prevents the user from double-tapping
  and double-spending. (But see §9 — not 100% tight.)
- **No domain-level write transaction** because `adjustStepBalance(-cost)`
  clamps at 0, so the second spend would simply be capped. The worst
  case is: user has `cost`, two simultaneous purchases; first one
  succeeds (balance=0, level=L+1), second one would succeed at use-case
  level (wallet read before first deduct), then atomic SQL clamps to 0
  (no negative), but `setUpgradeLevel` still succeeds. Double-level-up
  with single spend. This is not prevented today.
- **Mission progress** tracked inline because only the VM knows the
  cost after the fact. Could be a callback inside `PurchaseUpgrade`
  but that would couple the use case to mission tracking.

## 8. Feels Incomplete

- **No success feedback.** A purchase completes silently — cost
  disappears from balance, card levels up. Some games would flash
  the card or play a sound. The audio play happens only for
  *in-round* purchases (`SoundManager.play(UPGRADE_PURCHASE)`), not
  for Workshop.
- **`statValueFor(type, stats)`** returns `""` for 5 upgrade types
  (e.g. `FREE_UPGRADES`, `CASH_PER_WAVE`, `INTEREST`). These cards
  therefore show no stat text. Not technically incomplete, but
  feels inconsistent.
- **`hiddenUpgrades`** is `setOf(STEP_MULTIPLIER, RECOVERY_PACKAGES)`.
  No explanation of why these are hidden. Check plan docs to find
  out; likely deferred features.
- **`WorkshopDao.getAll()` returns every upgrade type.** If a new
  upgrade type were added in code but not seeded in DB, its row
  would be missing. `workshopRepository.ensureUpgradesExist()` is
  called in `HomeViewModel.init` but that's the *Home* VM — if the
  user deep-links straight into Workshop on first launch, they may
  miss the seeding.

## 9. Feels Vulnerable

- **Non-atomic spend + level-up.** If the user taps Buy, the
  coroutine suspends on `spendSteps`, and the process is killed
  before `setUpgradeLevel` runs, the player's balance is down but
  the level didn't advance. No compensating transaction. Users
  would rarely hit this (process kill is rare mid-tap) but not
  impossible. A Room `@Transaction` suspend function wrapping both
  writes would fix it.
- **Debounce race.** `if (_processing.value) return` followed by
  `_processing.value = true` is not atomic. Two `viewModelScope.launch`
  invocations on the Main dispatcher — possible if Compose dispatches
  two onClick events rapidly — could both read `false` and both
  enter the purchase. Fixing this cleanly needs
  `_processing.compareAndSet(false, true)` or a `Mutex`.
- **Map-serialised `bestWavePerTier` is read-modify-write.** (Not in
  this trace directly, but `PlayerRepositoryImpl.updateBestWave` does
  the same pattern elsewhere — see trace 08.)
- **`observeWallet().first()`** inside `purchase()` could race with
  another VM's update. E.g. user is walking during a Workshop
  purchase tap — the sensor ingestion might add Steps between the
  VM's `first()` and the use case's affordability check, so the user
  could legitimately afford a purchase they thought was unaffordable
  when the check started. Minor; resolves itself on the next render.
- **MissionDao updates can run while the daily-mission "new day"
  refresh logic is regenerating missions** (from `HomeViewModel`).
  No locking.

## 10. Feels Like Bad Design

- **`WorkshopViewModel` inlines 8+ use-case behaviours** into its
  `purchase`/`quickInvest`/`combine` — cost calc, affordability,
  stat resolution, mission progress, user-message dispatch. A
  `WorkshopInteractor` that produces `WorkshopUiState` and handles
  actions would separate concerns.
- **`statValueFor(type, stats)`** is a 18-case `when` inside the VM.
  Should be a method on `UpgradeType` or a lookup table. Placing it
  in the VM ties "how upgrades display" to a specific screen; if
  SmartReminder wanted to show stats too, duplication would emerge.
- **Two paths for cost display**: `if (isMaxed) 0L else calculateCost(type, level)`
  and the `UpgradeCard` then interprets `cost=0L && isMaxed`. A
  sealed class `UpgradeState.Maxed / .Affordable(cost) / .Expensive(cost)` would
  be clearer.
- **`purchaseUpgrade(type, level, wallet)` parameter** re-reads
  wallet immediately before calling. The `wallet` passed in is
  redundant. Either trust `combine` or always read inside the use
  case — current code does both.
- **Quick Invest uses the same `purchaseUpgrade` without updating
  the mission counter** — `quickInvest()` body calls
  `purchaseUpgrade(target, level, wallet)` and moves on. Intentional?
  Unclear. The `SPEND_5000_WORKSHOP` mission would likely want to
  count those too.
- **`_userMessage: MutableStateFlow<String?>`** is a one-slot
  message channel. If two messages fire in quick succession the
  earlier is lost. `SharedFlow<String>` with a replay buffer would
  be sturdier.
