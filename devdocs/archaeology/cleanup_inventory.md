# Codebase Cleanup Inventory (Phase 13)

**Purpose.** Identify candidates for removal, consolidation, or quarantine across the repo. **No file is deleted or modified here** — this document only names things, with evidence, confidence, risk, a verification step, and a suggested action per item.

**Scope.** Repository at `HEAD = a9d0386` on `main`, tree clean apart from the usual modified `docs/agent/STATE.md` / `RUN_LOG.md` and untracked `devdocs/` + `smoke_tests/`. Schema v8, 412 JVM tests, release APK builds 26 MB.

**Authority.** Code behaviour is authoritative per global rule #1. Where docs disagree with code, code is cited and the doc drift is flagged.

**Relationship to prior archaeology.**
- Phase 1 `small_summary.md`, Phase 2 `intro2codebase.md` + `intro2deployment.md`, Phase 3 `traces/*` (13 traces), Phase 4 `5_things_or_not.md`, Phase 5 `concepts/*`, Phase 6/7 `foundations/*`, Phase 8 `architecture_analysis.md` + `module_discovery.md`, Phase 9 `concept_mappings.md`, Phase 10 `evolution/gap_analysis.md`, Phase 11 `evolution/gap_closure_plan.md`, Phase 12 `smoke_tests/check_what_is_working/`.
- This inventory **synthesises and cross-references** prior findings rather than duplicating them. Each entry cites the original phase + code file where possible.

**How to read each entry.**

| Field | Meaning |
|---|---|
| **Path / symbol** | File path, class, or symbol identifier |
| **Evidence** | What makes this a candidate (grep counts, file:line, callers) |
| **Confidence** | `High` (verified unreachable / duplicate), `Medium` (likely dead but could be framework-hooked), `Low` (looks suspicious, needs investigation) |
| **Risk level** | If the suggested action were taken: `Low` = local change, `Medium` = touches shared surface, `High` = touches dynamic/framework boundary |
| **Verification step** | The minimum checks to run before acting (not this session) |
| **Action** | `remove`, `consolidate`, `quarantine`, `document`, `leave alone` |

**Legend (statuses deliberately conservative).**
- `remove` — candidate for deletion after verification; evidence of no runtime path.
- `consolidate` — duplicate or near-duplicate; merge into a single source.
- `quarantine` — move behind a feature flag / comment annotation because deletion is risky.
- `document` — keep, but add an explicit comment explaining why it exists.
- `leave alone` — named only because prior archaeology flagged it; net-positive to keep.

---

## TL;DR — Top candidates

Grouped by rough effort and confidence. Full detail with evidence / risk / verification appears below.

### Pure removals (High confidence, Low risk)

1. **`presentation/MainActivity.kt:237` `PlaceholderScreen` composable** — no callers, dead since Plan 06 replaced the placeholders.
2. **`domain/model/UltimateWeaponLoadout.kt`** — declared, tested, **never instantiated in main source**. `OwnedWeapon.equipped: Boolean` on each entity is the de-facto runtime loadout; class + test file together are unreachable data.
3. **`docs/plans/plan-05-google-fit.md`** (already renamed; **verify no lingering references**) — historical plan filename. Duplicate present in `.kiro/steering/source-files.md` (file still lists v7 for Database).

### Consolidations (Medium confidence, Low/Medium risk)

4. **`StepRepositoryImpl.releaseEscrow` / `discardEscrow` (lines 44 & 46)** — both delegate to `dao.clearEscrow(date)`; the semantic split lives only in the caller (`StepCrossValidator`). Either rename to a single `clearEscrow()` or split the DAO query so each method has real work to do.
5. **`StepCrossValidator.kt` Level 0 + Level 1 branches (validate(), ~lines 67–96)** — ~20 lines duplicated; only `MAX_ESCROW_SYNCS` differs (3 vs 2). Extract a single `applyEscrow(maxSyncs: Int)` helper.
6. **`data/BiomePreferences.kt` / `NotificationPreferences.kt` / `SoundPreferences.kt` / `MilestoneNotificationPreferences.kt` / `AntiCheatPreferences.kt` / `StepIngestionPreferences.kt` + `data/anticheat/AntiCheatPreferences.kt`** — 6 distinct `SharedPreferences` wrappers, all with the same `context.getSharedPreferences("…", MODE_PRIVATE)` shape. Phase 8 `module_discovery.md` §4.M8 named this the "virtual `prefs` module". Consolidate behind a single `PreferencesStore` or keep as separate files with a documented naming convention — either way, name the decision.
7. **`presentation/battle/engine/GameEngine.kt` `preOverdriveStats` + `preGoldenStats` snapshot pair** — two separate stat snapshots with implicit restore order (Phase 8 §4). Replace with a stack or a tagged snapshot so restore order is explicit.

### Quarantine (Medium confidence, Medium risk — deletion would remove a future feature)

8. **`domain/model/SupplyDropTrigger.STEP_BURST` enum entry** — declared at line 5 of `SupplyDropTrigger.kt` **but never produced by `GenerateSupplyDrop` or anywhere else**. Phase 10 §1.4 and Phase 11 Q6 list two equally-valid options: (a) wire it via a step-velocity trigger or (b) delete it with a documented intent. Do not touch without an explicit product decision.
9. **`domain/model/UpgradeType.STEP_MULTIPLIER` + `UpgradeType.RECOVERY_PACKAGES`** — hidden from the Workshop UI via `WorkshopViewModel.kt:42 hiddenUpgrades = setOf(…)` (R04 remediation) but the **enum entries + `UpgradeConfig` entries at `UpgradeType.kt:30,31,62,63` are still live** and **still exercised by `balance/CostCurveTest.kt:17,45`**. Any removal must update `CostCurveTest`, `hiddenUpgrades`, and `battle-formulas.md`'s STEP_MULTIPLIER note.
10. **`domain/model/MilestoneReward.Cosmetic` branch of the sealed class** — declared and referenced by 3 milestone entries (`Milestone.kt:19,24,28`) but **silently no-op'd at `ClaimMilestone.kt:25`** (`is MilestoneReward.Cosmetic -> { /* no-op until cosmetics system exists */ }`). Worse, the 3 cosmetic IDs ("garden_ziggurat_skin", "lapis_lazuli_skin", "sandals_of_gilgamesh") **do not match any `cosmeticId` in `CosmeticRepositoryImpl.SEED_COSMETICS` (zig_obsidian, zig_crystal, zig_golden, proj_fire, proj_lightning, enemy_shadow, enemy_neon)** — so even if the no-op were replaced with a real `addCosmetic`, it would reference non-existent rows. Requires a product decision before any change.

### Documentation / naming (Low risk)

11. **`di/HealthConnectModule.kt`** — 13-line file, entire body is `object HealthConnectModule` with `@InstallIn(SingletonComponent::class)` and no members. The enclosing docstring says "organizational placeholder". Either delete (Hilt will still auto-wire HC classes via `@Inject constructor`) or rename to make the "organizational" intent visible in one of the directory README / `source-files.md`.
12. **`presentation/navigation/Screen.kt` `items by lazy`** — a documented workaround for a sealed-class init-order NPE (commit `1872af9`). Add a one-line comment on the line itself so the next reader doesn't "simplify" it away.
13. **Stale doc references in `.kiro/steering/source-files.md` and `docs/database-schema.md`** — still claim schema v7 / omit `battleStepsEarned`. Phase 2, 5, 6, 7, 9 delta sections already flagged this.

### Build/test artefacts

14. **`app/schemas/*/1.json` through `6.json`** — 6 pre-release schema snapshots; only `MIGRATION_7_8` is registered in `AppMigrations.ALL`. Keep but document that they are historical artefacts (Room's schema exporter preserves them, and deleting them risks breaking future Room schema validation assumptions).
15. **`app/src/test/…/RoomSchemaTest.kt`, `StepWidgetProviderTest.kt`, `DeepLinkRoutingTest.kt`** — all three use `@RunWith(RobolectricTestRunner::class)` + `org.junit.Test` (JUnit 4). **`junit-vintage-engine` is not on the classpath**, so these ~9 tests are silently not discovered (see Phase 12 smoke report, §E1 below).

### Leave alone (named for transparency)

16. **`data/local/DailyStepRecordEntity.healthConnectSteps` field** — old rename carrier from Plan 05 (was `googleFitSteps`). Still used.
17. **`domain/model/CardLoadout.kt`** — *not* dead. Used by `ManageCardLoadout` usecase and `CardsViewModel` at runtime.
18. **`data/repository/*Impl.kt` `toDomain()` private extension pattern** — 6 repositories all have an identical `private fun EntityType.toDomain() = DomainType(…)` pattern. Convention; not duplication.

---

## DYNAMIC-RISK CAUTION BOX (read before acting on any candidate below)

Several frameworks in this project resolve types **dynamically**, at class-graph load time, through annotation processors, manifest lookups, or reflection. A class that appears "unreferenced" by a text/import grep may still be wired at runtime by one of these mechanisms. Before removing **anything**, confirm it isn't a party to the following:

| Mechanism | Where it matters |
|---|---|
| **Hilt (KSP codegen)** | Any `@Inject constructor`, `@HiltViewModel`, `@HiltWorker`, `@HiltAndroidApp`, `@AndroidEntryPoint`, `@Module` / `@InstallIn`, `@Provides`, `@Binds`. Grep counts 239 annotation occurrences across 79 files. Removing a provider silently causes `MissingBinding` at app start. |
| **Room (KSP codegen)** | `@Database`, `@Entity`, `@Dao`, `@TypeConverter`, `@Migration`. Entity field names become column names; renaming or removing a field without a `Migration` crashes at DB open. `@Database(version = N)` and `AppMigrations.ALL` must stay in sync with `app/schemas/N.json`. |
| **WorkManager + HiltWorkerFactory** | `StepSyncWorker` is a `@HiltWorker` referenced by `WorkRequest.Builder<StepSyncWorker>` in `StepSyncScheduler.kt`. Class name is also serialized into the `WorkDatabase` — renaming breaks already-scheduled periodic work on existing installs. |
| **AndroidManifest references** | `.StepsOfBabylonApp`, `.presentation.MainActivity`, `.presentation.HealthConnectPermissionActivity` (+ `ViewPermissionUsageActivity` alias), `.service.StepCounterService`, `.service.BootReceiver`, `.service.StepWidgetProvider`. Removal must be accompanied by a manifest update and an R8 keep-rule audit. |
| **Compose Navigation routes** | `Screen.kt` sealed subclasses are referenced by string route in `MainActivity` NavHost. A route string must not be renamed without updating every `navigate("…")` call site and deep-link intent-extras collector. |
| **Notification PendingIntents + deep-links** | `MainActivity.onNewIntent` + `pendingNavigation: MutableStateFlow<String?>` consume `navigate_to` intent extras sent by `StepNotificationManager`, `SupplyDropNotificationManager`, `MilestoneNotificationManager`, `SmartReminderManager`. Changing any of the string literals requires editing every producer. |
| **AppWidgetProvider** | `StepWidgetProvider` is referenced by `res/xml/step_widget_info.xml` (manifest `<meta-data android:resource="@xml/step_widget_info">`). Widget state persists across app reinstalls via the system's widget host. |
| **BootReceiver** | Only way the foreground service restarts after device boot; removal breaks step counting after reboot. |
| **Reflection** | **No `UUID.randomUUID()` calls, no `BuildConfig` reads, no `Class.forName` reflection detected in `app/src/main`.** But Health Connect SDK uses reflection internally — hence `proguard-rules.pro` has `-keep class androidx.health.connect.** { *; }`. |
| **Native library load** | `System.loadLibrary("sqlcipher")` in the SQLCipher runtime. Not removable without dropping SQLCipher encryption. |
| **Enums stored as strings in Room** | `UpgradeType.name`, `CardType.name`, `UltimateWeaponType.name`, `CosmeticCategory.name`, `SupplyDropTrigger.name`, `SupplyDropReward.name` — stored as raw strings in their respective entities. Deleting or renaming an enum value can make existing DB rows unloadable. Any enum edit therefore **requires a data migration** (not just a schema migration). R8 keep-rule `-keep enum com.whitefang.stepsofbabylon.domain.model.** { *; }` pins the class names but does not pin the ordinals or name spellings. |
| **ProGuard / R8 keep rules** | See `app/proguard-rules.pro`. Removing a `@Entity`, `@Dao`, `@HiltWorker`, or domain enum is mostly safe at the rule level, but removing a keep target (e.g. `org.json.**` if Converters stop using JSON) needs matching rule cleanup. |

**Rule of thumb:** any candidate whose risk column below says `High` lives under one of these mechanisms. For those, the verification step lists the specific framework to re-check before acting.

---

*(Sections A–F follow below with full per-candidate detail. Dynamic-risk register in §G.)*


## A. Source tree — main Kotlin

### A1. `PlaceholderScreen` dead composable

- **Path / symbol**: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt:237–242`, `private fun PlaceholderScreen(name: String)`.
- **Evidence**: `grep -n "PlaceholderScreen" app/src/main` returns exactly one match (the declaration). Zero call sites. Every NavHost `composable { … }` block now resolves to a real `*Screen(…)` function. Documented in Phase 1 `small_summary.md` §"Incomplete areas", Phase 3 `trace_05_compose_to_surfaceview_boot.md` §"Feels Incomplete", Phase 5 `missing_concepts_list.md`, Phase 10 §4, Phase 11 Q7.
- **Confidence**: **High** — function is `private`, single file, no reflection reachable.
- **Risk level**: **Low** — pure Compose function, no DI, no manifest reference.
- **Verification step**: `./run-gradle.sh testDebugUnitTest` (should remain 412 green) + `assembleDebug` (APK must still build; function is unused so no change expected).
- **Action**: **remove**. Add a one-line commit note citing Phase 11 Q7.

### A2. `UltimateWeaponLoadout` unused at runtime

- **Path / symbol**: `domain/model/UltimateWeaponLoadout.kt` (the whole file).
- **Evidence**: `grep UltimateWeaponLoadout app/src/main` returns zero matches in `main` after the declaration itself. The **test file** `test/domain/model/UltimateWeaponLoadoutTest.kt` is the only consumer (6 occurrences). Runtime UW loadout is tracked per-entity via `UltimateWeaponStateEntity.equipped: Boolean`, surfaced by `UltimateWeaponRepository.observeEquippedWeapons()`, validated ad-hoc in `UltimateWeaponViewModel.kt:82` with a hard-coded `3`. Confirmed in Phase 8 §4 ("CardLoadout and UltimateWeaponLoadout are near-identical, neither exercised at runtime" — first half updated here: `CardLoadout` **is** used via `ManageCardLoadout`; `UltimateWeaponLoadout` genuinely is not).
- **Confidence**: **High** (main source). **Medium** for the test file — it becomes orphaned if the class is removed.
- **Risk level**: **Low** — pure Kotlin domain class, no Hilt, no Room.
- **Verification step**: `grep -r UltimateWeaponLoadout app/src/main` returns no hits; `./run-gradle.sh testDebugUnitTest` (`UltimateWeaponLoadoutTest.kt` must be removed in the same PR or converted into an invariant test on the `equippedCount <= 3` check the VM already performs).
- **Action**: **remove** (both the class and its test), OR **quarantine with a comment** if product wants a typed loadout later. Do not leave as-is — the test currently passes but covers nothing the runtime does.

### A3. `StepRepositoryImpl.releaseEscrow` vs `discardEscrow` (identical bodies)

- **Path / symbol**: `data/repository/StepRepositoryImpl.kt:44,46`.
- **Evidence**: Both methods are one-liners, `= dao.clearEscrow(date)`. The semantic difference lives entirely in the call site (`StepCrossValidator.kt:76,92` = discard on offense; `StepCrossValidator.kt:106` = release on reconciliation). The `StepRepository` interface declares both as separate members (`StepRepository.kt:14,15`). `FakeStepRepository.kt:41,49` preserves the split. Flagged in Phase 4 item 2 ("Room @Transaction"), Phase 8 §4, Phase 3 `trace_03_hc_cross_validation_escrow.md`.
- **Confidence**: **High**.
- **Risk level**: **Medium** — changing the interface touches the fake and validator. The duplication is **behaviourally correct today** because both operations currently mean "zero-out `escrowSteps` and `escrowSyncCount`"; the only reason to keep two names is to preserve future divergence (e.g. discard writes an offense counter).
- **Verification step**: Decision first: should release and discard ever differ? If no, collapse both call paths to `clearEscrow`. If yes, spell the divergence out in the implementation rather than the caller.
- **Action**: **consolidate** (merge into `clearEscrow` and update two callers) **or** **document** (leave and add a comment on both methods explaining why). Pair with the @Transaction work in Phase 4 item 2 / Phase 11 I2 to avoid double-touching the file.

### A4. `StepCrossValidator` Level 0 / Level 1 duplication

- **Path / symbol**: `data/healthconnect/StepCrossValidator.kt`, `validate()` method, branches around lines 67–96 (Level 1) and 98–119 (Level 0).
- **Evidence**: Read the two `else if` branches: they are character-identical except `MAX_ESCROW_SYNCS_LEVEL1` (2) vs `MAX_ESCROW_SYNCS_DEFAULT` (3). Phase 8 `architecture_analysis.md` §"What doesn't make sense" item on this file.
- **Confidence**: **High**.
- **Risk level**: **Low** — internal refactor within one function, tests cover all 4 offense levels (`StepCrossValidatorTest`).
- **Verification step**: `./run-gradle.sh testDebugUnitTest --tests '*StepCrossValidatorTest'` — 10 tests must stay green.
- **Action**: **consolidate** into `applyEscrow(maxSyncs: Int, offenseLabel: String)`.

### A5. `GameEngine` stat-snapshot pair (implicit restore order)

- **Path / symbol**: `presentation/battle/engine/GameEngine.kt` — `preOverdriveStats`, `preGoldenStats` fields + `expireOverdrive` / Golden Ziggurat restore paths.
- **Evidence**: Phase 8 §4 flagged this; two separate `ResolvedStats?` snapshots with implicit restore order. If Golden Ziggurat expires while Overdrive is active, restore order matters and is currently non-obvious. No test covers the interaction.
- **Confidence**: **Medium** — code is correct *today* because of specific activation timings, but the class has grown and this is a trap for future additions.
- **Risk level**: **Medium** — central battle loop, `@Volatile` cross-thread state, any mistake reverts stats mid-combat.
- **Verification step**: Write a `BattleViewModelTest` scenario that activates Overdrive then Golden Ziggurat then expires both in reverse order; assert stat restoration matches spec. Only after the test passes, refactor to a `Deque<ResolvedStats>` or a named-snapshot map.
- **Action**: **consolidate** (replace two fields with a stack/map). Not urgent. Pair with Phase 4 item 3 (round-end cascade) when it's picked up.

### A6. `di/HealthConnectModule.kt` empty placeholder

- **Path / symbol**: entire file, 13 lines.
- **Evidence**:
  ```kotlin
  @Module
  @InstallIn(SingletonComponent::class)
  object HealthConnectModule
  ```
  No `@Provides`, no `@Binds`, no members. Hilt auto-wires every HC class via `@Inject constructor` directly; the module adds nothing. Phase 8 `architecture_analysis.md` §"What doesn't make sense" and Phase 2 `intro2codebase.md` §5 both flagged this.
- **Confidence**: **High**.
- **Risk level**: **Low** — Hilt resolves `@InstallIn` modules by class list at KSP time; removing an empty object does not break wiring.
- **Verification step**: `./run-gradle.sh assembleDebug` must still build and Hilt generation must still produce the same binding set. Tip: `./run-gradle.sh :app:kspDebugKotlin --rerun-tasks` and diff `app/build/generated/ksp/debug/java/com/whitefang/stepsofbabylon/di/` before/after.
- **Action**: **remove** (simplest) **or** **document** with a comment saying "reserved for future HC providers; kept as an organisational anchor".

### A7. `presentation/navigation/Screen.kt` `items by lazy` workaround

- **Path / symbol**: `Screen.kt` companion object, `val items by lazy { … }`.
- **Evidence**: Phase 8 `architecture_analysis.md` named commit `1872af9` as the fix-up for a sealed-class init-order NPE when `Screen.items` was `val` (not lazy). The workaround is correct but non-obvious.
- **Confidence**: **High** — needs to stay.
- **Risk level**: **Low** — just a comment add.
- **Verification step**: none needed for a comment.
- **Action**: **document** — add a one-line comment `// lazy to avoid sealed-class init-order NPE; see commit 1872af9`.

### A8. 6 `SharedPreferences` wrappers in `data/` (virtual `prefs` module)

- **Paths**:
  1. `data/BiomePreferences.kt`
  2. `data/NotificationPreferences.kt`
  3. `data/SoundPreferences.kt`
  4. `data/MilestoneNotificationPreferences.kt`
  5. `data/anticheat/AntiCheatPreferences.kt`
  6. `data/sensor/StepIngestionPreferences.kt`
  (Phase 8 called this the virtual `M8 prefs` module.)
- **Evidence**: Each file opens `context.getSharedPreferences("<name>", MODE_PRIVATE)` and exposes a small set of typed getters/setters. No shared base class. Different naming conventions across files. `GameSurfaceView.kt:26` even bypasses `SoundPreferences` and reads `sound_prefs` inline (Phase 8 §"What doesn't make sense").
- **Confidence**: **Medium** — each individual file is reachable; the duplication is the issue.
- **Risk level**: **Medium** — consolidation must preserve the stored preference keys byte-for-byte to avoid losing state on upgrade (these are the closest thing the game has to user configuration that survives across reinstalls via Android's Auto Backup, which is *disabled* in this app — so only across launches).
- **Verification step**: Enumerate every `.getSharedPreferences(` call across `main` and each wrapper's key constants; collect into a migration table; verify R8 / launcher behaviour on a device that already has the v1.0 build installed.
- **Action**: **consolidate** behind a single `PreferencesStore` API that exposes the same keys, **or** **document** the "one wrapper per concern" convention and fix the `GameSurfaceView.kt:26` leak. The decision is named in Phase 10 §2 and Phase 11 I7; deliberately unscheduled.

### A9. `TrackDailyLogin` Season-Pass leak from `DailyStepManager`

- **Path / symbol**: call site in `data/sensor/DailyStepManager.kt` (via `trackDailyLogin`), use case in `domain/usecase/TrackDailyLogin.kt`.
- **Evidence**: `TrackDailyLogin` has `seasonPassActive`/`seasonPassExpiry` params that grant +10 Gems/day on login. The call path from `DailyStepManager` never passes these flags, so the walking-streak Gems path loses the +10 Gems bonus for paying customers. Phase 8 `architecture_analysis.md` and Phase 11 Q8 documented this.
- **Confidence**: **High** (Phase 8 traced it with file:line).
- **Risk level**: **Low** — wire two additional params through the existing call chain.
- **Verification step**: unit test `TrackDailyLoginTest` already covers both the `seasonPassActive=true` and `=false` branches; add one that exercises the DailyStepManager path.
- **Action**: **document** for now (named as a bug, not a cleanup) + cross-link to Phase 11 Q8. Not a deletion candidate; included here because the path's silent divergence is the kind of thing a future cleaner might mistake for dead code.

### A10. `DailyStepManager.runFollowOnPipeline` catch-all blocks

- **Path / symbol**: `data/sensor/DailyStepManager.kt`, `runFollowOnPipeline()` method (5 stages, each in `try { … } catch (_: Exception) { }`).
- **Evidence**: Phase 8 §4 and Phase 4 item 4 both flagged the pattern ("4 pokemon-catch blocks"). R2-07 added `Log.w` to `StepSyncWorker` silent catches but not here.
- **Confidence**: **High**.
- **Risk level**: **Medium** — the pipeline runs on every step credit, including the 200 ms hot path. Changes that log or rethrow can create notification storms.
- **Verification step**: Run `DailyStepManagerTest` after adding per-stage logging.
- **Action**: **document** / minor **consolidate** — add per-stage `Log.w(TAG, "…", e)` matching the R2-07 pattern. Leaves the swallow-to-continue behaviour but makes silent failures observable. Named in Phase 11 I1 (anti-cheat visibility) but equivalently applies here.

### A11. `CosmeticEntity` double key

- **Path / symbol**: `data/local/CosmeticEntity.kt` — `@PrimaryKey(autoGenerate = true) id: Long` AND `cosmeticId: String` (used as logical lookup key in `CosmeticDao`, `ClaimMilestone` references).
- **Evidence**: Phase 8 `architecture_analysis.md` §4 first named this. `CosmeticRepositoryImpl.kt:30` looks up by `cosmeticId`, not by `id`.
- **Confidence**: **High**.
- **Risk level**: **Medium** — requires a Room migration (ALTER PRIMARY KEY is a table-recreate in SQLite).
- **Verification step**: write the v8→v9 migration, re-export the schema, run `RoomSchemaTest` (fix its vintage-engine discovery first — see E1).
- **Action**: **leave alone** until the cosmetic rendering pipeline work (Phase 11 MR1) is picked up; consolidate the PK then. Named here so a future cleaner doesn't delete one of the two keys without realising both are load-bearing.


## B. Abandoned features / orphan enum entries

These are code paths that **type-check, compile, and pass tests** but are not actually reached at runtime. They are *not* dead code in the simple sense (removing them would change the external surface of domain enums stored in Room, or revert a designed-for-future contract). Handle each with a product decision first.

### B1. `SupplyDropTrigger.STEP_BURST` — enum value never produced

- **Path / symbol**: `domain/model/SupplyDropTrigger.kt:5`.
  ```kotlin
  STEP_BURST("Your pace is impressive! An energy surge flows into your ziggurat."),
  ```
- **Evidence**: `grep STEP_BURST app/src` returns exactly 1 match — the declaration itself. `GenerateSupplyDrop.kt` produces `MILESTONE`, `THRESHOLD`, `RANDOM` only. Plan 19 RUN_LOG entry says "Step burst deferred" and Plan 25 notes "Step burst trigger for supply drops still deferred". Phase 5 `missing_concepts_list.md`, Phase 9 `concept_mappings.md` §Supply Drops, Phase 10 §1.4, Phase 11 Q6 all flagged it.
- **Confidence**: **High** (orphan value).
- **Risk level**: **Medium — DYNAMIC**. The enum is stored in `WalkingEncounterEntity.triggerType` as a string (`.name`). Any row ever persisted with `triggerType = "STEP_BURST"` would become unloadable if the value is deleted. *Today* no such row exists, but any product change that starts producing it and then reverts would create orphaned DB rows.
- **Verification step**: Decision first. If **delete**: `grep -r STEP_BURST` across `app/src` and `docs/` (the GDD + plan 19 reference it narratively) — update narrative text in sync. If **wire**: add a velocity-based producer in `GenerateSupplyDrop` and a `DropGeneratorState.lastBurstCheck` field.
- **Action**: **quarantine** — named in Phase 11 Q6 as "defaults to delete-with-documented-intent" but pending product choice. Do not touch without product input.

### B2. `UpgradeType.STEP_MULTIPLIER` + `RECOVERY_PACKAGES` — hidden from UI but still configured + tested

- **Paths / symbols**:
  - `domain/model/UpgradeType.kt:30–31` (enum entries) + lines `62–63` (`UpgradeConfig` entries inside `UPGRADE_CONFIGS`).
  - `presentation/workshop/WorkshopViewModel.kt:42` — `private val hiddenUpgrades = setOf(UpgradeType.STEP_MULTIPLIER, UpgradeType.RECOVERY_PACKAGES)`.
  - `balance/CostCurveTest.kt:17,45` — still exercises `STEP_MULTIPLIER` in the tested upgrade list and the 10-level cost-curve sum.
- **Evidence**: Both are declared, configured, and tested, but filtered out of the workshop UI by R04 (remediation 1, March 2026). Plan R's note: "Enum entries preserved for future implementation." The `RECOVERY_PACKAGES` effect is not implemented anywhere in `GameEngine`; `STEP_MULTIPLIER` is not read in the step pipeline. `docs/battle-formulas.md` has a "hidden" note for `STEP_MULTIPLIER` added in the March 2026 documentation sweep.
- **Confidence**: **High** (hidden from UI; never consumed at runtime).
- **Risk level**: **High — DYNAMIC**. Same enum-stored-as-string risk as B1 — plus the `WorkshopUpgradeEntity` table persists a row per `UpgradeType.name` via the seed path in `WorkshopRepositoryImpl.ensureUpgradesExist()`. Removing an enum value without a Room migration will leave orphan rows that can't be mapped back to the enum.
- **Verification step**: (a) Inventory any `WorkshopUpgradeEntity` rows with `upgradeType IN ('STEP_MULTIPLIER', 'RECOVERY_PACKAGES')` — these currently exist in every installed DB because of the `ensureUpgradesExist` seed. (b) Either wire the effects (Phase 11 doesn't schedule it) or delete the enum entries + ship a v8→v9 migration that DROPs the two rows.
- **Action**: **quarantine** — leave until product confirms which side of the fence these go on. Update `docs/battle-formulas.md` + `.kiro/steering/source-files.md` if touched.

### B3. `MilestoneReward.Cosmetic` sealed branch — no-op + wrong IDs

- **Path / symbol**: `domain/model/MilestoneReward.kt` (sealed class declaration); `domain/usecase/ClaimMilestone.kt:25` (`is MilestoneReward.Cosmetic -> { /* no-op until cosmetics system exists */ }`); producers `Milestone.kt:19,24,28`.
- **Evidence**: The sealed branch is declared and produced (3 `Cosmetic(…)` entries on 3 milestones) but the reward crediting is a no-op comment. Additionally, the 3 cosmetic IDs used in `Milestone.kt` **do not match any `cosmeticId` in `CosmeticRepositoryImpl.SEED_COSMETICS`** — `Milestone.kt` names `garden_ziggurat_skin`, `lapis_lazuli_skin`, `sandals_of_gilgamesh`; `CosmeticRepositoryImpl.kt:54–60` seeds `zig_obsidian`, `zig_crystal`, `zig_golden`, `proj_fire`, `proj_lightning`, `enemy_shadow`, `enemy_neon`. Confirmed by Phase 8 `architecture_analysis.md` §4, Phase 10 §4, Phase 11 I6.
- **Confidence**: **High** — both the no-op and the ID mismatch are explicit.
- **Risk level**: **Medium — DYNAMIC**. Changing any cosmetic ID requires a product decision about which cosmetics actually exist. The milestones themselves are stored in Room only by `.name` (no cosmetic-ID reference is persisted), so the IDs are a **code-only** contract today.
- **Verification step**: Product decision — (a) do the 3 Marathon-Walker/Iron-Soles/Globe-Trotter cosmetics exist? (b) If yes, add them to `SEED_COSMETICS` under the IDs `Milestone.kt` already names; if no, change the IDs in `Milestone.kt` to reference the 7 existing cosmetics. (c) Replace the `ClaimMilestone.kt:25` no-op with a real `cosmeticRepository.markOwned(reward.id)`.
- **Action**: **quarantine** — named explicitly as a "partial system" in Phase 9 coverage roll-up. Do not delete the no-op line, do not rename the IDs, until the cosmetic rendering pipeline MR1 is scheduled.

### B4. Cosmetic store — purchasable UI disabled ("Coming Soon")

- **Path / symbol**: `presentation/store/StoreScreen.kt:113`, `"Cosmetic visuals are being finalized. Purchases are disabled until ready."`; `CosmeticRepositoryImpl.kt` seeds 7 items on first access; R2-11 remediation disabled the purchase buttons.
- **Evidence**: Entire cosmetic purchase/equip/visual pipeline exists but is user-invisible. STATE.md known-issues line: "Cosmetic visual application not implemented (purchases disabled via R2-11 guard)." Phase 9 names this the one Partial/Skeleton coverage concept (45%).
- **Confidence**: **High** (intentionally wrapped).
- **Risk level**: **Low** for removal (nobody sees it). **High** for deletion of the subsystem — the `Cosmetic*` model / DAO / repo / seed / ViewModel actions are wired through 9 files and referenced from `ClaimMilestone` (B3).
- **Verification step**: Same as B3 — product decision. Phase 11 MR1 proposes "ship one cosmetic end-to-end" as the smallest forward step.
- **Action**: **leave alone** until MR1 is scheduled. Flagged for the same reason as B3: a future cleaner who removes `StoreScreen` cosmetic card without touching the repo + entity will leave dangling references.

### B5. `CardLoadout` and `UltimateWeaponLoadout` — typed loadouts not used by runtime

- **Paths / symbols**: `domain/model/CardLoadout.kt`, `domain/model/UltimateWeaponLoadout.kt`.
- **Evidence**: Runtime "loadout" is `equipped: Boolean` flag on each entity, enforced in `ManageCardLoadout` use case via a `equippedCount >= CardLoadout.MAX_SIZE` check that pulls **only the `MAX_SIZE` constant** — not the class itself. `UltimateWeaponViewModel.kt:82` similarly hard-codes `3` rather than reading `UltimateWeaponLoadout.MAX_SIZE`. Phase 8 §4 named this.
- **Confidence**: **High** (the types are used only by their own tests and, for `CardLoadout`, one constant reference).
- **Risk level**: **Medium** — removing the companion `MAX_SIZE` breaks `ManageCardLoadout`; removing the class but keeping the constant requires a home for it.
- **Verification step**: `grep -r CardLoadout.MAX_SIZE app/src/main` (1 hit) and `grep -r UltimateWeaponLoadout app/src/main` (0 hits outside the file itself).
- **Action**: For `UltimateWeaponLoadout` — **remove** (see A2). For `CardLoadout` — **consolidate**: move `MAX_SIZE` to `CardType` companion or `ManageCardLoadout` companion, then remove the class + test. Deferred pending the same product decision as the typed-loadout architecture question.

### B6. `Currency` / `SupplyDropReward` / `MilestoneReward` — three overlapping reward vocabularies

- **Paths / symbols**: `domain/model/Currency.kt`, `domain/model/SupplyDropReward.kt`, `domain/model/MilestoneReward.kt`.
- **Evidence**: Phase 8 §4 and Phase 10 §2 explicitly name this. `Currency` is a plain enum (STEPS, CASH, GEMS, POWER_STONES). `SupplyDropReward` is a second enum (Steps, Gems, Power Stones, Card Dust). `MilestoneReward` is a sealed class (Gems, PowerStones, Cosmetic). Card Dust is only in `SupplyDropReward`; Cosmetic is only in `MilestoneReward`; Cash never appears as a reward because it's round-local. No single type captures all reward vectors; each consumer has its own `when` exhaustion.
- **Confidence**: **High** for the overlap; consolidation is **a design decision** (Phase 10 §5 argues against rewrites; Phase 11 §4 explicitly rejects a `Reward` sealed-hierarchy unification until a fourth reward type ships).
- **Risk level**: **High — DYNAMIC**. Both enums' `.name` is persisted in Room (`WalkingEncounterEntity.rewardType`, `PlayerProfileEntity` columns, `MilestoneEntity` has no reward column because `Milestone` is the key). Any collapse into a unified type demands a Room data migration + enum backfill.
- **Verification step**: N/A — don't touch without the product prerequisite in Phase 11 §4.
- **Action**: **leave alone**. Named here so future readers know the redundancy is intentional.

### B7. `PlayerWallet` omits `cardDust`

- **Path / symbol**: `domain/model/PlayerWallet.kt`. `PlayerProfile.kt` has `cardDust`; `PlayerWallet` does not.
- **Evidence**: Phase 8 §4. Result: some use cases (`UpgradeCard`, `OpenCardPack`) need `PlayerProfile` instead of `PlayerWallet`, so the `observeWallet()` fan-out is not uniform.
- **Confidence**: **High** (naming is the gap).
- **Risk level**: **Low** — additive field on a pure data class.
- **Verification step**: add the field + `PlayerProfile.toWallet()` mapping (already exists) populates it; update fakes; tests run.
- **Action**: **consolidate** — add `cardDust: Long = 0` to `PlayerWallet`. Small, low-risk. Not scheduled here; mentioned for completeness.


## C. Tests, fakes, fixtures

### C1. Silently-skipped JUnit 4 + Robolectric tests (classpath gap)

- **Path / symbols**:
  1. `app/src/test/java/com/whitefang/stepsofbabylon/data/local/RoomSchemaTest.kt` — 3 `@Test`, `@RunWith(RobolectricTestRunner::class)` + `org.junit.Test`.
  2. `app/src/test/java/com/whitefang/stepsofbabylon/service/StepWidgetProviderTest.kt` — 3 `@Test`, same.
  3. `app/src/test/java/com/whitefang/stepsofbabylon/presentation/DeepLinkRoutingTest.kt` — 3 `@Test`, same.
- **Evidence**: `app/build.gradle.kts` sets `unitTests.all { it.useJUnitPlatform() }`. Classpath audit in Phase 12 report (`smoke_tests/check_what_is_working/report.md` §"What is broken but acceptable") confirms `junit-platform-launcher` is present and `junit:junit:4.13.2` is transitively available via Robolectric, but `junit-vintage-engine` is **not on the classpath**. Without vintage engine, Jupiter silently skips JUnit 4-style tests. Phase 12 reported 2 files × 3 tests = 6 tests affected and said `DeepLinkRoutingTest` was running (`Area 5.5 ✅`). Grep contradicts that: `DeepLinkRoutingTest.kt` uses `import org.junit.Test` + `@RunWith(RobolectricTestRunner::class)` identically to the other two, so it should not be discovered either. Net impact therefore is **up to 9 tests, not 6**, silently dropped.
- **Confidence**: **High** for classpath gap; **Medium** for the DeepLinkRoutingTest inclusion (the XML report for `testDebugUnitTest` should be inspected to verify exactly how many `DeepLinkRoutingTest.*` tests ran).
- **Risk level**: **Low** — fix is additive.
- **Verification step**: `./run-gradle.sh :app:dependencies --configuration debugUnitTestRuntimeClasspath | grep -iE 'junit-vintage'` — confirm absence. Before/after test count check: today's 412 should rise to ~418–421 after the fix.
- **Action**: **document** here (don't auto-fix in this phase). The one-line fix is `testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")` in `app/build.gradle.kts`. Alternatively: port all 3 files to JUnit 5 + `@ExtendWith(RobolectricExtension::class)` for uniformity — Phase 12 report §"Non-destructive fix path" covers both.
- **Status (2026-05-07)**: **RESOLVED in Phase A.2** (commit `a336bce`). All 3 files recovered; medium-confidence DeepLinkRoutingTest inclusion was correct — test count rose from **412 to 421 (+9)**, matching the 3 × 3 estimate. One additional workaround beyond the one-line fix: each class needed `@Config(sdk = [34], application = android.app.Application::class)` to avoid (a) Robolectric 4.14.1 not supporting `compileSdk 36` and (b) the default Hilt test Application trying to load SQLCipher natively. JUnit 5 uniformity port is deferred.

### C2. `UltimateWeaponLoadoutTest.kt` — orphan if its class is removed

- **Path / symbol**: `app/src/test/java/com/whitefang/stepsofbabylon/domain/model/UltimateWeaponLoadoutTest.kt`.
- **Evidence**: 6 tests against a class that is **never instantiated in main** (see A2). The class is not removed yet; the test currently passes and doesn't block anything. Removing the class without removing the test will break compilation.
- **Confidence**: **High**.
- **Risk level**: **Low**.
- **Verification step**: remove together with the class (see A2).
- **Action**: **remove in the same PR as A2**.

### C3. Existing fakes — coverage audit (no orphans found)

- **Paths**: `app/src/test/java/com/whitefang/stepsofbabylon/fakes/` (15 files total).
- **Evidence of liveness** (grep `Fake* = …` / `Fake*(…)` constructor references): each of `FakePlayerRepository`, `FakeWorkshopRepository`, `FakeStepRepository`, `FakeUltimateWeaponRepository`, `FakeLabRepository`, `FakeCardRepository`, `FakeCosmeticRepository`, `FakeWalkingEncounterRepository`, `FakeBillingManager`, `FakeRewardAdManager`, `FakeDailyStepDao`, `FakeMilestoneDao`, `FakeDailyMissionDao`, `FakeDailyLoginDao`, `FakeWeeklyChallengeDao` has at least 2 consumers in `test/`.
- **Confidence**: **High** — all fakes used.
- **Risk level**: N/A.
- **Action**: **leave alone**. Named only because Phase 10 §4 proposed "configurable fake failure modes" — that's a feature addition, not a cleanup.

### C4. Test fixtures / sample data

- **Evidence**: there are **no fixture files** (no `*.json` in `app/src/test/resources`, no `TestFixtures.kt`, no `@Parameterized` data providers). All test data is inline per-test. This is noted here only to assert that the project has no orphaned fixtures to remove.
- **Confidence**: **High**.
- **Action**: **leave alone**.

### C5. Balance tests — consumers of the quarantined upgrades

- **Path / symbol**: `balance/CostCurveTest.kt:17,45`.
- **Evidence**: The `tested` list and the `for (level in 0 until 10)` cost-sum both reference `UpgradeType.STEP_MULTIPLIER`. See B2.
- **Confidence**: **High**.
- **Risk level**: **Low** — local test edit if B2 is resolved.
- **Verification step**: update this file in the same PR as any decision on B2.
- **Action**: **document**.

---

## D. Config, build, scripts, schema, migrations, deployment

### D1. Room schema files `1.json`–`6.json` (historical)

- **Path**: `app/schemas/com.whitefang.stepsofbabylon.data.local.AppDatabase/1.json`, `2.json`, …, `6.json`.
- **Evidence**: `AppMigrations.ALL` = `[MIGRATION_7_8]` (see `Migrations.kt`). No Migration objects exist for v1→v2, v2→v3, …, v6→v7 — those bumps happened under `fallbackToDestructiveMigration` during development, prior to R05 adding the explicit migration ladder. Schema files 1–6 are the exported JSON snapshots Room used at build time; they have no runtime effect once `MIGRATION_7_8` is the only member of `ALL`. However, Room's build-time schema validation (the `copyRoomSchemas` task + `room.schemaDirectory(…)` config) **expects prior versions to be present** when `exportSchema = true`; deleting 1–6 may or may not be accepted by Room 2.8.4 — behaviour is version-dependent and not guaranteed.
- **Confidence**: **Medium** — they look like artefacts, but Room's tooling decides whether they are.
- **Risk level**: **Medium — DYNAMIC**. Room validates schema compatibility against the files on every build.
- **Verification step**: in a throwaway branch, delete `1.json`–`6.json` and run `./run-gradle.sh :app:kspDebugKotlin --rerun-tasks` + `./run-gradle.sh assembleDebug`. If both pass and the v8 schema is still exported, they're safe to drop. If Room complains, restore.
- **Action**: **leave alone** for v1.0 release (zero benefit to deleting). Revisit if `app/schemas/` grows beyond ~12 versions.

### D2. `docs/plans/plan-05-health-connect.md` — renamed already, but referenced history

- **Path**: `docs/plans/plan-05-health-connect.md` (previously `plan-05-google-fit.md` — renamed in the March 2026 doc sweep).
- **Evidence**: Phase 7 delta already flagged that Google Fit references still linger in `plan-02`, `plan-03`, `ADR-0002`, `RUN_LOG.md`. Historical rename documented; not a cleanup target.
- **Confidence**: **High** (already done).
- **Action**: **leave alone**. Flagged so future cleaners don't re-rename.

### D3. `gradle/libs.versions.toml` — already audited in March 2026 sweep

- **Evidence**: The March 13 doc sweep removed the unused `kotlin-android` plugin. Phase 2 `intro2deployment.md` §"Build system" notes no other unused aliases. Current catalog has 25 library aliases; all referenced by `app/build.gradle.kts`.
- **Confidence**: **High**.
- **Action**: **leave alone**.

### D4. `run-gradle.sh` — helper script, gitignored

- **Evidence**: `README.md` documents its purpose (non-TTY wrapper). `.gitignore` lists it. Not a cleanup target.
- **Action**: **leave alone**.

### D5. `proguard-rules.pro` — audit pass

- **Path**: `app/proguard-rules.pro`.
- **Evidence**: Read in full — 8 keep-rule sections: Room, Hilt/Dagger, WorkManager, SQLCipher, Health Connect, Sensor callbacks, domain model enums, `org.json` (for `Converters`). All rules correspond to live framework consumers. No stale rules.
- **Confidence**: **High**.
- **Action**: **leave alone**. If `Converters` stops using JSON (unlikely pre-release), revisit the `org.json` keep.

### D6. `AndroidManifest.xml` — audit pass

- **Path**: `app/src/main/AndroidManifest.xml`.
- **Evidence**: 7 permissions, 1 application entry, 2 activities (MainActivity + HealthConnectPermissionActivity), 1 activity-alias (ViewPermissionUsageActivity → HealthConnectPermissionActivity, for Android 14+ HC permission rationale), 1 service (StepCounterService), 2 receivers (BootReceiver, StepWidgetProvider), 1 startup InitializationProvider removing the default WorkManager init. All classes exist and are loaded at runtime by their respective framework consumers.
- **Confidence**: **High**.
- **Action**: **leave alone**. Named because any candidate elsewhere that *removes* one of these classes must update the manifest in the same PR.

### D7. `app/src/main/res/raw/sfx_*.ogg` — placeholder audio assets

- **Paths**: `sfx_enemy_death.ogg`, `sfx_hit.ogg`, `sfx_round_end.ogg`, `sfx_shoot.ogg`, `sfx_upgrade.ogg`, `sfx_uw_activate.ogg`, `sfx_wave_start.ogg`.
- **Evidence**: STATE.md known-issues line: "Sound assets are placeholder sine wave tones." Referenced by `SoundManager.kt` (`R.raw.sfx_*`). Listed in Plan 31 / docs/agent/STATE.md as a release blocker.
- **Confidence**: **High** (placeholder, not orphan).
- **Risk level**: **Low** for *replacement* (drop-in). **High** for *removal* — `SoundManager.kt` will fail at runtime on the missing resource ID.
- **Action**: **leave alone** / **document** — flagged only because a future "cleanup" might mistakenly think a sine-wave OGG is an orphan. Name under Phase 11 MR4 release prep.

### D8. Missing app icon resources

- **Evidence**: STATE.md known-issues line: "No app icon resources." No `res/mipmap-*` or `ic_launcher*` files in `res/`. `AndroidManifest.xml` has no `android:icon=…` attribute on the `<application>` tag, so the default Android icon is used.
- **Confidence**: **High** — missing, not orphaned.
- **Risk level**: N/A (this is an omission).
- **Action**: **leave alone** (blocker for Plan 31, not cleanup).

### D9. Generated files / build outputs

- **Paths**: `app/build/`, `build/`, `.gradle/`, `.kotlin/`.
- **Evidence**: All `.gitignore`'d. Regenerated per build. Not in-tree.
- **Action**: **leave alone**.

### D10. `gradle.properties`, `local.properties`

- **Evidence**: `local.properties` is gitignored. `gradle.properties` sets `org.gradle.jvmargs`, `android.useAndroidX=true` — both live.
- **Action**: **leave alone**.

### D11. `docs/StepsOfBabylon_GDD.docx`

- **Evidence**: Source .docx for the GDD. `docs/StepsOfBabylon_GDD.md` is the authoritative markdown copy (Phase 7 used .md only). The .docx is a binary redundancy.
- **Confidence**: **Medium** — useful if product edits the GDD in Word; dead weight otherwise.
- **Risk level**: **Low**.
- **Verification step**: ask owner whether they edit in Word (`.docx`) or markdown. If markdown: remove `.docx`. If Word: ensure a scripted export step and document it.
- **Action**: **document** — add a note in the docs/ README (none exists today; create `docs/README.md` or a line in the top-level README explaining the relationship).

### D12. `docs/external-reviews/REPO_ANALYSIS_BUGS_AND_UX.md` + `_2.md`

- **Evidence**: External-review source docs that drove Plans R and R2. Both plans completed; reviews are historical. `docs/agent/STATE.md` still references them.
- **Confidence**: **High** — kept for audit trail.
- **Action**: **leave alone**.

### D13. Historical doc drift already catalogued

- **Evidence**: Phase 7 `devdocs/foundations/*` delta sections enumerate schema v7→v8 drift, three simultaneous test counts (397/401/412), the Battle Step Rewards feature missing from every user-facing doc, the GDD pillars self-inconsistency, and several vague spec areas. Phase 11 Q1 schedules these.
- **Action**: **leave alone** in the cleanup lens — these are doc-accuracy fixes, not cleanup. Called out so the two tracks don't collide.

---

## E. Docs — orphans, redundancy, retention

### E1. Already-deleted orphans (no action needed, evidence trail)

- `docs/agent/state.json` — deleted; `RUN_LOG` line 94 records creation, line 314 notes "orphaned file from earlier approach (harmless)". `ls` confirms absent.
- `docs/temp/` — deleted; `RUN_LOG` line 315 notes "contains a reference playbook from setup (harmless)". `ls` confirms absent.
- **Evidence**: `find . -name 'state.json' -path '*/docs/*'` returns nothing; `test -d docs/temp && echo y || echo n` returns `n`.
- **Confidence**: **High**.
- **Action**: **leave alone** — update `RUN_LOG` historical notes only if it becomes confusing. For now the lines honestly reflect what was in the tree when they were written.

### E2. `docs/plans/plan-R-remediation.md` and `plan-R2-remediation.md`

- **Evidence**: All 12 sub-plans in R and all 12 in R2 are marked complete in the doc + master-plan.md. Still useful as audit history.
- **Action**: **leave alone**.

### E3. `docs/balance/balance-report.md`

- **Evidence**: Generated once by Plan 28, 39 balance-regression tests reference its findings. Still current — no constants changed since.
- **Action**: **leave alone**.

### E4. `docs/release/*` — still needed for Plan 31

- **Paths**: `privacy-policy.md`, `play-store-listing.md`, `signing-guide.md`, `release-checklist.md`.
- **Evidence**: Plan 31 prerequisites.
- **Action**: **leave alone**.

### E5. `.kiro/steering/source-files.md` — out of date

- **Evidence**: Claims `@Database(version = 7)` — code is v8. Missing `MilestoneNotificationPreferences.kt` (R2-08 new file). Phase 1, 2, 5 all flagged.
- **Confidence**: **High**.
- **Risk level**: **Low**.
- **Verification step**: diff against `source-files.md` line by line against the actual tree; align.
- **Action**: **document** update (Phase 11 Q1).

### E6. `docs/architecture.md`, `docs/database-schema.md`

- **Evidence**: Both still reference schema v7 and omit `CosmeticEntity`; `database-schema.md` still reads "Current schema version: 2" at its top and lists v7 in its Security section (contradiction within the same doc). Flagged in Phase 2 `intro2codebase.md` delta, Phase 7 delta, Phase 9.
- **Action**: **document** update (Phase 11 Q1).


## F. Dynamic-risk register — classes that are "invisible" to grep

Enumerates classes, types, or symbols that are alive at runtime through dynamic resolution even when naive grep coverage looks thin. Everything here is **leave alone** unless a specific candidate above explicitly overrides.

| Symbol | Dynamic mechanism | Where to look before touching |
|---|---|---|
| `StepsOfBabylonApp` | Manifest `android:name=".StepsOfBabylonApp"` + `@HiltAndroidApp` | `AndroidManifest.xml`; `StepsOfBabylonApp.kt`; Hilt KSP generates `Hilt_StepsOfBabylonApp` |
| `MainActivity` | Manifest `<activity android:name=".presentation.MainActivity">` + `@AndroidEntryPoint` | `AndroidManifest.xml`; all 12 NavHost `composable(...)` blocks |
| `HealthConnectPermissionActivity` + `ViewPermissionUsageActivity` alias | Manifest intent-filters for HC rationale (both Android 13 and 14+ paths) | `AndroidManifest.xml`; HC SDK permission UI |
| `StepCounterService` | Manifest `<service foregroundServiceType="health">`; invoked via `startForegroundService` | `AndroidManifest.xml`; `MainActivity` permission callback |
| `BootReceiver` | Manifest `<receiver>` for `BOOT_COMPLETED` | `AndroidManifest.xml` |
| `StepWidgetProvider` | Manifest `<receiver>` for `APPWIDGET_UPDATE` + `res/xml/step_widget_info.xml` meta-data | `AndroidManifest.xml`; `step_widget_info.xml` |
| `StepSyncWorker` | `@HiltWorker` + `HiltWorkerFactory` via `StepsOfBabylonApp : Configuration.Provider` | `StepSyncScheduler.kt`; `StepsOfBabylonApp.kt`; WorkManager DB persists the class-name string |
| `AppDatabase` + every `@Entity` class | Room KSP codegen | `DatabaseModule.kt`; `AppMigrations.ALL`; `app/schemas/*.json` |
| Every `*Dao` interface | Room KSP codegen | `DatabaseModule.kt` `@Provides` blocks |
| Every `@Inject constructor` | Hilt KSP codegen | The six `di/*Module.kt` files; `RepositoryModule @Binds` for the 9 repository implementations |
| Stub swaps (`StubBillingManager`, `StubRewardAdManager`) | `@Binds` in `BillingModule` / `AdModule` | Plan 31 will replace these bindings with real SDKs; callers use interfaces |
| Domain enums (`UpgradeType`, `CardType`, `UltimateWeaponType`, `CosmeticCategory`, `SupplyDropTrigger`, `SupplyDropReward`, `ResearchType`, `OverdriveType`, `EnemyType`, `Currency`, `Milestone`, `DailyMissionType`, `Biome`) | Stored as `.name` strings in Room | R8 keep-rule `-keep enum com.whitefang.stepsofbabylon.domain.model.** { *; }`; check every `@TypeConverter` and every entity field declaration |
| `Migration` objects in `AppMigrations.ALL` | Room schema-version validator | `AppDatabase.kt @Database(version = N)` must equal the max `Migration.endVersion`; `app/schemas/N.json` must exist |
| `res/raw/sfx_*.ogg` | `SoundManager.kt` loads by `R.raw.…` | `res/raw/`; removing any file = runtime crash when the effect plays |
| `res/layout/widget_step_counter.xml` + `res/xml/step_widget_info.xml` | Inflated by `StepWidgetProvider` / referenced by manifest | Keep both; `widget_root` id is used by `RemoteViews` |
| `res/xml/network_security_config.xml` | Manifest `android:networkSecurityConfig=…` | Blocks cleartext; do not drop |
| SQLCipher native library | `System.loadLibrary("sqlcipher")` inside the SQLCipher runtime; also referenced in proguard `-keep class net.zetetic.**` | Do not touch unless replacing the encryption layer |
| Deep-link intent `navigate_to` extras | `MainActivity.onNewIntent` → `pendingNavigation: MutableStateFlow<String?>` | 4 notification managers produce these strings; mismatched routes silently drop |
| Compose route strings | `Screen.kt` sealed subclasses | `MainActivity` NavHost + every `navigate("…")` call site |

### F1. Audit checklist before removing *anything* from `app/src/main`

1. `grep -r <symbol> app/src/main` **and** `app/src/test` **and** `AndroidManifest.xml` **and** `proguard-rules.pro` **and** `res/` **and** `app/schemas/`.
2. For entity/enum edits: write a Room `Migration` and an enum-name backfill (if renaming).
3. For anything touching notification deep-links: update every producer (4 notification managers).
4. For anything touching battle engine: add or update a `BattleViewModelTest` scenario.
5. For anything touching stubs-vs-real SDKs: Plan 31 is the scheduled locus; do not pre-empt.
6. Run `./run-gradle.sh testDebugUnitTest assembleDebug lintDebug` before committing. Check APK size didn't grow unexpectedly.

---

## G. Retention, compatibility, and legal hold

Called out explicitly because the prompt asks for flags on audit / compliance / licensing / backwards compatibility / public API retention.

### G1. Audit retention

- **`docs/agent/RUN_LOG.md`** — append-only project audit trail. Entries dated from 2026-03-04 to 2026-05-05. Mandated by `.kiro/steering/11-agent-protocol.md` and `.kiro/steering/10-project-memory.md`. **Do not truncate or delete historical entries**, even those that refer to files which no longer exist (`state.json`, `docs/temp/`, old plan filenames). Phase 7 noted this explicitly.
- **`docs/agent/DECISIONS/ADR-000N-*.md`** — architecture decision records. Three exist (template + ADR-0002 Health Connect + ADR-0003 Battle Step Rewards). ADR retention is per project memory steering rules.
- **`docs/external-reviews/*`** — input documents for remediation plans; retain.

### G2. Legal / licensing retention

- `LICENSE` — not present in the repo tree (not flagged as cleanup; noted for Plan 31 preparation).
- `docs/release/privacy-policy.md` — required for Play Store listing.
- `docs/release/signing-guide.md` — required for build pipeline.
- **Placeholder audio files (`sfx_*.ogg`)** — generated as sine-wave tones per STATE.md. When replaced with licensed audio, document provenance and license in a `res/raw/LICENSES.md` or equivalent. No obligation exists today because the placeholders are self-made.

### G3. Public API / backwards compatibility

- The app has **no public API**. No external integrations, no server, no multiplayer (per GDD and STATE.md).
- **Public surface is the Play Store listing + runtime behaviour on existing installs**. Every cleanup candidate above that touches a **Room entity / DAO / enum name / SharedPreferences key / WorkManager class name** has a backwards-compat obligation against users on v1.0.0 (versionCode 1). Once v1.0.1 ships, every such edit must be migratable; once multiple `versionCode`s ship, pairs of adjacent versions must be jointly migratable.
- `versionName = "1.0.0"` / `versionCode = 1` at `app/build.gradle.kts` — this is the first release; nothing to preserve yet. But plan accordingly.

### G4. Build-time contracts

- `app/schemas/*.json` — Room schema validator input. See D1.
- `keystore.properties` — gitignored; loaded by `app/build.gradle.kts`. Its absence during local debug builds is handled.
- `run-gradle.sh` — gitignored; its absence is surfaced in README as a manual recreate step.

---

## H. Summary — "don't delete, do decide"

Across all sections: **3 items are High-confidence, Low-risk pure removals** (A1, A2, C2). The rest either need a product decision (§B), are duplicates worth consolidating (§A3, A4, A5, A8), are doc/comment hygiene (§A7, E5, E6), are already-deleted historical orphans (§E1), or are dynamic-risk items explicitly called out as *leave alone* (§F).

**Prerequisites tracked elsewhere** (do not re-schedule):
- Cosmetic subsystem end-to-end: Phase 11 MR1.
- `Reward` hierarchy unification: Phase 11 §4 (explicitly rejected until a fourth reward type ships).
- TimeProvider / @Transaction / FollowOnPipeline extractions: Phase 4 items 1, 2, 4; Phase 11 I4, I2/I3, M1.
- Anti-cheat visibility (surface counters in Stats): Phase 4 item 5; Phase 11 I1.
- Play Console + real SDK integration: Plan 31.

**Cross-references.**
- `devdocs/archaeology/5_things_or_not.md` — 5 PR-sized leverage bets.
- `devdocs/archaeology/architecture_analysis.md` — 13 "what doesn't make sense" items.
- `devdocs/archaeology/module_discovery.md` — module-boundary prioritised list.
- `devdocs/archaeology/concept_mappings.md` Appendix B — 25-concept coverage % roll-up.
- `devdocs/evolution/gap_analysis.md` §3 — 12 tech-debt items ordered by leverage.
- `devdocs/evolution/gap_closure_plan.md` — phased execution order.
- `smoke_tests/check_what_is_working/report.md` — C1 junit-vintage fix path.

**What this document is not.**
- Not a work plan. Phase 11 is the schedule.
- Not an ADR. No architectural decision is made here.
- Not a deletion. No file is modified by this session.
- Not exhaustive of every single grep-match; focused on the 30-odd candidates with actionable evidence and meaningful cleanup signal.

---

*End of cleanup inventory. Update STATE.md + append RUN_LOG.md next per `.kiro/steering/11-agent-protocol.md`.*
