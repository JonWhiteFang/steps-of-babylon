# Missing Concepts

*Archaeology Phase 5 — concepts that the code tree does **not** implement,
derived by reconciling the GDD + roadmap (`docs/plans/master-plan.md`) +
ADRs + `STATE.md` known issues + Phase 3 trace §8–§10 findings against
actual code. Each entry: ≤3 sentences, implementation status
(`Missing` unless noted), and file pointers to the nearest related code
or doc. Split between **intentionally deferred** and **unintended gaps**.*

---

## 1. Intentionally deferred (tracked in roadmap)

### Real Google Play Billing Library integration
Plan 31 scope. `StubBillingManager` simulates purchases with a 500 ms
delay and always returns `PurchaseResult.Success`; no receipt verification,
no subscription renewal handling, no grace periods. Swap point is the
`@Binds` in `BillingModule`.
**Implementation status:** Missing (stub in place).
**Files:** `data/billing/StubBillingManager.kt`, `di/BillingModule.kt`,
`docs/plans/plan-31-play-console.md`.

### Real AdMob (or ad-mediation) SDK integration
Plan 31 scope. `StubRewardAdManager` waits 1 s then always returns
`AdResult.Rewarded`; no mediation, no fill-rate handling, no
country-specific ad rules. Swap point is the `@Binds` in `AdModule`.
**Implementation status:** Missing (stub in place).
**Files:** `data/ads/StubRewardAdManager.kt`, `di/AdModule.kt`,
`docs/plans/plan-26-monetization.md`.

### Accessibility — full Plan 24 pass
TalkBack polish beyond spot `contentDescription`, colour-blind modes,
adjustable text sizing, audio cues, alternative input schemes.
`CONSTRAINTS.md` calls out accessibility as a post-v1.0 priority; Plan 24
is checked "deferred" in the master plan.
**Implementation status:** Missing.
**Files:** `docs/plans/plan-24-accessibility.md`, `docs/agent/STATE.md`
("Accessibility (Plan 24, deferred)").

### Cosmetic visual application in renderer
`CosmeticEntity` supports ownership and equipping of 7 placeholder items,
but the battle renderer never reads the equipped item to alter ziggurat /
projectile / enemy appearance. R2-11 disables the purchase button with a
"Coming Soon" label until the visual pipeline lands.
**Implementation status:** Missing (ownership works; rendering no-op).
**Files:** `presentation/store/StoreScreen.kt`,
`data/repository/CosmeticRepositoryImpl.kt`,
`presentation/battle/engine/GameEngine.kt` (no cosmetic hook).

### Real audio assets
`res/raw/sfx_*.ogg` files are placeholder sine-wave tones; `SoundManager`
and `SoundPool` plumbing is complete. Sourcing royalty-free SFX is a
release-prep manual step.
**Implementation status:** Missing (pipeline present).
**Files:** `res/raw/sfx_*.ogg`, `presentation/audio/SoundManager.kt`,
`docs/agent/STATE.md` (known issue).

### App icon resources
No custom launcher icon; the default system icon is in use. Called out in
`STATE.md` known issues.
**Implementation status:** Missing.
**Files:** `res/mipmap-*/`, `AndroidManifest.xml` (`android:icon` absent at
application level).

### Host a public privacy-policy URL
`docs/release/privacy-policy.md` contains the policy text, but Google Play
requires an https-reachable URL. Hosting is a release-prep manual step.
**Implementation status:** Missing (text complete).
**Files:** `docs/release/privacy-policy.md`,
`docs/release/release-checklist.md`.

### Store listing visual assets
Screenshots (phone + 7-inch tablet), feature graphic (1024×500), promo
video. `play-store-listing.md` enumerates requirements but no assets live
in the repo.
**Implementation status:** Missing.
**Files:** `docs/release/play-store-listing.md`.

### Instrumented (`androidTest`) test suite
All 412 tests are JVM-only (JUnit 5 + Robolectric). No Room DAO tests on a
real SQLite instance, no Compose UI tests via `createAndroidComposeRule`,
no service / widget integration tests on device. `README.md` explicitly
notes these are planned but not implemented.
**Implementation status:** Missing.
**Files:** `README.md`, `docs/plans/plan-29-testing.md`.

### CI / CD pipeline
No `.github/`, no `.circleci/`, no `.gitlab-ci.yml`, no Jenkinsfile, no
fastlane, no Dockerfile. All release checks are manual
(`./run-gradle.sh test` + `assembleRelease` gate).
**Implementation status:** Missing (intentional for v1.0).
**Files:** `docs/release/release-checklist.md` (manual gate).

### Step burst trigger for supply drops
`SupplyDropTrigger.STEP_BURST` enum value exists with a message ("Your pace
is impressive!"), but no burst-detection code ever emits that trigger.
`GenerateSupplyDrop` only checks milestone / threshold / random.
**Implementation status:** Missing (enum entry orphan).
**Files:** `domain/model/SupplyDropTrigger.kt`,
`domain/usecase/GenerateSupplyDrop.kt` (no STEP_BURST branch).

### Onboarding / tutorial flow
No first-run tutorial. New players see the Home screen cold, with
permission prompts and a "BATTLE" button. Documented but not built.
**Implementation status:** Missing.
**Files:** `presentation/home/HomeScreen.kt`,
`presentation/MainActivity.kt`.

### Localisation / i18n
All UI strings are English-only, hardcoded in composables (no `strings.xml`
resource IDs in the screens). The GDD doesn't list additional locales for
v1.0.
**Implementation status:** Missing (intentional for v1.0).
**Files:** every `presentation/**/*Screen.kt`, `res/values/strings.xml`
(holds only `app_name`).

---

## 2. Unintended gaps (no tracking doc yet)

### Centralised `TimeProvider` abstraction
53 direct `System.currentTimeMillis()` / `LocalDate.now()` calls across 33
files (counted during Phase 4). Currently mitigated with the
default-parameter pattern, but VM tickers and cross-module date-string
consistency cannot use that. Phase 4 item 1 is the explicit proposal.
**Implementation status:** Missing.
**Files:** `devdocs/archaeology/5_things_or_not.md` §1, `data/sensor/DailyStepManager.kt`
(`todayDate()`), `domain/usecase/AwardBattleSteps.kt`.

### Room `@Transaction` wrapping for multi-write sequences
Zero `@Transaction` or `withTransaction` uses in `app/src/main`. Two known
partial-failure risks: `AwardBattleSteps` (addSteps + incrementBattleSteps)
and `StepCrossValidator` Levels 0/1 (spendSteps + updateEscrow). Phase 4
item 2 covers this.
**Implementation status:** Missing.
**Files:** `domain/usecase/AwardBattleSteps.kt`,
`data/healthconnect/StepCrossValidator.kt`,
`devdocs/archaeology/5_things_or_not.md` §2.

### `DatabaseKeyManager` should wipe the DB file on decrypt failure
Current recovery wipes the encrypted-passphrase blob in SharedPreferences
and generates a fresh key — but leaves the SQLCipher-encrypted DB file on
disk, which then cannot be opened with the new passphrase. Produces
crash-on-launch after device restore. Trace 12 §9.
**Implementation status:** Missing.
**Files:** `data/local/DatabaseKeyManager.kt`,
`devdocs/archaeology/traces/trace_12_db_bootstrap_and_keystore.md`.

### Robust round-end cascade against navigation interrupts
If the user deep-links away mid-battle (or the OS kills the VM), `endRound`
never runs; best wave, PS awards, and daily-mission progress are lost.
`BattleViewModel.onCleared` nulls the step-reward callback but does not
finalise the round. Phase 4 item 3.
**Implementation status:** Missing.
**Files:** `presentation/battle/BattleViewModel.kt` (`onCleared`),
`devdocs/archaeology/5_things_or_not.md` §3.

### Deep-link coverage for all 12 routes
`MainActivity.pendingNavigation` collector handles only Home, Workshop,
Battle, Missions, Supplies. Store, Stats, Weapons, Cards, Economy,
Settings cannot be reached from a notification even though notification
managers have no reason to exclude them. Trace 10 §8.
**Implementation status:** Missing (partial coverage).
**Files:** `presentation/MainActivity.kt`,
`devdocs/archaeology/traces/trace_10_supply_drop_to_deep_link.md`.

### Extract `FollowOnPipeline` from `DailyStepManager`
`DailyStepManager` has 11 constructor dependencies and mixes pure crediting
with 5 downstream subsystems. Phase 4 item 4 proposes pulling the follow-on
stages into their own class so DailyStepManager shrinks to its actual
responsibility (rate limit → velocity → ceiling → persist).
**Implementation status:** Missing (refactor not done).
**Files:** `data/sensor/DailyStepManager.kt`,
`devdocs/archaeology/5_things_or_not.md` §4.

### Surfacing anti-cheat effects to the player
`AntiCheatPreferences` tracks rate-rejected, velocity-penalised, CV offenses —
none are shown in any UI. Players whose credit slows due to velocity
penalty or cross-validation cap have no visible explanation. Phase 4 item 5.
**Implementation status:** Missing.
**Files:** `data/anticheat/AntiCheatPreferences.kt`,
`presentation/stats/StatsScreen.kt`,
`devdocs/archaeology/5_things_or_not.md` §5.

### Boss / high-threat targeting priority
`ZigguratEntity` fires at the *nearest* enemies only (`findNearestEnemies(n)`).
No option to prioritise bosses, ranged, or low-HP targets — a common
tower-defence ergonomics gap that balance tests don't flag because they
run in aggregate.
**Implementation status:** Missing.
**Files:** `presentation/battle/entities/ZigguratEntity.kt`,
`presentation/battle/engine/GameEngine.kt` (`findNearestEnemies`).

### Sound settings location
Sound mute / volume lives in `NotificationSettingsScreen` because it is the
only "settings" screen in the app. There is no dedicated audio/game
settings screen, which is surprising given a "Sound" section on a
"Notifications" page.
**Implementation status:** Missing (dedicated surface).
**Files:** `presentation/settings/NotificationSettingsScreen.kt`.

### `PlaceholderScreen` dead composable
`MainActivity.kt` still holds a `@Composable fun PlaceholderScreen(...)`
that no NavHost entry references. Flagged as unused in Phase 1 and
trace 05 but not yet removed.
**Implementation status:** Missing (dead code removal).
**Files:** `presentation/MainActivity.kt`.

---

## 3. Compliance / privacy / operational assumptions (unwritten)

### Data-export / data-deletion flow (GDPR-style)
No user-facing "export my data" or "delete my account" flow. Because all
state is local, deletion is "uninstall the app" — but this is nowhere
documented as the compliance answer, and no in-app UI surfaces the fact.
**Implementation status:** Missing (implicit via uninstall).
**Files:** `docs/release/privacy-policy.md` (no export/delete section),
`presentation/settings/NotificationSettingsScreen.kt` (no "delete data").

### Crash reporting / observability
No Crashlytics, Sentry, or Bugsnag. Production crashes surface only via
Play Console's ANR/crash reports. No structured logging beyond
`android.util.Log`, no log shipping.
**Implementation status:** Missing (intentional privacy stance).
**Files:** absence in `gradle/libs.versions.toml`.

### Remote config / kill-switch
No Firebase Remote Config or equivalent. Balance tuning, feature flags,
and emergency kill-switches all require a new app release. Acceptable for
v1.0 offline product but constrains rapid response.
**Implementation status:** Missing (intentional).
**Files:** absence in `gradle/libs.versions.toml`.

### Cloud save / cross-device sync
`allowBackup="false"` (R05) means Google's Auto Backup is disabled, and
no app-level cloud sync exists. Users who reinstall or change device lose
all progress. Trade-off made explicitly in R05 for data-integrity reasons.
**Implementation status:** Missing (intentional post-R05).
**Files:** `AndroidManifest.xml`,
`devdocs/archaeology/traces/trace_12_db_bootstrap_and_keystore.md`.

### Post-release schema-migration rehearsal
`AppMigrations.MIGRATION_7_8` is the only written migration; `.fallbackToDestructiveMigrationOnDowngrade(true)`
covers downgrades by dropping all tables. There is no documented
post-release migration-rehearsal process (unit test against a seeded DB,
for example), which the single-migration history hides.
**Implementation status:** Missing (untested process).
**Files:** `data/local/Migrations.kt`, `di/DatabaseModule.kt`,
`app/schemas/*.json`.

### Server-authoritative cheat validation
`CONSTRAINTS.md` accepts no server backend for v1.0, so anti-cheat is
client-only (rate limit, velocity, HC cross-validation, caps). A player with
root or a modified build can defeat every protection. Accepted tradeoff,
not a bug — but worth naming.
**Implementation status:** Missing (intentional).
**Files:** `docs/agent/CONSTRAINTS.md`,
`data/sensor/StepVelocityAnalyzer.kt`,
`data/healthconnect/StepCrossValidator.kt`.

### No in-app privacy-policy navigation outside HC rationale
Players can reach a policy summary only via Health Connect permission
rationale (`HealthConnectPermissionActivity`). Settings / Store / Home have
no "Privacy Policy" link. The external Play-Store-hosted policy URL is the
only other surface.
**Implementation status:** Missing.
**Files:** `presentation/HealthConnectPermissionActivity.kt`,
`presentation/settings/NotificationSettingsScreen.kt`.

### Play Console configuration, test tracks, staged rollout
Plan 31 scope. Product SKU registration, internal/closed/open test tracks,
staged rollout percentages, pre-launch report review — all manual Play
Console work that has no code equivalent and is not recorded in the repo.
**Implementation status:** Missing.
**Files:** `docs/plans/plan-31-play-console.md`,
`docs/release/release-checklist.md`.

---

## 4. Integration contracts worth calling out

### Single `Random = Random` default param is the project's RNG contract
The three use cases with injectable `Random` (`CalculateDamage`,
`OpenCardPack`, `GenerateSupplyDrop`) set the pattern. Anyone adding a new
stochastic use case should follow it rather than introducing a new
abstraction — but that contract exists only as convention, not as a
documented interface or lint rule.
**Implementation status:** Partial (convention, no enforcement).
**Files:** `domain/usecase/CalculateDamage.kt`,
`domain/usecase/OpenCardPack.kt`, `domain/usecase/GenerateSupplyDrop.kt`.

### `today: String = LocalDate.now().toString()` default param is the time contract
Same shape as the RNG contract: a date parameter with a live default. Not a
`TimeProvider`; not a `Clock` object. Undocumented except as a pattern
visible in `AwardBattleSteps`, `StartResearch`, `TrackDailyLogin`. Phase 4
item 1 argues for upgrading this to a narrow `TimeProvider`.
**Implementation status:** Partial (convention, no enforcement).
**Files:** `domain/usecase/AwardBattleSteps.kt`,
`domain/usecase/TrackDailyLogin.kt`,
`devdocs/archaeology/5_things_or_not.md` §1.

### Notification deep-link route protocol
Intent extra key `navigate_to` with a value equal to a `Screen.route` string.
Only five of twelve routes are honoured today. No schema class, no
enum, no compile-time safety — a future new notifier could typo the extra
key and the deep-link would silently no-op.
**Implementation status:** Partial.
**Files:** four `service/*NotificationManager.kt`, `presentation/MainActivity.kt`,
`devdocs/archaeology/traces/trace_10_supply_drop_to_deep_link.md`.

### Widget SharedPreferences contract
`StepWidgetProvider` reads `steps` + `balance` from a specific SharedPreferences
file; `WidgetUpdateHelper` writes those keys. The coupling is stringly-typed
and documented only by inspection. If key names drift, the widget shows
stale zeros with no error.
**Implementation status:** Partial (implicit contract).
**Files:** `service/StepWidgetProvider.kt`, `service/WidgetUpdateHelper.kt`,
`devdocs/archaeology/traces/trace_11_widget_update.md`.

### `GameEngine.onStepReward` callback protocol
The engine exposes `@Volatile var onStepReward: ((Long) -> Unit)?` and
documents in-code that "the listener must not block the loop — forward to a
coroutine scope. Set to null to unsubscribe". That contract is enforced by
reviewer judgement, not compiler. Subtle bugs here would manifest as frame
drops during kills.
**Implementation status:** Partial (doc-only contract).
**Files:** `presentation/battle/engine/GameEngine.kt`,
`presentation/battle/BattleViewModel.kt` (`wireStepRewardCallback`).

### Foreground-service heartbeat ↔ worker coordination
The 2-minute heartbeat threshold in `StepIngestionPreferences.HEARTBEAT_THRESHOLD_MS`
is the sole protocol between service and worker. If WorkManager changes its
minimum periodic interval below the heartbeat threshold, or the service's
notification-update frequency changes, the coordination breaks silently.
**Implementation status:** Partial (implicit).
**Files:** `data/sensor/StepIngestionPreferences.kt`,
`service/StepSyncWorker.kt`, `service/StepCounterService.kt`,
`devdocs/archaeology/traces/trace_02_step_sync_worker_and_heartbeat_handoff.md`.
