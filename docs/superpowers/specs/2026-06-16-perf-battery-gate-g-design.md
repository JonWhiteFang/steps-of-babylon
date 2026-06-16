# Design Spec — #26 Performance & Battery (Gate G, in-repo slice)

**Date:** 2026-06-16
**Issue:** [#26](../../../issues/26) — "Improve mobile runtime performance, startup time, and battery efficiency" (roadmap item V1X-23)
**Gate:** Closed-Test Readiness Gate **G. Performance & battery** (`docs/plans/plan-FORWARD.md`)
**Status:** spec — adversarial-reviewed (43 raised → 23 surviving → synthesized); ready for plan.

> **Numbering key (read first — two collision-prone schemes are in play):**
> - **`A28`/`A29`/`A30`/`A31`/`A11`/`A18`** = *audit-report finding* numbers from
>   `docs/external-reviews/2026-06-10-multi-agent-code-audit.md` (the perf hotspots this spec fixes).
> - **`#118`/`#124`/`#125`/`#127`/`#128`/`#146`** = *GitHub issue* numbers (fragile zones / trackers).
> - These are **different namespaces**: e.g. GitHub issue #29 is "Workshop decision support" (MERGED
>   2026-06-16), unrelated to audit finding `A29` (the `distinctUntilChanged` item). Audit finding `A11`
>   is tracked under GitHub issue **#125** (already closed), NOT under #128 — so the deferred
>   `getAliveEnemies` work is NOT a #128 item (see §1).

---

## 1. Problem & framing

Mobile incremental/walking games are opened in frequent short bursts and judged heavily on startup
speed, runtime smoothness, and battery cost. Issue #26 (V1X-23) asks for Baseline Profiles, startup
profiling, recomposition/bitmap/Room profiling, a battery audit, and OEM device validation.

**The fundamental scope boundary:** several of #26's acceptance criteria — overnight idle-drain,
background-process behaviour on Samsung/Xiaomi/OnePlus — **cannot be closed inside the repository.**
They require physical devices and developer judgment. This spec therefore scopes the **code-addressable
in-repo slice** and, as a PR deliverable, **will record** the device-measured battery/OEM line as a
**deferred manual pass** in `plan-FORWARD.md` — replacing Gate G's checkbox at `plan-FORWARD.md:66` with
a `- [deferred] …` line per the gate-maintenance convention (`plan-FORWARD.md:68-69`), so it is never
silently dropped. (That `[deferred]` line does not exist yet; adding it is part of this PR's definition
of done — see §6.)

**This round's scope (developer-chosen): measurement infrastructure + safe runtime fixes.**

In scope:
1. **Measurement infrastructure** — `androidx.profileinstaller` in `:app`, the `androidx.baselineprofile`
   plugin, a `:baselineprofile` generator module (commits `baseline-prof.txt`), and a `:macrobenchmark`
   module (cold-start `StartupTimingMetric` + a Home→Workshop→Battle journey). Generated/run **locally on
   a real device**; **not** gated in CI (emulator timing is noisy — Google's own guidance).
2. **Safe runtime fixes** — the already-identified, behaviour-preserving GC-churn hotspots that do NOT
   touch a fragile economy/concurrency invariant: audit finding **A28** (CollisionSystem per-frame list
   allocations) and **A31** (CHRONO_FIELD per-frame `Paint`), plus **A29** (missing
   `distinctUntilChanged` on the shared profile Flow) **if it lands with the mandatory distinct-emission
   test green** (see §4.3 — "no existing test breaks" is NOT a sufficient bar).
3. **Battery audit document** — a written inventory of every wake/wakeup source + a measurement procedure
   + candidate tunings stated as hypotheses (NOT applied this round).
4. **Startup baseline document** — the measured cold-start number (None vs BaselineProfiles).

Explicitly OUT of scope this round (deferred, with rationale):
- **Cadence tuning** (notification 30s→60s, HC sync 15→30 min). Behaviour-changing; needs device
  verification that cross-validation accuracy / step-freshness isn't degraded, which can't be fully done
  in-repo. The battery-audit doc specs it for a future PR.
- **Audit findings A11/A18 — caching `getAliveEnemies()` per frame.** Note the *allocation* half of A11
  (the per-call double-list-allocation in `getAliveEnemies`) is **already fixed and closed** — it is now a
  single-pass filter (`GameEngine.kt:834-840`, GitHub issue **#125**, PR #143). What A11/A18 additionally
  recommend — **caching one alive-enemy snapshot across the frame** and sharing it across orbs/ongoing
  UWs — is the part deliberately **forbidden by the R125 invariant**: `getAliveEnemies()` must be
  re-derived live on each call, never cached across the frame, because a shared snapshot would let one
  ongoing UW effect (BLACK_HOLE/POISON_SWAMP) re-pass another's corpses into `takeDamage`, double-crediting
  kills. (Post-#146, `EnemyEntity.takeDamage` also hard-guards `if (!isAlive) return 0.0` as
  defense-in-depth — but the no-cache live re-filter, guarded by `R125` GameEngineTest, remains the
  **primary** layer, and a "safe fixes" round leaves it untouched.) Doing the snapshot-sharing safely needs
  the #146-style "guard the corpse across the orb/UW hit paths" refactor — a behaviour-sensitive economy
  change. **Excluded from a "safe fixes" round**; tracked as a **dedicated follow-up** under GitHub issue
  #125's domain (NOT #128 — the #128 tracker is the *other* audit Lows, not A11).
- **Recomposition / bitmap profiling fixes** — the `FrameTimingMetric` tooling this round produces gives
  jank / frame-duration visibility but **not per-Composable recomposition counts**. Genuine recomposition
  profiling (Compose compiler metrics/reports via `composeCompiler { reportsDestination/metricsDestination }`,
  or Layout-Inspector recomposition counts) is an explicit **named follow-up**, not this PR. (Bitmap
  allocation: there are currently **zero** `Bitmap`/`drawBitmap`/`BitmapFactory`/`ImageBitmap` uses in the
  render path — all battle art is `Canvas` vector drawing — so the issue's "audit bitmap allocations" line
  is a confirmed no-op today, recorded as such rather than deferred.)
- **OEM device matrix + overnight idle-drain** — physical-device, developer-judgment slice.

### Ground-truth verification (performed while writing this spec, against `main`)

- No `:macrobenchmark` / `:baselineprofile` module, no `androidx.profileinstaller`, no `baseline-prof.txt`,
  no startup tracing exist today (`settings.gradle.kts` includes only `:app`; catalog has no profile/
  benchmark entries).
- Instrumented CI lane runs `connectedDebugAndroidTest` on an **API-34 emulator** (reactivecircus runner,
  `.github/workflows/instrumented.yml`). A Macrobenchmark module *could* slot into this infra (V1X-08
  enabled `androidTest`), but emulator timings are unreliable → not gated.
- **A28 is still open:** `CollisionSystem.checkCollisions` (`CollisionSystem.kt:24-26`) allocates three
  `filterIsInstance<…>().filter{}` lists per call; the call site is `GameEngine.kt:478`, already **inside
  `synchronized(entitiesLock)`** (lock taken at `GameEngine.kt:441`). `CollisionSystem` is a Kotlin
  `object` (singleton) — scratch lists therefore must live on the **engine instance**, not the object.
- **A31 is still open:** `GameEngine.render()` allocates `android.graphics.Paint()` per frame for the
  CHRONO_FIELD overlay (`GameEngine.kt:513`, inside the `if (chronoActive)` block at `:512`), beside
  already-cached `hpPercentPaint`/`bossCountdownPaint`.
- **A30 is ALREADY FIXED** — `StatsViewModel.buildBars` QUARTER branch (`StatsViewModel.kt:103-122`,
  weekly buckets returned through `:131`) already parses each row once in a single O(history) pass
  (carries a `#30` comment). **Dropped from this spec's fix set.**
- **A29 is still open:** `distinctUntilChanged` appears nowhere in `app/src/main`. `PlayerRepositoryImpl`
  `observeProfile/observeWallet/observeTier` (`:18-26`) all map `dao.get()` with no distinct guard.

---

## 2. Goals / non-goals

**Goals**
- A repeatable, committed Baseline Profile covering the most-used path (**Home → Workshop → Battle**).
- A documented cold-start baseline number (None vs BaselineProfiles) → satisfies "startup metrics documented".
- A documented battery-audit *process* + wake-source inventory → satisfies "battery profiling process exists"
  and "hotspots documented and prioritised".
- Eliminate the two confirmed, fragile-zone-safe GC-churn hotspots (A28, A31) with **provably identical
  behaviour**.
- Keep JVM + CI green; do not weaken any fragile-zone invariant (#118, #124, #125).

**Non-goals**
- Closing the device-measured battery / OEM-matrix Gate-G line (manual, deferred).
- Closing the Gate-G frame-rate line (low-end-device 2×/4× verdict — device-only manual pass; this PR
  contributes the `FrameTimingMetric` journey benchmark + the A28/A31 GC-churn reductions, but the
  verdict itself is not closed in-repo — see §6).
- Any cadence/behaviour tuning of services or workers.
- The `getAliveEnemies()` caching refactor (A11/A18) — excluded as above.
- Adding perf-timing assertions to the CI gate.

---

## 3. Architecture — measurement infrastructure

Follows the AndroidX-recommended **two-test-module** split (baseline-profile generation and
macrobenchmark are distinct plugin responsibilities and are kept separate).

### 3.1 `:app` changes
- Apply `alias(libs.plugins.androidx.baselineprofile)`. This plugin, applied to a `com.android.application`
  module, **auto-generates** the `nonMinifiedRelease` and `benchmarkRelease` variants (derived from
  `release`) used to generate the profile and run macrobenchmarks — we do **not** hand-author a
  `create("benchmark")` build type. (Note: those variants inherit `release`'s `signingConfig`,
  `isMinifyEnabled`, the AdMob/license `buildConfigField`s, and the `ndk.debugSymbolLevel` block — that
  inheritance is exactly why the #124 guard reconciliation in §3.5 is required.)
- Add `implementation(libs.androidx.profileinstaller)` (so the committed profile is installed at runtime).
- Add a `baselineProfile { }` consumer block referencing `:baselineprofile`. **Set
  `automaticGenerationDuringBuild = false`** so an ordinary `assembleRelease`/`bundleRelease` does NOT
  pull a profile-generation/benchmark task into the release graph (keeps the shipping lane clean and keeps
  the #124 guard's task-graph reasoning simple).
- The committed profile lands at `app/src/main/baseline-prof.txt` (and `startup-prof.txt` if generated).

### 3.2 New module `:baselineprofile` (`com.android.test`)
- `plugins { }` block: `alias(libs.plugins.android.test)` + `alias(libs.plugins.kotlin.android)` +
  `alias(libs.plugins.androidx.baselineprofile)`. (The `kotlin.android` plugin is **required** — a
  `com.android.test` module does not engage Kotlin the way `:app`'s application+compose setup does, so its
  `.kt` sources won't compile without it.) `targetProjectPath = ":app"`.
- `BaselineProfileGenerator` (Macrobenchmark `BaselineProfileRule`) drives the critical user journey:
  cold launch → Home → navigate to Workshop → enter a Battle → let a wave run briefly. This is the path
  whose classes/methods get AOT-compiled.
- Output consumed by `:app`'s `baselineProfile { }` and committed.

### 3.3 New module `:macrobenchmark` (`com.android.test`)
- `plugins { }` block: `alias(libs.plugins.android.test)` + `alias(libs.plugins.kotlin.android)`.
  `targetProjectPath = ":app"`; targets the `:app` `benchmarkRelease` variant (profileable, non-debuggable,
  minified — auto-generated by the baselineprofile plugin per §3.1).
- `StartupBenchmark` — `StartupTimingMetric`, cold/warm/hot, comparing `CompilationMode.None()` vs
  `CompilationMode.Partial(BaselineProfiles())` to quantify the profile's win.
- `JourneyBenchmark` — `FrameTimingMetric` over the Home→Workshop→Battle journey (jank visibility).

### 3.4 Catalog / Gradle plumbing
- New `gradle/libs.versions.toml` **library** entries: `androidx.profileinstaller`,
  `androidx.benchmark.macro.junit4`, `androidx.test.uiautomator` (UiAutomator drives the journey).
- New `gradle/libs.versions.toml` **plugin** aliases (catalog-only, never inline `id()`):
  - `androidx-baselineprofile = { id = "androidx.baselineprofile", version.ref = "<baselineprofile>" }`
  - `android-test = { id = "com.android.test", version.ref = "agp" }` — **missing today**; required by both
    new modules.
  - `kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }` — **missing today**;
    required so the new modules' `.kt` sources compile (build-breaking if omitted).
  - The standalone `androidx.benchmark` plugin is **not** applied — `androidx.baselineprofile` on `:app`
    plus the `androidx.benchmark.macro.junit4` dependency in `:macrobenchmark` is sufficient.
- `settings.gradle.kts` includes `:baselineprofile` and `:macrobenchmark`.

### 3.5 Fragile-zone reconciliation — #124 license-key guard (load-bearing)
The `androidx.baselineprofile` plugin on `:app` auto-generates the `benchmarkRelease` and
`nonMinifiedRelease` variants (§3.1), whose Gradle task names (`assembleBenchmarkRelease`,
`bundleNonMinifiedRelease`, etc.) **match the existing `^(bundle|assemble|package).*Release$` regex** in
`app/build.gradle.kts:158` — which would falsely trip the **#124 fail-closed license-key guard** (since
these variants inherit `release`'s blank-by-default `play.licenseKey`) and break every benchmark build.
(The profile *generation* tasks — `generate*BaselineProfile`/`collect*BaselineProfile` — end in
`Profile`, so they do NOT match the regex and are not a concern; only the `*Release` assemble/bundle tasks
for the two new variants are.)

**Fix (preserves the #124 invariant) — explicit predicate:** keep the existing **broad**
`^(bundle|assemble|package).*Release$` match (so a future product-flavor release task such as
`bundleProdRelease` is still caught — the load-bearing rationale at `app/build.gradle.kts:154-157`) and
**add a per-task, case-sensitive exclusion**:

```kotlin
val releaseTask = Regex("^(bundle|assemble|package).*Release$")
val buildsRelease = allTasks.any { t ->
    releaseTask.matches(t.name) &&
        !t.name.contains("Benchmark") &&
        !t.name.contains("NonMinified")
}
```

The exclusion is applied **per task inside the `any { }`** (not as a whole-graph "is any benchmark task
present?" check) — so a graph that contains BOTH `bundleRelease` AND `assembleBenchmarkRelease` (e.g. a
developer assembling a real release while a benchmark task is also scheduled) **still hard-fails on the
blank key**, because the shippable `bundleRelease` task matches the regex and carries neither excluded
token. A real release with a blank `play.licenseKey` therefore still hard-fails — the #124 invariant is
unchanged; we only stop the benchmark/non-minified variants from a false positive. `generate*BaselineProfile`
tasks need no exclusion (they fail the regex).

- This is a fragile-zone touch, called out for the Adversarial Review Gate.
- **Validate the actual task names before locking the predicate:** the plan must, after wiring the plugin
  + both modules, run `./gradlew tasks --all` (graph-wide — note `allTasks` spans every project, not just
  `:app`) and confirm every task name matching `^(bundle|assemble|package).*Release$` is either a genuine
  shippable task or carries a `Benchmark`/`NonMinified` token. If a non-shippable match lacks both tokens,
  widen the exclusion accordingly and record it.
- **Guard-for-the-guard verification** (manual this round — there is no `buildSrc`/build-logic/GradleTestKit
  harness, so a JVM unit test of build-logic is out of scope): document and run three checks —
  (a) `bundleRelease` with a blank key **throws**; (b) `assembleBenchmarkRelease` / `assembleNonMinifiedRelease`
  with a blank key **succeed** (don't throw); (c) a **combined** graph (`bundleRelease` + a benchmark task)
  with a blank key **still throws** (proves the per-task, not whole-graph, semantics).

### 3.6 CI posture
- PR gate + instrumented lane must continue to compile the workspace and stay green. Because the existing
  PR-gate `assembleDebug` and the `connectedDebugAndroidTest` instrumented lane do **not** build the new
  modules' release-derived variants, **add an explicit PR-gate compile step** —
  `./gradlew :baselineprofile:assemble :macrobenchmark:assemble` (or the specific benchmark/nonMinified
  variant the modules target) — so the new Kotlin/test sources are genuinely type-checked in CI.
- **No perf-timing assertions are added to CI** (emulator numbers flake). Baseline-profile generation and
  benchmarks run **locally on a real device** by the developer.
- Verify the new `com.android.test` modules don't perturb `assembleDebug` / `testDebugUnitTest` / the
  release lane (`bundleRelease`).

---

## 4. Architecture — safe runtime fixes

All three are behaviour-preserving and each carries an explicit risk gate.

### 4.1 A28 — CollisionSystem per-frame list allocations
- **Site:** `CollisionSystem.checkCollisions` (`CollisionSystem.kt:24-26`); caller `GameEngine.kt:478`,
  inside `synchronized(entitiesLock)` (`:441`).
- **Fix:** replace the three `filterIsInstance<…>().filter{}` allocations with **three reusable
  `ArrayList` scratch buffers owned by the `GameEngine` instance** (NOT the `CollisionSystem` object —
  it's a singleton; instance-owned buffers avoid cross-engine shared mutable state). Per tick: `clear()`
  the three buffers, do **one `for (e in entities)` partition pass** by type + `isAlive`, hand the
  buffers to `simulation.detectProjectileEnemyHits` / `detectZigguratHits`.
- **Signature (literal):** the partition moves to the caller under-lock and `checkCollisions` receives the
  three pre-filled typed lists — `entities: List<Entity>` is removed:
  ```kotlin
  fun checkCollisions(
      simulation: Simulation,
      projectiles: List<ProjectileEntity>,
      enemies: List<EnemyEntity>,
      enemyProjectiles: List<EnemyProjectileEntity>,
      zigX: Float, zigY: Float, zigWidth: Float,
      onProjectileHitEnemy: (ProjectileEntity, EnemyEntity) -> Unit,
      onEnemyProjectileHitZiggurat: (EnemyProjectileEntity) -> Unit,
  )
  ```
  `CollisionSystem` stays a stateless `object`; the buffers live on `GameEngine`.
- **Single-fill semantics (load-bearing for byte-identity):** the `enemies` buffer is filled **once** by
  the partition pass and that **same** buffer instance is passed to the projectile→enemy sweep — matching
  today's behaviour where one `enemies` snapshot (`enemies` val at `CollisionSystem.kt:25`) serves the
  whole call. Do **not** re-derive or re-filter mid-sweep.
- **Risk gate:** the partition pass iterates `entities` → **must remain under `entitiesLock`** (#118).
  The simulation sweep contract is unchanged (it already receives `List<…>`). Behaviour must be
  byte-identical. This is **not** the A11/#125 cross-frame caching hazard — the buffers are refilled every
  tick and never retained across `update()` calls.
- **Regression (JVM/Robolectric):** the **existing** `SimulationTest` collision-sweep tests
  (`detectProjectileEnemyHits` / `detectZigguratHits`) stay green; **add a new test** (a new
  `CollisionSystemTest`, or extend `SimulationTest`/`GameEngineTest`) asserting:
  1. the scratch-buffer partition yields the same projectile→enemy and enemy-projectile→ziggurat hit
     outcomes as the old `filterIsInstance` path, **incl. dead-entity exclusion**;
  2. buffers are `clear()`ed between ticks (no stale carry-over);
  3. **mid-sweep death (corpse) parity** — an enemy alive at partition time that is killed by an earlier
     projectile must still be present in the buffer for a later projectile in the same `checkCollisions`
     call, and `onDeath` must fire **exactly once** (reuse the `R146` corpse-hit assertion pattern at
     `GameEngineTest.kt:649` / `:673`, run against the new scratch-buffer path) — proves the buffer reuse
     does not reintroduce a double-credit.
  4. (optional strengthening) the partition + `clear()` happen inside `synchronized(entitiesLock)` and
     `CollisionSystem` holds no per-call scratch state.

### 4.2 A31 — CHRONO_FIELD per-frame Paint allocation
- **Site:** `GameEngine.render()` (`GameEngine.kt:513`, inside the `if (chronoActive)` block at `:512`).
- **Fix:** hoist the overlay `Paint` to a cached `private val chronoOverlayPaint` field next to
  `hpPercentPaint`/`bossCountdownPaint`; reuse it in `render()`.
- **Colour:** preserve the current observable colour value exactly (`0x222196F3`). The audit notes this
  literal lacks the explicit `.toInt()`/alpha the other ARGB literals carry — **do not silently change
  the rendered colour**; record the alpha question in the plan and resolve it only with an intentional,
  reviewed decision (default: keep current behaviour).
- **Risk gate:** pure render path, no game-logic change.
- **Acceptance (objective + visual):** the project already has `GameSurfaceViewTest`
  (`@RunWith(RobolectricTestRunner) @Config(sdk=[34])`) — add a Robolectric test that exposes
  `chronoActive=true` via a `@VisibleForTesting` seam (mirroring the existing `initEngineIfNeeded` pattern),
  calls `render(mockCanvas)` twice, and asserts (a) `canvas.drawRect` is invoked with a `Paint` of colour
  `0x222196F3` + `FILL` style, and (b) the **same `Paint` instance** is captured on both calls (identity
  check) — directly proving the per-frame allocation is gone. On-device visual verification (CHRONO_FIELD
  UW active) remains as a secondary check.

### 4.3 A29 — missing distinctUntilChanged on the shared profile Flow (include-if-clean)
- **Site:** `PlayerRepositoryImpl.observeProfile/observeWallet/observeTier` (`:18-26`), all mapping
  `dao.get()` (Room re-emits on every `player_profile` write, incl. high-frequency counter writes).
- **Fix:** map to the narrow projection **then** `.distinctUntilChanged()` (the distinct must sit **after**
  the full `map`, so it dedupes the projected value the consumer sees, not the raw entity), so a
  step-balance write doesn't wake a tier-only observer and unrelated counter writes don't re-fire every
  screen's combine mapper.
- **Risk gate:** behaviour-preserving for genuine value changes — it only suppresses no-op re-emissions.
  But this Flow is consumed by every screen ViewModel. **There is NO pre-existing safety net for this**:
  `PlayerRepositoryImplTest` (`:44/:58/:72`) only reads `.first()` (one emission), and ViewModel tests use
  `FakePlayerRepository` (which has no distinct guard and emits conflated `StateFlow` state). So "no
  existing test breaks" is **trivially satisfiable and does NOT prove behaviour preservation.**
- **Decision rule (revised):** include A29 **only if the mandatory new distinct-emission test below lands
  green**; that test — not "no existing test breaks" — is the bar. If writing it surfaces any real
  behavioural dependence on duplicate emissions, **defer A29 to a follow-up** (it is the lowest-value of
  the three and not required to satisfy Gate G).
- **Regression (MANDATORY when A29 is included):** a `PlayerRepositoryImpl` test against the **real**
  `PlayerRepositoryImpl` (not `FakePlayerRepository`), with a fake/in-memory `PlayerProfileDao`, asserting:
  (a) two identical-value emissions from the DAO Flow produce **one** downstream emission on
  `observeProfile`/`observeWallet`/`observeTier`; (b) a genuine value change **still** emits. Use
  `coroutines-test` + Flow collection (the repo has no Turbine — do not introduce it; collect into a list
  under `runTest`).

---

## 5. Battery audit (document-and-defer — no behaviour change this round)

Deliverable: **`docs/performance/battery-audit.md`**.

- **Wake/wakeup-source inventory (code-grounded, with file:line + current constant):**
  - `StepCounterService` foreground service (continuous while moving).
  - `StepNotificationManager` notification throttle — **30 s** (`StepNotificationManager.kt:25`).
  - `WidgetUpdateHelper` — **60 s** (`WidgetUpdateHelper.kt:13`).
  - `StepSyncWorker` periodic WorkManager cadence — **15 min** (`StepSyncScheduler.kt:14`).
  - Health Connect read frequency (driven by the worker).
  - `SmartReminderManager`, boot receiver.
- **`TYPE_STEP_COUNTER` note:** hardware-batched by Android; no app-side polling to optimise (standing
  rationale, kept).
- **Wake-lock audit (issue scope item):** confirmed **zero** explicit `WakeLock` acquisitions in app
  source — record this as a no-op finding rather than a deferred task (the foreground service is the
  wakefulness mechanism, not a `PowerManager.WakeLock`).
- **Sensor-polling audit (issue scope item):** covered by the `TYPE_STEP_COUNTER` note above — the sensor
  is hardware-batched, so there is no app-side polling cadence to tune; recorded explicitly so the audit
  doc answers the issue's "sensor polling audits" line.
- **Candidate tunings — stated as hypotheses, NOT applied:** notification 30 s→60 s; HC sync 15→30 min
  (≈50 % fewer wakeups). Each tagged with its **risk** (cross-validation accuracy / step-freshness UX)
  and the **device measurement** that must confirm it before shipping. This section is the spec for a
  future cadence-tuning PR.
- **Measurement procedure:** Battery Historian / `adb shell dumpsys batterystats`, overnight idle-drain
  protocol, and the OEM matrix (Samsung / Xiaomi / OnePlus / Pixel) — explicitly flagged as the
  **physical-device, developer-judgment** slice that closes Gate G's battery line and cannot be done
  in-repo.

---

## 6. Testing & acceptance

**JVM (the gate that must stay green):**
- A28: existing `SimulationTest` sweep tests stay green + a **new** partition/scratch-buffer test (new
  `CollisionSystemTest`, or extend `SimulationTest`/`GameEngineTest`) covering identical outcomes,
  dead-entity exclusion, buffer clearing, and the mid-sweep-death corpse-hit parity (§4.1).
- A29 (if included — see §4.3 decision rule): **mandatory** `PlayerRepositoryImpl` distinct-emission test
  against the real repo (identical-value → one emission; real change → still emits).
- A31: Robolectric `GameSurfaceView`/render identity-check test (same `Paint` instance across two
  `render()` calls, colour `0x222196F3` + `FILL`) — §4.2; plus on-device visual as a secondary check.
- Headline test count rises by the new tests; update CLAUDE.md's count line.

**Macrobenchmark / Baseline Profile (local, real device — not CI):**
- Generate + commit `baseline-prof.txt` for the Home→Workshop→Battle journey.
- Capture the cold-start baseline (None vs BaselineProfiles) into **`docs/performance/startup-baseline.md`**.
  Record the **honesty caveat**: the None-vs-BaselineProfiles delta is an **upper bound** on the
  real-world startup win — `CompilationMode.None()` models an install with no Play cloud profile; most
  Play installs eventually receive one. The committed profile still delivers on day-one / sideload /
  cold-cache launches (`CompilationMode.None()` remains the correct AndroidX reference baseline; only the
  documented number needs the caveat).

**CI:**
- PR gate compiles the new modules via the explicit `./gradlew :baselineprofile:assemble
  :macrobenchmark:assemble` step (§3.6) and stays green; instrumented lane unchanged.
- No perf-timing assertions added.
- Verify the #124 guard change (manual this round — §3.5 guard-for-the-guard): (a) `bundleRelease` blank
  key **throws**; (b) `assembleBenchmarkRelease`/`assembleNonMinifiedRelease` blank key **succeed**;
  (c) combined `bundleRelease`+benchmark graph blank key **still throws**.

**Fragile-zone touches flagged for review:**
- #124 guard predicate (broad regex + per-task `Benchmark`/`NonMinified` exclusion — must preserve
  fail-closed on real + future-flavor releases).
- #118 `entitiesLock` discipline on the A28 partition pass (must stay under-lock).
- #125 exclusion rationale (we are NOT caching `getAliveEnemies()`; A28 buffers are per-tick, not
  cross-frame).

**Acceptance for this PR = the Gate-G in-repo slice:**
- Baseline Profile implemented + committed.
- Startup baseline documented (with the upper-bound caveat).
- Battery-audit process + wake-source inventory documented.
- A28 + A31 fixed (A29 if its mandatory test is green); JVM + CI green.
- **`plan-FORWARD.md` edited**: Gate G's battery line (`:66`) replaced with a `- [deferred] …` line for the
  device-measured battery / OEM-matrix slice; the frame-rate line (`:65`) annotated to note the in-repo
  contribution (journey benchmark + A28/A31) vs the device-only verdict. Both remain **deferred, not
  dropped** (developer judgment; cannot close in-repo).

---

## 7. Open questions for the review gate (resolved in synthesis)

1. **#124 guard predicate** — RESOLVED: keep the broad `^(bundle|assemble|package).*Release$` match +
   per-task `!contains("Benchmark") && !contains("NonMinified")` exclusion (preserves future-flavor
   coverage; per-task semantics keep a combined graph fail-closed). Validate actual task names with
   `./gradlew tasks --all` before locking. (§3.5)
2. **One module vs two** — RESOLVED: two modules (`:baselineprofile` + `:macrobenchmark`), the
   AndroidX-recommended split. (§3.2/§3.3)
3. **A29 in or out** — RESOLVED to a gate, not a guess: include **only if** the mandatory distinct-emission
   test (against the real repo, since no pre-existing safety net exists) lands green; else defer. (§4.3)

---

## 8. References

- Issue #26 / V1X-23 (`docs/plans/plan-V1X-roadmap.md:1392`).
- Gate G (`docs/plans/plan-FORWARD.md:64`).
- Audit findings **A11/A18/A28/A29/A30/A31** (sections in
  `docs/external-reviews/2026-06-10-multi-agent-code-audit.md`; audit-report numbers, NOT GitHub issues).
- Fragile zones GitHub issues #118 / #124 / #125 / #146 (`docs/agent/STATE.md`, CLAUDE.md).
