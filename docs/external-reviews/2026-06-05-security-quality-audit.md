# Security & Quality Audit — Steps of Babylon

> **Generated:** 2026-06-05 09:21 UTC
> **Scope:** Full-repository audit — security vulnerabilities, performance bottlenecks, technical debt.
> **Method:** Multi-agent orchestration — 8 partitioned subsystem finders → adversarial per-finding verification (71 agents). Each finding was re-opened and challenged by an independent verifier against the cited code and the offline-game threat model. The two High findings and the secrets/CI surface were additionally hand-verified.
> **Commit context:** branch `main` @ `636e054` (post PR #112).

## Executive summary

**What it is:** An offline, single-player Android game (~33k LOC Kotlin, 255 main + 132 test files). Stack: Jetpack Compose, Hilt, Room **+ SQLCipher** (encrypted DB), Google Play Billing v8, AdMob + UMP consent, Health Connect. **No backend, no accounts, no server API.**

**What that means for the threat model:** Classic web vulns (SQLi, XSS, CSRF, auth/session) don't apply. The real attack surface is **(1) in-app-purchase & client-economy integrity**, (2) local data-at-rest crypto, (3) GDPR/consent correctness, (4) the per-frame battle engine's performance, and (5) dependency/CI hygiene. A rooted user editing their *own* offline wallet is an accepted limitation (ranked Low); the focus is on **logic bugs and races that let a *normal, non-rooted* user dupe currency or that harm paying users.**

**Headline:** The codebase is genuinely well-engineered — SQLCipher at rest, `allowBackup=false`, cleartext disabled, SHA-pinned CI actions, no committed secrets, an established **atomic-claim pattern** (`grantOnceAtomic`, `claimMilestoneAtomic`, `purchaseUpgradeAtomic`) for money paths, and a large test suite. **The dominant problem is that this atomic pattern was applied inconsistently** — a whole *class* of currency-grant/spend paths skipped it, creating double-grant races a normal user can trigger by double-tapping.

| Severity | Security | Performance | Tech Debt | Total |
|---|---|---|---|---|
| **High** | 2 | 0 | 0 | **2** |
| **Medium** | 6 | 1 | 5 | **12** |
| **Low** | 8 | 13 | 27 | **48** |

---

## 🔴 HIGH severity

### H1 · Security · Daily-mission claim double-grants premium currency on double-tap
**Files:** `presentation/missions/MissionsViewModel.kt:96-104`, `data/local/DailyMissionDao.kt:27`

`claimMission(id)` is a non-atomic read-then-act with **no `_processing` guard** (every other ViewModel has one):
```kotlin
val missions = dailyMissionDao.getByDateOnce(today)
val m = missions.find { it.id == id && it.completed && !it.claimed } ?: return@launch
if (m.rewardGems > 0) playerRepository.addGems(m.rewardGems)          // unconditional increment
if (m.rewardPowerStones > 0) playerRepository.addPowerStones(...)
dailyMissionDao.markClaimed(id)   // UPDATE ... SET claimed=1 WHERE id=:id  -- no "AND claimed=0" guard
```
The first suspension point (`getByDateOnce`) yields the main thread; two rapid taps both pass the `!it.claimed` snapshot check before either `markClaimed` commits → reward credited twice. **Gems are the same premium currency sold for real money via IAP** (`BillingManagerImpl.kt:232`). No root required — just two taps on a visible button. The repo already solved this exact race for milestones (`claimMilestoneAtomic`); missions were never converted.

**Fix:** Add `DailyMissionDao.claimMissionAtomic` — a `@Transaction` doing `UPDATE ... SET claimed=1 WHERE id=:id AND completed=1 AND claimed=0`, credit only if rows-affected == 1. Add a `_processing` guard to `claimMission`.

### H2 · Security · Paying Season Pass subscribers lose access after 30 days while still being charged
**Files:** `data/billing/BillingManagerImpl.kt:236-238, 128-135, 308`, `data/local/BillingReceiptDao.kt:100`

Season Pass is an auto-renewing `SUBS`. Expiry is computed **client-side**: `expiry = purchase.purchaseTime + THIRTY_DAYS_MILLIS`, and `isSeasonPassActive()` force-disables once `expiry < now`. The KDoc claims reconciliation "refreshes the expiry on each renewal" — but `reconcileType()` routes every renewal through `grantOnceAtomic`, which short-circuits `if (existing?.granted == true) return false` keyed on `purchaseToken`. **For auto-renewing Play subscriptions the `purchaseToken` is stable across renewals**, so on every post-first sweep the row is already `granted=true`, `creditWallet()`/`updateSeasonPass()` never re-fire, and `purchaseTime` stays the original. Net effect: a paying subscriber's pass flips **off 30 days after the first purchase** even though Google keeps billing monthly. The captured `isAutoRenewing` flag is never read.

**Fix:** Don't derive SUBS validity from a client-side `+30d` timer. In `reconcileType()`, for SUBS purchases that Play reports as `PURCHASED`, call `updateSeasonPass(active=true, …)` on **every** sweep independent of `grantOnceAtomic` (reserve `grantOnceAtomic` for one-time consumables). Drop the client 30-day clock.

---

## 🟠 MEDIUM severity

### The "non-atomic spend/grant" cluster (Security)
The same root-cause pattern recurs across five more economy paths — each reachable by a non-rooted user, each bounded in payoff (soft currency / items, not unbounded minting), hence Medium not High:

| # | Issue | Files | Fix |
|---|---|---|---|
| M1 | **`OpenCardPack` awards 3 cards even when the gem spend is a no-op.** `spendGems()` calls guarded `spendGemsAtomic` but **discards** its rows-affected result; cards granted unconditionally. Stale `uiState.value.gems` snapshot passes the pre-check. | `domain/usecase/OpenCardPack.kt:27-48`, `data/repository/PlayerRepositoryImpl.kt:33` | Make spend+grant one `@Transaction`; award only if deduct affected 1 row. |
| M2 | **In-app gem purchases grant item when atomic spend fails** (same discarded-result bug). `purchaseCosmetic` → `spendGems` (ignored) → `markOwned` unconditionally. | `presentation/store/StoreViewModel.kt:102-110`, `PlayerRepositoryImpl.kt:33` | Return Boolean from `spendGems`; gate grant on it; else "Not enough Gems". |
| M3 | **`ClaimSupplyDrop` double-credits.** In-memory `if (drop.claimed)` check + non-atomic credit-then-`markClaimed` (no `AND claimed=0` guard). Double-tap or `claimAll` racing a tap both credit. | `domain/usecase/ClaimSupplyDrop.kt:20-35`, `data/local/WalkingEncounterDao.kt:20`, `presentation/supplies/UnclaimedSuppliesViewModel.kt:31` | Guarded `UPDATE … WHERE id=:id AND claimed=0` returning Int; credit only on 1 row. Add `_processing` guard. |
| M4 | **`TrackDailyLogin` awards the same day's gems/power-stones twice** under concurrent foreground (`HomeViewModel.init`) + background (`DailyStepManager` ingestion) runs. Both read `*Claimed=false`, both credit (additive SQL increments). | `domain/usecase/TrackDailyLogin.kt:21-56`, `data/local/DailyLoginDao.kt:7` | Single guarded `@Transaction` mirroring `claimMilestoneAtomic`. |
| M5 | **Cosmetic purchase marks owned even when gem spend silently fails** (the discarded `spendGemsAtomic` result again, distinct call path). | `StoreViewModel.kt:107`, `PlayerRepositoryImpl.kt:33` | Same as M2. |

> **The single highest-leverage fix in the whole audit:** change `PlayerRepositoryImpl.spendGems`/`spendPowerStones` to return the atomic DAO's success Boolean, and have every caller gate the grant on it. That one change neutralizes M1, M2, M5 and hardens H1/M3/M4.

### Other Medium findings

| # | Cat | Issue | Files | Fix |
|---|---|---|---|---|
| M6 | Security | **Season Pass kill-during-flow can't be recovered:** `reconcilePendingPurchases()` is only called from `StoreViewModel.init`, never on app resume (the interface KDoc says it *should* be on resume — wiring was specified, never implemented). | `BillingManagerImpl.kt:154`, `StoreViewModel.kt:44`, `MainActivity.kt:252` | Call `reconcilePendingPurchases()` from `MainActivity.onResume` (already Mutex-guarded/idempotent). |
| M7 | Tech Debt | **Shared `@Singleton DailyStepManager` mutated concurrently by foreground service + WorkManager** with plain non-volatile fields and no Mutex. `updateDailySteps` is a non-atomic read-copy-upsert → lost updates corrupt `daily_step_record` (wallet stays correct via atomic increment, so the row and wallet diverge). | `data/sensor/DailyStepManager.kt:69-160`, `data/repository/StepRepositoryImpl.kt:21` | Wrap mutations in a `Mutex`, **or** make the row write an atomic incrementing UPSERT (mirror `incrementBattleSteps`). |
| M8 | Performance | **Per-frame heap allocations on the battle hot path.** `CollisionSystem.checkCollisions` does 3× `filterIsInstance<T>().filter{…}` (6 lists/tick); `getAliveEnemies()` 2 more, called per-orb/per-UW; at 4× speed up to ~4 update()/frame → dozens of short-lived lists/frame at 40 enemies. GC-churn jank on low-end devices. | `presentation/battle/engine/CollisionSystem.kt:24-26`, `GameEngine.kt:728,647` | Reusable scratch lists `clear()`+refilled once/tick; partition entities by type in one pass. (Note: `removeAll{}`/`addAll` cited by the finder are in-place and do *not* allocate.) |
| M9 | Tech Debt | **Room migrations have zero migration tests.** `MIGRATION_9_10`/`10_11` do destructive table-recreate + data-split on real upgrades; logic is correct on inspection but a future bug would silently corrupt wallets invisibly (`MigrationTestHelper` absent). | `Migrations.kt:81,136`, `RoomSchemaTest.kt:24` | Add `MigrationTestHelper` androidTest seeding v9/v10 rows; assert transformed data; wire into `instrumented.yml`. |
| M10 | Tech Debt | **`StepSyncWorker` step-crediting logic is re-implemented as a hand-written copy inside `StepIngestionTest`** — drift in the real worker isn't caught; worker + `StepCounterService` have no direct test. | `StepIngestionTest.kt:32` vs `StepSyncWorker.kt:67` | Extract catch-up into an injectable collaborator both call, or test via `TestListenableWorkerBuilder`; delete the copy. |
| M11 | Tech Debt | **`ActivityMinuteConverter`** (exercise-minutes→currency, with double-count suppression + stateful cap-delta math) has no test. | `data/healthconnect/ActivityMinuteConverter.kt:40`, `StepSyncWorker.kt:52` | Unit-test below/above the 50-steps/min threshold, multi-session cap clamping, unknown exercise type. |

---

## 🟡 LOW severity (selected highlights of 48)

**Security / crypto (8):**
- **Keystore key not bound to device-unlock/StrongBox** — `DatabaseKeyManager.kt:95-99` omits `setUnlockedDeviceRequired(true)` / `setIsStrongBoxBacked(true)` (minSdk 34, always available). Defensive-only for an offline game; add both (StrongBox in try/catch fallback).
- **Downgrade silently destroys all local data** including purchased state — `DatabaseModule.kt:24-28` uses `fallbackToDestructiveMigrationOnDowngrade`.
- **`obfuscatedAccountId` is a non-keyed SHA-256 of a cleartext SharedPreferences UUID**, trivially resettable (`BillingManagerImpl.kt:351`) — weak anti-fraud signal (accepted for v1).
- **`PurchasesUpdatedListener` resolves the single in-flight deferred for *any* purchase Play delivers**, including out-of-band ones (`RealBillingClientAdapter.kt:72`).
- **`connected=true` cached permanently** — a dropped Billing connection is never re-established by `ensureConnected()` (`BillingManagerImpl.kt:82,168`).
- **Wall-clock replay** of daily-login/streak/step economy by changing device time (offline, self-only — accepted).
- **`UltimateWeaponViewModel` unlock/upgrade lack the in-flight guard** every other purchase screen has (`:97-107`).
- **Corrupted `daily_step_record` from the M7 race feeds `StepCrossValidator` escrow deductions** — anti-cheat can wrongly debit a normal user's wallet (`StepCrossValidator.kt:67-152`).

**Performance (13):**
- Per-frame `Paint` allocation in `GameEngine.render()` chrono overlay (`:439`).
- Per-enemy `String` allocation for the HP-% overlay on the draw path (`:448`).
- `findNearestEnemies` allocates a sorted list per Ziggurat shot (`:981`); projectile-trail spawn re-iterates the full entity list per projectile (`:399`).
- Recursive bounce-projectile full-scan per hit + unbounded SCATTER child chains (`:899`).
- Fixed-timestep loop has **no max-frame-skip cap** → spiral-of-death risk at 4× after a stall (`GameLoopThread.kt:38`).
- `deleteAllData()` runs DB-close + file-delete + Keystore `deleteEntry` synchronously on the main thread (`DataDeletionManager.kt:26` — one-time, behind 2 dialogs, so Low).
- `DatabaseKeyManager.getPassphrase` runs SharedPreferences read + Keystore decrypt + potential full DB wipe on the first DAO-injecting thread.
- Labs/Missions recompose every second via a 1 Hz tick in the `uiState` combine.
- Cards/Labs/Store use non-lifecycle `collectAsState` (recompose while stopped).
- **`WalkingHistoryChart` uses `Color.hashCode()` as the Paint ARGB color** → wrong label colors (`:59`).
- Foreground-service `onCreate()` blocks on a 5-second `CountDownLatch` sensor read; `StepSyncWorker` blocks its worker thread the same way (`StepCounterService.kt:78`, `StepSyncWorker.kt:96`).
- `render_play_store_icon.py:129` uses a 4.19M-iteration Python pixel loop instead of a vectorized fill.

**Technical Debt (27):**
- ~**110 hardcoded user-facing Compose strings** remain (lint guard is XML-only by design — `StoreScreen.kt:50` etc.); nav labels & tier/biome names hardcoded English in non-UI layers.
- **Exponential cost formula duplicated across 3 classes** (`CalculateUpgradeCost`/`CalculateResearchCost`/…).
- Pervasive **swallow-all `catch (_: Exception) {}`** in the credit follow-on pipeline hides supply-drop/economy/widget failures (`DailyStepManager.kt:195-236`).
- `Converters.kt` TypeConverters throw uncaught on malformed JSON map columns.
- **`generate_sfx.py` writes raw WAV bytes into `*.ogg` files** (the converter is a dead no-op).
- `CardsViewModel.equipCard` can exceed the 3-card cap under double-tap.
- **No code-coverage tooling** (no Jacoco/Kover; CI can't see untested money/crypto code).
- Reflection-based `GameEngineTest` couples to private members.
- **Health Connect pinned to an alpha release** (`1.2.0-alpha02`) shipped to prod.
- Release lane signs/ships an AAB that is **never lint-checked nor release-variant-compiled** before signing (`release.yml:33`).
- `TYPE_STEP_COUNTER` reboot reset not handled in the worker catch-up path → silent step loss / unrecoverable baseline after reboot.
- Several safety-critical classes untested (`DatabaseKeyManager` crypto path, `AntiCheatPreferences`, `WalkingEncounterRepositoryImpl` write paths); `StepVelocityAnalyzer` both-flags `0.0` branch never asserted; `EscrowLifecycleTest` bypasses Room's transaction; `grantOnceAtomic` concurrent-race guarantee only tested sequentially.
- `wipeDatabaseFile` deletes `-wal`/`-shm` but not the legacy `-journal` companion.
- Music volume slider in Settings doesn't affect live `MusicManager` until next Activity resume.

---

## ✅ Verified-clean / rejected (rigor note)
- **Gradle wrapper-jar validation "missing"** → **false positive.** The finder saw no `wrapper-validation` action, but all workflows use `gradle/actions/setup-gradle@v6.1.0`, which validates wrapper checksums by default *before* `./gradlew` runs (and before keystore decode in the release lane). Already mitigated.
- **No committed secrets** — `.gitignore` correctly covers `keystore.properties`, `*.jks`, `local.properties`, `adi-registration.properties`; a secrets sweep of tracked files found nothing. CI actions are SHA-pinned; release secrets are injected via env, not echoed to logs.
- The **`grantOnceAtomic` billing path itself is correct** (atomic upsert + guard + wallet credit in one transaction) — the Season Pass bug (H2) is in the *expiry derivation*, not the grant.

---

## Top 5 most critical issues

1. **H1 — Daily-mission double-tap currency dupe** (`MissionsViewModel.kt:96`). A normal user mints premium (real-money-equivalent) Gems with two taps. Fix with the existing atomic-claim pattern + a `_processing` guard.
2. **H2 — Season Pass subscribers lose paid access after 30 days while billed monthly** (`BillingManagerImpl.kt:236`). Direct harm to paying customers and a refund/review-rating risk. Stop deriving SUBS validity from a client-side timer; refresh from Play state every reconcile.
3. **The non-atomic spend/grant cluster (M1, M2, M3, M4, M5)** — five more normal-user economy dupes/free-item bugs sharing one root cause: **`spendGems`/`spendPowerStones` discard the guarded DAO's success result.** One repository-layer change (return + gate on the Boolean) closes most of them.
4. **M7 — Unsynchronized concurrent step ingestion** (`DailyStepManager.kt:69-160`). Foreground service + WorkManager mutate the same singleton with no lock → corrupted daily stats and wallet/row divergence. Mutex or atomic UPSERT.
5. **M9 — Untested destructive Room migrations** (`Migrations.kt:81,136`). The two recreate-table data-split migrations run on every real user upgrade with zero `MigrationTestHelper` coverage; a future edit could silently corrupt wallets/inventory on update, invisible to CI.

**Two systemic recommendations:**
- **(a) Finish applying the atomic-claim/spend pattern uniformly** — audit every `addGems`/`addPowerStones`/`addSteps`/`markOwned`/`markClaimed` call site and ensure each is gated by a guarded `@Transaction` returning rows-affected, plus a ViewModel `_processing` guard. The team already has the idiom; it's just not applied everywhere.
- **(b) Add Kover with a coverage floor** on `data/billing`, `data/local`, `data/sensor`, `data/healthconnect` so untested money/crypto paths can't merge silently.

---

*Audit produced via multi-agent find→adversarial-verify orchestration: 63 raw findings, 62 confirmed/adjusted, 1 rejected. Every confirmed finding was independently re-verified against the cited code under the offline-game threat model.*
