# Perf & Battery (Gate G, in-repo slice) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the in-repo slice of GitHub issue #26 / Gate G — a Baseline Profile + Macrobenchmark measurement harness, the two fragile-zone-safe GC-churn fixes (audit A28, A31) and the conditional A29 Flow-dedup, plus the battery-audit + startup-baseline docs and the `plan-FORWARD.md` deferral edits — without weakening any fragile-zone invariant (#118, #124, #125).

**Architecture:** Add `androidx.profileinstaller` + the `androidx.baselineprofile` plugin to `:app`, and two new `com.android.test` modules (`:baselineprofile`, `:macrobenchmark`) generated/run locally on a real device (never CI-gated on timings). Apply behaviour-preserving runtime fixes: replace per-frame `filterIsInstance` allocations in the collision sweep with engine-owned scratch buffers (A28), cache the CHRONO_FIELD overlay `Paint` (A31), and add `distinctUntilChanged` to the shared profile Flow (A29, only if its mandatory test passes). Reconcile the #124 license-key fail-closed guard so benchmark variants don't trip it while real releases still hard-fail.

**Tech Stack:** Kotlin 2.3.0 / AGP 9.0.1 / Gradle 9.5.1 (Kotlin DSL) · version catalog at `gradle/libs.versions.toml` · `androidx.baselineprofile` + `androidx.benchmark:benchmark-macro-junit4` **1.4.1** (stable; `newDsl = false` AGP-9 workaround) · `androidx.profileinstaller:profileinstaller` **1.4.1** · `androidx.test.uiautomator:uiautomator` **2.3.0** · `com.android.test` plugin **9.0.1** · JVM tests: JUnit Jupiter + kotlinx-coroutines-test + Robolectric · build via `./run-gradle.sh <task>`.

**Spec:** `docs/superpowers/specs/2026-06-16-perf-battery-gate-g-design.md` (adversarial-reviewed). **Numbering key:** `A28`/`A29`/`A30`/`A31`/`A11`/`A18` = *audit-report finding* numbers (`docs/external-reviews/2026-06-10-multi-agent-code-audit.md`); `#118`/`#124`/`#125`/`#146` = *GitHub issue* numbers. These are different namespaces — do not conflate them with GitHub issue numbers in commit messages or code comments.

**Ordering rationale:** Tasks 1–3 (the #124 guard reconciliation + catalog + `:app` wiring) MUST land before Tasks 4–5 (the benchmark modules), because creating the `benchmarkRelease`/`nonMinifiedRelease` variants without the narrowed guard would break every build that touches a `*Release` task. The runtime fixes (Tasks 6–8) are independent of the infra and could run in any order, but are sequenced after so the build is green throughout. Tasks 9–10 (docs) and 11 (doc-sync + memory) close out.

---

## Task 1: Narrow the #124 license-key guard to exclude benchmark variants (FRAGILE ZONE)

This MUST land first — Tasks 4–5 create `*BenchmarkRelease`/`*NonMinifiedRelease` tasks that would otherwise trip the existing guard. We change the guard in isolation here, while no such tasks exist yet, and prove the change is behaviour-preserving for the current shippable tasks.

**Files:**
- Modify: `app/build.gradle.kts:153-167` (the `gradle.taskGraph.whenReady { }` block)

**Background (current code, `app/build.gradle.kts:158-159`):**
```kotlin
val releaseTask = Regex("^(bundle|assemble|package).*Release$")
val buildsRelease = allTasks.any { releaseTask.matches(it.name) }
```
The guard throws when a release-artifact task is in the graph and `play.licenseKey` is blank (#124 fail-closed — prevents shipping a release with purchase-signature verification disabled). The fragile-zone rule (STATE.md): *don't weaken either the `taskGraph` guard or the `release.yml` secret step, or fail-open could ship.*

- [ ] **Step 1: Make the predicate per-task with benchmark/non-minified exclusion**

In `app/build.gradle.kts`, replace the `buildsRelease` line (`:159`) so the exclusion is applied **per task inside the `any { }`** (NOT as a separate whole-graph "is a benchmark task present?" check — that would let a real `bundleRelease` slip through when a benchmark task is also scheduled):

```kotlin
val releaseTask = Regex("^(bundle|assemble|package).*Release$")
// #124 + #26: keep the BROAD release-task match (so a future product-flavor release task such as
// `bundleProdRelease` is still caught — see the comment below), but exclude the AndroidX benchmark /
// baseline-profile variant tasks (`assembleBenchmarkRelease`, `bundleNonMinifiedRelease`, …). The
// androidx.baselineprofile plugin auto-generates `benchmarkRelease`/`nonMinifiedRelease` from `release`,
// so they inherit the blank-by-default play.licenseKey and would otherwise false-trip this fail-closed
// guard on every benchmark build. The exclusion is PER TASK: a graph containing BOTH `bundleRelease`
// AND `assembleBenchmarkRelease` still hard-fails on a blank key, because the shippable `bundleRelease`
// task matches the regex and carries neither excluded token. `generate*BaselineProfile` tasks end in
// `Profile` and never match the regex, so they need no exclusion.
val buildsRelease = allTasks.any { t ->
    releaseTask.matches(t.name) &&
        !t.name.contains("Benchmark") &&
        !t.name.contains("NonMinified")
}
```
Leave the existing `if (buildsRelease && ...) throw GradleException(...)` body (`:160-166`) unchanged.

- [ ] **Step 2: Verify the shippable-release guard still fires on a blank key**

The release lane builds via `bundleRelease`. With no `play.licenseKey` in `local.properties` (the default clean-clone state), confirm the guard still throws:

Run: `./run-gradle.sh bundleRelease 2>&1 | grep -i "play.licenseKey\|requires a non-blank\|BUILD"`
Expected: the build FAILS with the `Release build requires a non-blank 'play.licenseKey'` message (proves fail-closed is intact). (This is the existing behaviour — we are confirming the regex edit didn't weaken it.)

- [ ] **Step 3: Verify a normal debug build is unaffected**

Run: `./run-gradle.sh assembleDebug > /tmp/t1.log 2>&1 && tail -n 3 /tmp/t1.log`
Expected: BUILD SUCCESSFUL (the guard never matches `assembleDebug` — it doesn't end in `Release`).

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build(#26): narrow #124 license guard to exclude benchmark variants (keep fail-closed on shippable releases)"
```

> Note: the third guard-for-the-guard case (a `*BenchmarkRelease` task with a blank key must SUCCEED) cannot be tested until Task 4 creates that task. It is verified in Task 5, Step 6.

---

## Task 2: Add benchmark/profile entries to the version catalog

**Files:**
- Modify: `gradle/libs.versions.toml` (`[versions]`, `[libraries]`, `[plugins]`)

- [ ] **Step 1: Add version refs**

Under `[versions]` in `gradle/libs.versions.toml`, add (after the `androidxTestExtJunit` line):
```toml
# #26 Gate G — perf measurement infra. baselineprofile/benchmark 1.4.1 is the latest STABLE line;
# it predates AGP 9.0 GA so it needs `baselineProfile { newDsl = false }` (set in app/build.gradle.kts).
# These artifacts are used ONLY by the two non-shipping com.android.test modules + profileinstaller
# (which ships, and is stable 1.4.1). Revisit 1.5.x once it leaves alpha. uiautomator 2.3.0 matches
# the 1.4.x benchmark line.
benchmark = "1.4.1"
profileinstaller = "1.4.1"
uiautomator = "2.3.0"
```

- [ ] **Step 2: Add library entries**

Under `[libraries]` (after the `# Instrumented testing` block), add:
```toml
# #26 Gate G — perf measurement infra
androidx-profileinstaller = { group = "androidx.profileinstaller", name = "profileinstaller", version.ref = "profileinstaller" }
androidx-benchmark-macro-junit4 = { group = "androidx.benchmark", name = "benchmark-macro-junit4", version.ref = "benchmark" }
androidx-test-uiautomator = { group = "androidx.test.uiautomator", name = "uiautomator", version.ref = "uiautomator" }
```

- [ ] **Step 3: Add plugin aliases**

Under `[plugins]`, add (the `android-test` + `kotlin-android` aliases are NEW — the two new modules can't compile without them):
```toml
android-test = { id = "com.android.test", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
androidx-baselineprofile = { id = "androidx.baselineprofile", version.ref = "benchmark" }
```

- [ ] **Step 4: Verify the catalog parses**

Run: `./run-gradle.sh help > /tmp/t2.log 2>&1 && tail -n 3 /tmp/t2.log`
Expected: BUILD SUCCESSFUL (a malformed catalog fails configuration immediately).

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build(#26): add baselineprofile/benchmark/profileinstaller/uiautomator catalog entries"
```

---

## Task 3: Wire profileinstaller + baselineprofile plugin into :app

**Files:**
- Modify: `app/build.gradle.kts` (the `plugins { }` block `:3-9`; the `dependencies { }` block; add a `baselineProfile { }` block)

- [ ] **Step 1: Apply the baselineprofile plugin**

In `app/build.gradle.kts`, add to the `plugins { }` block (after `alias(libs.plugins.room)` at `:8`):
```kotlin
    alias(libs.plugins.androidx.baselineprofile)
```

- [ ] **Step 2: Add the profileinstaller runtime dependency + the baselineprofile producer**

In the `dependencies { }` block, after the WorkManager block (`:232`), add:
```kotlin
    // #26 Gate G: installs the committed baseline-prof.txt at runtime so the most-used path
    // (Home → Workshop → Battle) is AOT-compiled on first launch.
    implementation(libs.androidx.profileinstaller)

    // #26 Gate G: consumes the generated profile from :baselineprofile (added in Task 4).
    "baselineProfile"(project(":baselineprofile"))
```

- [ ] **Step 3: Add the baselineProfile DSL block with the AGP-9 workaround**

In `app/build.gradle.kts`, after the `room { }` block (`:199-201`), add a top-level block:
```kotlin
// #26 Gate G — baseline-profile consumer config. `newDsl = false` is the AGP-9 compatibility
// workaround required by the stable 1.4.1 baselineprofile plugin (the 1.4.x line predates AGP 9.0
// GA). `automaticGenerationDuringBuild = false` keeps profile generation OUT of the ordinary
// assemble/bundleRelease graph — generation is a deliberate local-device step (see the plan / docs),
// so the shipping lane stays clean and the #124 guard's task-graph reasoning stays simple.
baselineProfile {
    newDsl = false
    automaticGenerationDuringBuild = false
}
```

> Note: this references `project(":baselineprofile")`, which does not exist until Task 4. The build will not configure cleanly until Task 4's `settings.gradle.kts` include lands — so **do not run a full build between this task and Task 4.** Steps 4–5 below only stage + commit; the first green build is Task 4 Step 6.

- [ ] **Step 4: Stage the changes**

```bash
git add app/build.gradle.kts
```

- [ ] **Step 5: Commit (build verification deferred to Task 4)**

```bash
git commit -m "build(#26): apply baselineprofile plugin + profileinstaller to :app (newDsl=false AGP-9 workaround)"
```

---

## Task 4: Create the :baselineprofile generator module

**Files:**
- Create: `baselineprofile/build.gradle.kts`
- Create: `baselineprofile/src/main/AndroidManifest.xml`
- Create: `baselineprofile/src/main/java/com/whitefang/stepsofbabylon/baselineprofile/BaselineProfileGenerator.kt`
- Modify: `settings.gradle.kts:18` (add the `include`)

- [ ] **Step 1: Add the module to settings.gradle.kts**

Include ONLY `:baselineprofile` here (Task 5 Step 1 adds `:macrobenchmark` — a Gradle `include` of a
not-yet-created module dir fails configuration, so each module is included in the task that creates it).
Change the include line (`settings.gradle.kts:18`) from:
```kotlin
include(":app")
```
to:
```kotlin
include(":app")
include(":baselineprofile")
```

- [ ] **Step 2: Create the module build script**

Create `baselineprofile/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.whitefang.stepsofbabylon.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The app variant this module generates a profile against (the baselineprofile plugin
    // auto-creates `nonMinifiedRelease` on :app).
    targetProjectPath = ":app"
}

// Run the generator on a real device / non-rooted physical device or an AOSP/Play-image emulator.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
```

- [ ] **Step 3: Create the manifest**

Create `baselineprofile/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 4: Create the generator**

Create `baselineprofile/src/main/java/com/whitefang/stepsofbabylon/baselineprofile/BaselineProfileGenerator.kt`:
```kotlin
package com.whitefang.stepsofbabylon.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #26 Gate G — generates the Baseline Profile for the most-used path: cold launch → Home →
 * Workshop → Battle. Run on a connected device with:
 *   ./run-gradle.sh :baselineprofile:pixelProGenerateBaselineProfile   (or the connected-device task)
 * The output is merged into app/src/main/baseline-prof.txt (committed). See docs/performance/.
 *
 * The interaction steps use UiAutomator text/description lookups that match the app's bottom-nav
 * + battle entry. If a label changes, update the waitForObject selectors here.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.whitefang.stepsofbabylon") {
        pressHome()
        startActivityAndWait()
        // Let Home settle (currency dashboard + steps hero compose).
        device.waitForIdle()
        // Navigate to Workshop via the bottom nav, then into a battle. Selectors are best-effort;
        // the profile still captures startup + Home even if a later nav step is adjusted.
        device.waitForIdle()
    }
}
```

> The generator's nav steps are intentionally minimal + resilient — UiAutomator selectors depend on on-device labels, which the developer refines when running on a real device (Task 9). The startup + Home portion is captured regardless.

- [ ] **Step 5: Stage the module**

```bash
git add baselineprofile settings.gradle.kts
```

- [ ] **Step 6: Verify the workspace configures + the new module compiles**

Run: `./run-gradle.sh :baselineprofile:assemble > /tmp/t4.log 2>&1 && tail -n 5 /tmp/t4.log`
Expected: BUILD SUCCESSFUL. (This is the first full-configuration build since Task 3 — it proves the `:app` `baselineProfile { }` wiring + the new module + the catalog aliases all resolve together. If it fails on a missing `:macrobenchmark`, confirm Step 1 included ONLY `:baselineprofile`.)

- [ ] **Step 7: Commit**

```bash
git commit -m "build(#26): add :baselineprofile generator module (Home→Workshop→Battle journey)"
```

---

## Task 5: Create the :macrobenchmark module

**Files:**
- Create: `macrobenchmark/build.gradle.kts`
- Create: `macrobenchmark/src/main/AndroidManifest.xml`
- Create: `macrobenchmark/src/main/java/com/whitefang/stepsofbabylon/macrobenchmark/StartupBenchmark.kt`
- Create: `macrobenchmark/src/main/java/com/whitefang/stepsofbabylon/macrobenchmark/JourneyBenchmark.kt`
- Modify: `settings.gradle.kts` (add `include(":macrobenchmark")`)

- [ ] **Step 1: Add the module include**

In `settings.gradle.kts`, after `include(":baselineprofile")`, add:
```kotlin
include(":macrobenchmark")
```

- [ ] **Step 2: Create the module build script**

Create `macrobenchmark/build.gradle.kts`:
> **AGP-9 NOTE (learned in Task 4):** do NOT add `alias(libs.plugins.kotlin.android)` — AGP 9 has
> built-in Kotlin and applying `org.jetbrains.kotlin.android` to a `com.android.test` module is an
> ERROR. The `kotlin-android` catalog alias was removed in Task 4. The two new plugins are already
> declared `apply false` in the root `build.gradle.kts` (Task 4), so the module just applies them by
> alias with no version.
```kotlin
plugins {
    // No kotlin.android — AGP 9 provides built-in Kotlin (matches the :baselineprofile module).
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.whitefang.stepsofbabylon.macrobenchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"

    // Macrobenchmark runs against the profileable, non-debuggable `benchmarkRelease` variant the
    // baselineprofile plugin auto-creates on :app.
    @Suppress("UnstableApiUsage")
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
```

- [ ] **Step 3: Create the manifest**

Create `macrobenchmark/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 4: Create the startup benchmark**

Create `macrobenchmark/src/main/java/com/whitefang/stepsofbabylon/macrobenchmark/StartupBenchmark.kt`:
```kotlin
package com.whitefang.stepsofbabylon.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #26 Gate G — cold-start timing for the most-used launch. Run on a connected device:
 *   ./run-gradle.sh :macrobenchmark:connectedBenchmarkReleaseAndroidTest
 * Compares CompilationMode.None() (no cloud profile) vs Partial(BaselineProfiles()) to quantify
 * the committed profile's win. Numbers are recorded in docs/performance/startup-baseline.md.
 * NOT part of the CI gate — emulator timings are unreliable.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() =
        startup(CompilationMode.Partial(androidx.benchmark.macro.BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = "com.whitefang.stepsofbabylon",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = mode,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
```

- [ ] **Step 5: Create the journey (frame timing) benchmark**

Create `macrobenchmark/src/main/java/com/whitefang/stepsofbabylon/macrobenchmark/JourneyBenchmark.kt`:
```kotlin
package com.whitefang.stepsofbabylon.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #26 Gate G — frame-timing (jank visibility) over the Home → Workshop → Battle journey.
 * Gives frame-duration distribution, NOT per-Composable recomposition counts (recomposition
 * profiling is a named follow-up — see the spec). Run on a connected device; not CI-gated.
 */
@RunWith(AndroidJUnit4::class)
class JourneyBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun journey() = rule.measureRepeated(
        packageName = "com.whitefang.stepsofbabylon",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial(),
    ) {
        startActivityAndWait()
        device.waitForIdle()
        // Nav into Workshop + Battle is refined on-device (selectors depend on labels).
    }
}
```

- [ ] **Step 6a: Verify both modules compile + discover the REAL variant task names**

First confirm the modules produce variants (the bare `assemble` may not be the right gate for a
`com.android.test` module). List the actual assemble/release task names for each:

Run: `./run-gradle.sh :macrobenchmark:tasks --all 2>&1 | grep -iE "assemble|Release" | head; echo "---"; ./run-gradle.sh :baselineprofile:tasks --all 2>&1 | grep -iE "assemble|Release" | head`
Expected: a list including variant-specific tasks (e.g. `assembleBenchmarkRelease`, `assembleNonMinifiedRelease` on `:app`-derived variants, or the module's own `assemble`). **Record the exact task names** — they feed Step 6b, the CI step (Task 5 Step 8), and Task 9. If the bare `:macrobenchmark:assemble` doesn't exist, gate on the discovered variant task instead.

Then compile-check both modules using the discovered task (substitute the real name if `assemble` is absent):

Run: `./run-gradle.sh :macrobenchmark:assemble :baselineprofile:assemble > /tmp/t5.log 2>&1; tail -n 6 /tmp/t5.log`
Expected: BUILD SUCCESSFUL (the new Kotlin/test sources type-check).

- [ ] **Step 6b: Validate the #124 guard task-name exclusion is exhaustive**

The Task 1 exclusion (`!contains("Benchmark") && !contains("NonMinified")`) must cover EVERY non-shippable `*Release` task the plugin now generates graph-wide. Enumerate them:

Run: `./run-gradle.sh tasks --all 2>&1 | grep -iE "^(assemble|bundle|package).*Release" | grep -ivE "Benchmark|NonMinified"`
Expected: ONLY genuine shippable tasks (`assembleRelease`, `bundleRelease`, `packageRelease`, and any real product-flavor release task) appear — NO benchmark/profile-derived task. **If any non-shippable `*Release` task lacks both tokens, STOP and widen the Task 1 exclusion** (add the new token) before proceeding, then re-run.

- [ ] **Step 6c: Verify the #124 guard-for-the-guard (cases b + c)**

`--dry-run` still configures the task graph and fires `gradle.taskGraph.whenReady` (the guard runs at graph-ready, before task execution), so it's a safe, fast way to exercise the guard. With no `play.licenseKey` in `local.properties`:

Run: `./run-gradle.sh :app:assembleBenchmarkRelease --dry-run 2>&1 | tail -n 5`
Expected: BUILD SUCCESSFUL — a benchmark-only graph must NOT throw on a blank key (case b). (Case c — a real release task still throws — is already proven by real execution in Task 1 Step 2's `bundleRelease` run; the line below re-confirms it in a combined graph.)

Run: `./run-gradle.sh :app:bundleRelease :app:assembleBenchmarkRelease --dry-run 2>&1 | grep -i "play.licenseKey\|requires a non-blank\|BUILD"`
Expected: the guard THROWS (`Release build requires a non-blank 'play.licenseKey'`) because `bundleRelease` is in the graph — proving the per-task (not whole-graph) semantics keep a combined graph fail-closed (case c).
> If `--dry-run` unexpectedly does NOT trigger `whenReady` on this Gradle version (sanity-check by confirming case c throws), fall back to real `assembleBenchmarkRelease` execution for case b — but that requires a device/longer build; the dry-run path is preferred.

- [ ] **Step 7: Commit**

```bash
git add macrobenchmark settings.gradle.kts
git commit -m "build(#26): add :macrobenchmark module (startup + journey frame-timing); verify #124 guard cases b/c"
```

- [ ] **Step 8: Add the new modules to the CI PR-gate compile step (spec §3.6)**

The existing PR-gate `assembleDebug` + the `connectedDebugAndroidTest` lane do NOT build the new
`com.android.test` modules, so their Kotlin/test sources would go un-type-checked in CI. Add an explicit
compile step to the PR-gate workflow. Read `.github/workflows/ci.yml` first to find the existing Gradle
invocation step; then either extend its task list or add a step that runs (using the variant task names
discovered in Step 6a — substitute if `:…:assemble` isn't the right gate):
```yaml
      - name: Compile benchmark modules (type-check only; benchmarks run locally, never CI-gated)
        run: ./gradlew :baselineprofile:assemble :macrobenchmark:assemble
```
Place it after the existing build/test step. Do NOT add any benchmark *execution* or perf-timing
assertion to CI (emulator timings flake — spec §3.6). Commit:
```bash
git add .github/workflows/ci.yml
git commit -m "ci(#26): type-check :baselineprofile + :macrobenchmark in the PR gate (no perf assertions)"
```

---

## Task 6: A28 — replace CollisionSystem per-frame allocations with engine-owned scratch buffers (FRAGILE ZONE #118/#125)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CollisionSystem.kt`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngine.kt` (the scratch buffers + the `checkCollisions` call at `:478-486`, inside `synchronized(entitiesLock)` opened at `:441`)
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CollisionSystemScratchTest.kt` (new)

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CollisionSystemScratchTest.kt`. This exercises the NEW `checkCollisions` signature (typed lists, no `entities: List<Entity>`), asserting parity with the old `filterIsInstance` behaviour incl. dead-entity exclusion and that the same `enemies` list serves the whole call:
```kotlin
package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.battle.engine.Simulation
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyProjectileEntity
import com.whitefang.stepsofbabylon.presentation.battle.entities.ProjectileEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A28 (audit finding) — the collision sweep now receives pre-filtered, engine-owned scratch lists
 * instead of allocating three `filterIsInstance<…>().filter{}` lists per call. These tests pin that
 * the new signature produces identical hit outcomes and an empty-list short-circuits. Robolectric is
 * needed because the concrete entities construct android.graphics.Paint.
 *
 * REAL CONSTRUCTORS (verified against the entity files — do NOT use x/y/shooter shorthand):
 *   ProjectileEntity(startX, startY, targetX, targetY, speed, damage = 0.0, …) : Entity(x=startX, y=startY, width=8f)
 *   EnemyProjectileEntity(startX, startY, targetX, targetY, speed = 300f, damage, shooter = null) : Entity(x=startX, y=startY, width=6f)
 *   EnemyEntity(enemyType, currentHp, maxHp, speed, damage, targetX, targetY, onDeath, …) : Entity()  // x/y set via .apply
 * Place an EnemyEntity's geometry with `.apply { x = …; y = …; initDistance() }` (mirrors
 * GameEngineTest.makeStationaryEnemy at GameEngineTest.kt:846-856).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CollisionSystemScratchTest {

    private fun enemyAt(x: Float, y: Float, hp: Double = 1.0): EnemyEntity = EnemyEntity(
        enemyType = EnemyType.BASIC,
        currentHp = hp, maxHp = hp,
        speed = 0f, damage = 0.0,
        targetX = x, targetY = y,
        onDeath = { },
    ).apply { this.x = x; this.y = y; initDistance() }

    @Test
    fun `checkCollisions fires projectile-enemy hit for an overlapping pair from the typed lists`() {
        // Projectile spawned AT the enemy position so geometry overlaps immediately.
        val proj = ProjectileEntity(startX = 10f, startY = 10f, targetX = 10f, targetY = 10f, speed = 100f, damage = 1.0)
        val enemy = enemyAt(10f, 10f)
        val hits = mutableListOf<Pair<ProjectileEntity, EnemyEntity>>()

        CollisionSystem.checkCollisions(
            Simulation(),
            projectiles = listOf(proj),
            enemies = listOf(enemy),
            enemyProjectiles = emptyList(),
            zigX = 0f, zigY = 0f, zigWidth = 20f,
            onProjectileHitEnemy = { p, e -> hits.add(p to e) },
            onEnemyProjectileHitZiggurat = { },
        )

        assertEquals(1, hits.size)
    }

    @Test
    fun `checkCollisions does not fire when the enemy list is empty`() {
        // The engine's partition pass filters dead enemies OUT before this call; passing an empty
        // list models that (the sweep must short-circuit with no hit).
        val proj = ProjectileEntity(startX = 10f, startY = 10f, targetX = 10f, targetY = 10f, speed = 100f, damage = 1.0)
        var fired = false
        CollisionSystem.checkCollisions(
            Simulation(),
            projectiles = listOf(proj),
            enemies = emptyList(),
            enemyProjectiles = emptyList(),
            zigX = 0f, zigY = 0f, zigWidth = 20f,
            onProjectileHitEnemy = { _, _ -> fired = true },
            onEnemyProjectileHitZiggurat = { },
        )
        assertEquals(false, fired)
    }

    @Test
    fun `checkCollisions fires enemy-projectile ziggurat hit from the typed list`() {
        // Enemy projectile spawned at the ziggurat centre (0,0) so it is within zigWidth/2 + projW/2.
        val eproj = EnemyProjectileEntity(startX = 0f, startY = 0f, targetX = 0f, targetY = 0f, damage = 1.0)
        var hit = 0
        CollisionSystem.checkCollisions(
            Simulation(),
            projectiles = emptyList(),
            enemies = emptyList(),
            enemyProjectiles = listOf(eproj),
            zigX = 0f, zigY = 0f, zigWidth = 20f,
            onProjectileHitEnemy = { _, _ -> },
            onEnemyProjectileHitZiggurat = { hit++ },
        )
        assertEquals(1, hit)
    }
}
```
Add `import com.whitefang.stepsofbabylon.domain.model.EnemyType` to the test imports.
> These constructors are verified against the real entity files. Do NOT revert to the `x =/y =/shooter =` shorthand — those params do not exist (`EnemyEntity` extends `Entity()` no-arg; geometry is set via `.apply`). The corpse-parity assertion is NOT in this file — it lives in `GameEngineTest` (Step 6) where the real reward path can be asserted.

- [ ] **Step 2: Run the test to verify it fails to compile (new signature absent)**

Run: `./run-gradle.sh testDebugUnitTest --tests "*CollisionSystemScratchTest" > /tmp/t6.log 2>&1; tail -n 15 /tmp/t6.log`
Expected: FAIL — compilation error, `checkCollisions` does not accept `projectiles`/`enemies`/`enemyProjectiles` params (old signature takes `entities: List<Entity>`).

- [ ] **Step 3: Change the CollisionSystem signature to typed lists**

Replace the body of `CollisionSystem.checkCollisions` (`CollisionSystem.kt:17-28`) so it receives pre-filtered typed lists and no longer allocates:
```kotlin
    fun checkCollisions(
        simulation: Simulation,
        projectiles: List<ProjectileEntity>,
        enemies: List<EnemyEntity>,
        enemyProjectiles: List<EnemyProjectileEntity>,
        zigX: Float, zigY: Float, zigWidth: Float,
        onProjectileHitEnemy: (ProjectileEntity, EnemyEntity) -> Unit,
        onEnemyProjectileHitZiggurat: (EnemyProjectileEntity) -> Unit,
    ) {
        simulation.detectProjectileEnemyHits(projectiles, enemies, onProjectileHitEnemy)
        simulation.detectZigguratHits(enemyProjectiles, zigX, zigY, zigWidth, onEnemyProjectileHitZiggurat)
    }
```
Remove the now-unused `import ...Entity` for the generic `Entity` if it becomes unused (leave the three concrete entity imports). Update the KDoc's "owns ... `filterIsInstance` type partitioning" line to note the partition now happens in `GameEngine` under `entitiesLock`.

- [ ] **Step 4: Add engine-owned scratch buffers + the under-lock partition pass in GameEngine**

In `GameEngine.kt`, add three private scratch buffers near `entitiesLock` (`:62`):
```kotlin
    // A28 (audit): reusable per-tick scratch buffers for the collision partition, owned by THIS
    // engine instance (NOT the CollisionSystem object — it's a singleton; instance buffers avoid
    // cross-engine shared mutable state). Filled in one pass under [entitiesLock] each tick; never
    // retained across update() calls (so this is NOT the #125 cross-frame caching hazard).
    private val projScratch = ArrayList<ProjectileEntity>()
    private val enemyScratch = ArrayList<EnemyEntity>()
    private val enemyProjScratch = ArrayList<EnemyProjectileEntity>()
```
Then replace the `CollisionSystem.checkCollisions(...)` call (`:478-486`, already inside `synchronized(entitiesLock)`) with a single partition pass + the new call:
```kotlin
            // A28: one partition pass over `entities` (under [entitiesLock]) fills the three scratch
            // buffers, replacing three per-frame filterIsInstance().filter{} allocations. The SAME
            // `enemyScratch` instance serves the whole sweep — matching the old single `enemies`
            // snapshot — so mid-sweep deaths behave identically (a corpse stays in the list; the
            // #146 `takeDamage` guard prevents a double onDeath).
            projScratch.clear(); enemyScratch.clear(); enemyProjScratch.clear()
            for (e in entities) {
                when {
                    e is ProjectileEntity && e.isAlive -> projScratch.add(e)
                    e is EnemyEntity && e.isAlive -> enemyScratch.add(e)
                    e is EnemyProjectileEntity && e.isAlive -> enemyProjScratch.add(e)
                }
            }
            CollisionSystem.checkCollisions(
                simulation,
                projScratch, enemyScratch, enemyProjScratch,
                zig.x, zig.y, zig.width,
                onProjectileHitEnemy = ::onProjectileHitEnemy,
                onEnemyProjectileHitZiggurat = { proj ->
                    applyDamageToZiggurat(proj.damage, proj.shooter)
                    proj.isAlive = false
                },
            )
```
> The `when` uses `e is X && e.isAlive` arms; an entity that is neither type (e.g. `OrbEntity`, `ZigguratEntity`) or is dead falls through to no buffer — matching the old `filterIsInstance<…>().filter{ it.isAlive }` exclusion exactly.

- [ ] **Step 5: Run the new test + the full battle-engine suite to verify green**

Run: `./run-gradle.sh testDebugUnitTest --tests "*CollisionSystemScratchTest" --tests "*GameEngineTest" --tests "*SimulationTest" > /tmp/t6b.log 2>&1; tail -n 15 /tmp/t6b.log`
Expected: PASS — the new parity tests pass AND every existing `GameEngineTest` (incl. `R125`, `R146`) + `SimulationTest` collision test stays green (proves byte-identical behaviour + no double-credit regression).

- [ ] **Step 6: Add the mid-sweep-death corpse parity test — in `GameEngineTest` (real reward path)**

The corpse-parity guarantee that matters is "a second projectile on a corpse does NOT re-credit the
kill reward." That can only be proven through the real `onProjectileHitEnemy` → `handleEnemyDeath`
reward path — a synthetic counter in `CollisionSystemScratchTest` would NOT prove it. The repo already
has exactly this test at `GameEngineTest.kt:673-699` (`R146 a second projectile on a corpse does not
re-credit the kill reward`), and it must stay green after the A28 refactor (Task 6 Step 5 already runs
`*GameEngineTest`).

Add ONE new `GameEngineTest` entry that drives **both** projectiles through the engine's real
collision path in a single `update()` (not via the manual `invokeOnProjectileHitEnemy` shim), proving
the new scratch-buffer single-fill keeps the corpse in the swept list yet the `#146` `takeDamage` guard
stops a re-credit. Use the existing helpers (`freshEngine()`, `makeStationaryEnemy`, `engineDeathHandler`,
`flushPendingAdd`, `addEntity`) — read `GameEngineTest.kt:673-699` + `:846-856` for the exact pattern:
```kotlin
    @Test
    fun `A28 two projectiles on one enemy in a single tick credit the kill exactly once`() {
        val eng = freshEngine()
        val zig = eng.ziggurat!!
        val enemy = EnemyEntity(
            enemyType = EnemyType.BASIC,
            currentHp = 0.01, maxHp = 0.01, speed = 0f, damage = 0.0,
            targetX = zig.originX, targetY = zig.originY,
            onDeath = engineDeathHandler(eng), // route death through the real reward block
        ).apply { x = zig.originX; y = zig.originY + 300f; initDistance() }
        eng.addEntity(enemy)
        flushPendingAdd(eng)

        val cashBefore = eng.cash
        // Two projectiles co-located on the enemy, both alive in the same entity list → both land
        // in the single enemyScratch fill and both reach the corpse in one collision sweep.
        eng.addEntity(ProjectileEntity(zig.originX, zig.originY, enemy.x, enemy.y, 100f, damage = 9999.0)
            .apply { x = enemy.x; y = enemy.y })
        eng.addEntity(ProjectileEntity(zig.originX, zig.originY, enemy.x, enemy.y, 100f, damage = 9999.0)
            .apply { x = enemy.x; y = enemy.y })
        flushPendingAdd(eng)

        eng.update(0.016f) // one tick: partition → sweep → both projectiles hit; corpse re-hit is a no-op
        assertTrue(eng.cash > cashBefore, "the kill must credit cash")
        // Re-run a tick with no new projectiles: cash must not move (corpse already gone / guarded).
        val cashAfterKill = eng.cash
        eng.update(0.016f)
        assertEquals(cashAfterKill, eng.cash, "no re-credit after the kill")
    }
```
> If driving both hits in a single real `update()` proves fiddly (projectile speed/positioning), fall
> back to the proven `invokeOnProjectileHitEnemy(eng, proj1, enemy)` / `proj2` shim exactly as
> `R146 (:673-699)` does, asserting `eng.cash` is unchanged after the corpse hit — that already
> exercises the same `takeDamage` guard the A28 buffer-reuse depends on. Either way the assertion is on
> **`eng.cash` via the real reward path**, NOT a synthetic counter. Adjust positions so the first hit
> is lethal (`currentHp = 0.01`).

- [ ] **Step 7: Run the corpse test + full build**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > /tmp/t6c.log 2>&1; tail -n 8 /tmp/t6c.log`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CollisionSystem.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngine.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/CollisionSystemScratchTest.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngineTest.kt
git commit -m "perf(#26): A28 — engine-owned scratch buffers for collision sweep (no per-frame list allocs; under entitiesLock)"
```

---

## Task 7: A31 — cache the CHRONO_FIELD overlay Paint

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngine.kt` (the render block `:512-515`; add a cached field + a `@VisibleForTesting` seam)
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/ChronoOverlayPaintTest.kt` (new)

- [ ] **Step 1: Add a VisibleForTesting seam for chronoActive**

`chronoActive` is `private var` at `GameEngine.kt:257`. Add a test-only setter just after it:
```kotlin
    @androidx.annotation.VisibleForTesting
    internal fun setChronoActiveForTest(active: Boolean) { chronoActive = active }
```

- [ ] **Step 2: Write the failing test (same Paint instance across two renders)**

Create `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/ChronoOverlayPaintTest.kt`:
```kotlin
package com.whitefang.stepsofbabylon.presentation.battle.engine

import android.graphics.Canvas
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A31 (audit finding) — the CHRONO_FIELD full-screen overlay must reuse a cached Paint instead of
 * allocating a new android.graphics.Paint every render() frame. This test proves (a) the overlay
 * draws with the expected colour + FILL style, and (b) the SAME Paint instance is used across two
 * render() calls (identity check — the allocation is gone).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ChronoOverlayPaintTest {

    @Test
    fun `chrono overlay reuses one Paint instance across frames`() {
        val engine = GameEngine()
        engine.init(800f, 600f)
        engine.setChronoActiveForTest(true)

        val canvas = mock<Canvas>()
        engine.render(canvas)
        engine.render(canvas)

        val paints = argumentCaptor<Paint>()
        // drawRect(left, top, right, bottom, paint) — capture the full-screen overlay paint.
        verify(canvas, org.mockito.kotlin.atLeast(2)).drawRect(
            org.mockito.kotlin.eq(0f), org.mockito.kotlin.eq(0f),
            org.mockito.kotlin.eq(800f), org.mockito.kotlin.eq(600f),
            paints.capture(),
        )
        val captured = paints.allValues
        assertEquals(0x222196F3, captured[0].color)
        assertEquals(Paint.Style.FILL, captured[0].style)
        // Identity: the overlay paint is the same object on both frames.
        assertEquals(true, captured[0] === captured[1])
    }
}
```
> **Captor precision:** `render()` has exactly one OTHER full-screen `drawRect` — `UWVisualEffect.kt:106`
> (color `0x332196F3`) — but it fires ONLY in the reduced-motion fallback (the effect early-returns when
> `!reducedMotion`). This test constructs `GameEngine()` with the default `reducedMotion = false` and
> enqueues no UW effect, so it never fires; the `eq(0f),eq(0f),eq(800f),eq(600f)` matcher captures only
> the chrono overlay. The colour assert (`0x222196F3`, distinct from the UW fallback's `0x332196F3`) is a
> second guard. `GameEngine()` no-arg construction + `init(800f, 600f)` is valid (see `GameEngineTest.kt:47`
> `val eng = GameEngine()` then `eng.init(...)`; `GameSurfaceView` also constructs it no-arg internally).

- [ ] **Step 3: Run the test to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "*ChronoOverlayPaintTest" > /tmp/t7.log 2>&1; tail -n 15 /tmp/t7.log`
Expected: FAIL — the two captured paints are different instances (a new `Paint()` is allocated each frame at `:513`), so the `===` identity assert fails.

- [ ] **Step 4: Hoist the Paint to a cached field**

In `GameEngine.kt`, add a cached field next to `hpPercentPaint` (`:537`):
```kotlin
    // A31 (audit): cached CHRONO_FIELD overlay paint — was allocated per frame in render(). Colour
    // 0x222196F3 preserved exactly (semi-transparent blue). The literal intentionally keeps the
    // existing value; the alpha nuance the audit flagged is left unchanged (no observable colour
    // change in this PR).
    private val chronoOverlayPaint = android.graphics.Paint().apply {
        color = 0x222196F3; style = android.graphics.Paint.Style.FILL
    }
```
Then change the render block (`:512-515`) to reuse it:
```kotlin
        // Chrono field overlay
        if (chronoActive) {
            canvas.drawRect(0f, 0f, screenWidth, screenHeight, chronoOverlayPaint)
        }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "*ChronoOverlayPaintTest" > /tmp/t7b.log 2>&1; tail -n 8 /tmp/t7b.log`
Expected: PASS — same Paint instance, colour `0x222196F3`, FILL style.

- [ ] **Step 6: Full build**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > /tmp/t7c.log 2>&1; tail -n 8 /tmp/t7c.log`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngine.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/ChronoOverlayPaintTest.kt
git commit -m "perf(#26): A31 — cache CHRONO_FIELD overlay Paint (no per-frame Paint alloc)"
```

---

## Task 8: A29 — distinctUntilChanged on the shared profile Flow (CONDITIONAL — only if the mandatory test passes)

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/data/repository/PlayerRepositoryImpl.kt:18-26`
- Test: extend `app/src/test/java/com/whitefang/stepsofbabylon/data/repository/PlayerRepositoryImplTest.kt`

> **Decision gate (spec §4.3):** A29 has NO pre-existing safety net (existing repo tests only read `.first()`; ViewModel tests use `FakePlayerRepository`). The bar for inclusion is that the mandatory distinct-emission test below passes. If writing it surfaces a real dependence on duplicate emissions, STOP — revert this task and record A29 as deferred (it is not required for Gate G).

- [ ] **Step 1: Write the failing distinct-emission test**

Add the imports below to `PlayerRepositoryImplTest.kt` (it already imports `MutableStateFlow`, `runTest`,
`mock`, `whenever`, `assertEquals`):
```kotlin
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
```
Add a small entity helper to the test class (`PlayerProfileEntity` is an all-defaulted data class, so
this compiles as-is — verified against `PlayerProfileEntity.kt`):
```kotlin
    private fun makeEntity(currentTier: Int = 1, gems: Long = 0L): PlayerProfileEntity =
        PlayerProfileEntity(currentTier = currentTier, gems = gems)
```
Then the test. **Two non-obvious requirements, both confirmed by plan review against the repo's own
patterns:** (1) `runTest(UnconfinedTestDispatcher())` so the launched collector subscribes EAGERLY at
`launch` before the first `flow.value =` write (mirrors `SimulationTest.kt:337` / `GameEngineTest.kt:1209`,
which document exactly this); (2) `launch { }` unqualified — it binds the implicit `TestScope` receiver
inside `runTest`; a fully-qualified `kotlinx.coroutines.launch { }` would NOT compile (no scope receiver).
Because `MutableStateFlow` conflates, advance the scheduler between writes so the duplicate `3` is
actually delivered to the collector before `4` lands:
```kotlin
    @Test
    fun `observeTier suppresses duplicate-value emissions but still emits real changes`() =
        runTest(UnconfinedTestDispatcher()) {
            val flow = MutableStateFlow(makeEntity(currentTier = 3))
            val dao = mock<PlayerProfileDao>()
            whenever(dao.get()).thenReturn(flow)
            val repo = PlayerRepositoryImpl(dao)

            val seen = mutableListOf<Int>()
            val job = launch { repo.observeTier().take(2).toList(seen) } // eager subscribe (Unconfined)

            // Identical-value re-emission (an unrelated counter write that didn't change the tier):
            flow.value = makeEntity(currentTier = 3, gems = 999L) // tier unchanged
            // A genuine tier change:
            flow.value = makeEntity(currentTier = 4)

            job.join()
            // Pre-fix: observeTier re-emits the duplicate 3 → take(2) completes as [3, 3].
            // Post-fix (distinctUntilChanged): the duplicate 3 is suppressed → [3, 4].
            assertEquals(listOf(3, 4), seen)
        }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "*PlayerRepositoryImplTest" > /tmp/t8.log 2>&1; tail -n 15 /tmp/t8.log`
Expected: FAIL — `observeTier` re-emits the duplicate `3`, so `seen` is `[3, 3]` not `[3, 4]` (the `take(2)` completes on the duplicate before the real change arrives).

- [ ] **Step 3: Add distinctUntilChanged after each projection**

In `PlayerRepositoryImpl.kt`, add `import kotlinx.coroutines.flow.distinctUntilChanged` and update the three observers (`:18-26`) — the distinct must sit AFTER the full `map` so it dedupes the projected value the consumer sees:
```kotlin
    override fun observeProfile(): Flow<PlayerProfile> =
        dao.get().filterNotNull().map { it.toDomain() }.distinctUntilChanged()

    override fun observeWallet(): Flow<PlayerWallet> =
        dao.get().filterNotNull().map { it.toDomain().toWallet() }.distinctUntilChanged()

    override fun observeTier(): Flow<Int> =
        dao.get().filterNotNull().map { it.currentTier }.distinctUntilChanged()
```

- [ ] **Step 4: Run the new test + the full repo suite to verify green**

Run: `./run-gradle.sh testDebugUnitTest --tests "*PlayerRepositoryImplTest" > /tmp/t8b.log 2>&1; tail -n 10 /tmp/t8b.log`
Expected: PASS — `seen == [3, 4]`, and the existing `observeProfile`/`observeWallet`/`observeTier` `.first()` tests still pass (a real value change still emits).

- [ ] **Step 5: Run the full suite to confirm no ViewModel regression**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > /tmp/t8c.log 2>&1; tail -n 8 /tmp/t8c.log`
Expected: BUILD SUCCESSFUL, all tests pass. **If any ViewModel test now fails because it depended on a duplicate emission, STOP and revert per the decision gate.**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/data/repository/PlayerRepositoryImpl.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/data/repository/PlayerRepositoryImplTest.kt
git commit -m "perf(#26): A29 — distinctUntilChanged on shared profile Flow (suppress no-op re-emissions)"
```

---

## Task 9: Write the battery-audit doc + generate the baseline profile + startup baseline (local device)

**Files:**
- Create: `docs/performance/battery-audit.md`
- Create: `docs/performance/startup-baseline.md`
- Create (generated, committed): `app/src/main/baseline-prof.txt`

- [ ] **Step 1: Write the battery-audit doc**

Create `docs/performance/battery-audit.md` with the code-grounded wake-source inventory, the no-op findings, candidate tunings (NOT applied), and the device-measurement procedure. Use these verified facts:
- `StepCounterService` foreground service (continuous while moving).
- `StepNotificationManager` notification throttle — **30 s** (`StepNotificationManager.kt:25`, `THROTTLE_MS = 30_000L`).
- `WidgetUpdateHelper` — **60 s** (`WidgetUpdateHelper.kt:13`, `THROTTLE_MS = 60_000L`).
- `StepSyncWorker` periodic cadence — **15 min** (`StepSyncScheduler.kt:14`, `PeriodicWorkRequestBuilder<StepSyncWorker>(15, TimeUnit.MINUTES)`).
- Health Connect read frequency — driven by `StepSyncWorker`.
- `TYPE_STEP_COUNTER` — hardware-batched; no app-side polling cadence to tune.
- **Wake-lock audit:** ZERO explicit `PowerManager.WakeLock` acquisitions in app source (verified — foreground service is the wakefulness mechanism). Record as a no-op.
- **Candidate tunings (hypotheses, NOT applied this round):** notification 30 s→60 s; HC sync 15→30 min (≈50% fewer wakeups) — each tagged with its risk (cross-validation accuracy / step-freshness UX) + the device measurement (Battery Historian / `adb shell dumpsys batterystats`) that must confirm it before shipping. This doc is the spec for a future cadence-tuning PR.
- **Measurement procedure + OEM matrix** (Samsung / Xiaomi / OnePlus / Pixel) flagged as the physical-device, developer-judgment slice that closes Gate G's battery line and cannot be done in-repo.

- [ ] **Step 2: Generate the baseline profile on a connected device**

Run (requires a connected device/emulator with an AOSP or Google-APIs (non-Play) image — Play-image emulators reject the `nonMinifiedRelease` install):
```bash
./run-gradle.sh :baselineprofile:generateBaselineProfile
```
This writes `app/src/main/generated/baselineProfiles/baseline-prof.txt` (or `app/src/main/baseline-prof.txt` depending on the plugin output dir). Confirm the file exists and contains `HSPLcom/whitefang/stepsofbabylon/...` lines. If the connected-device task name differs, list options with `./run-gradle.sh :baselineprofile:tasks --all | grep -i generate`.

> If no suitable device is available in this environment, record in `startup-baseline.md` that generation is a pending local-device step and commit the harness only — the modules + harness still satisfy "Baseline Profiles are implemented." Do NOT fabricate a profile file.

- [ ] **Step 3: Capture the startup baseline**

Run on a connected device:
```bash
./run-gradle.sh :macrobenchmark:connectedBenchmarkReleaseAndroidTest
```
Record the `startupNoCompilation` vs `startupBaselineProfile` median `timeToInitialDisplayMs` into `docs/performance/startup-baseline.md`, with the **upper-bound honesty caveat** (the None-vs-BaselineProfiles delta over-states the real-world win — `CompilationMode.None()` models an install with no Play cloud profile; most Play installs eventually get one; the committed profile still helps day-one / sideload / cold-cache). If no device is available, record the measurement procedure + that the numbers are pending a local-device run.

- [ ] **Step 4: Commit**

```bash
git add docs/performance/ app/src/main/baseline-prof.txt 2>/dev/null; git add docs/performance/
git commit -m "docs(#26): battery-audit + startup-baseline docs; commit generated baseline profile"
```

---

## Task 10: Record the Gate-G deferrals in plan-FORWARD.md

**Files:**
- Modify: `docs/plans/plan-FORWARD.md:64-66` (Gate G section)

- [ ] **Step 1: Edit the Gate-G lines per the gate-maintenance convention**

In `docs/plans/plan-FORWARD.md`, the Gate G block currently is:
```markdown
### G. Performance & battery
- [ ] Acceptable frame rate on a low-end device at 2×/4× speed
- [ ] Foreground-service + Health Connect polling battery cost is sane — *satisfied-by #26 (V1X-23)*
```
Replace it with (per the convention at `:68-69` — `[deferred]` replaces the checkbox for deferred-not-blocker items):
```markdown
### G. Performance & battery
- [ ] Acceptable frame rate on a low-end device at 2×/4× speed — *in-repo contribution shipped by #26
  (FrameTimingMetric journey benchmark + A28/A31 per-frame GC-churn fixes); the low-end-device 2×/4×
  verdict itself is a device-only manual pass and is not closed in-repo.*
- [deferred] Foreground-service + Health Connect polling battery cost is sane — physical-device,
  developer-judgment slice (overnight idle-drain + OEM matrix Samsung/Xiaomi/OnePlus/Pixel); #26 shipped
  the in-repo measurement infra (Baseline Profile + Macrobenchmark) + the battery-audit doc
  (`docs/performance/battery-audit.md`) with candidate cadence tunings specced but not applied. Cannot
  close from the repo alone.
```

- [ ] **Step 2: Commit**

```bash
git add docs/plans/plan-FORWARD.md
git commit -m "docs(#26): record Gate-G battery line as deferred + frame-rate in-repo contribution"
```

---

## Task 11: Current-state doc sync + STATE/RUN_LOG (PR Task-List Convention)

Per CLAUDE.md's mandatory PR Task-List Convention, sync current-state docs BEFORE the STATE/RUN_LOG update.

**Files:**
- Modify: `CLAUDE.md` (headline test count line — Testing section)
- Modify: `CHANGELOG.md` (add a `[Unreleased]` entry for #26)
- Modify: `docs/steering/source-files.md` (add the two new modules + new test files + the perf docs)
- Modify: `docs/steering/structure.md` (note the multi-module addition — `:baselineprofile` / `:macrobenchmark`)
- Modify: `docs/agent/STATE.md` + `docs/agent/RUN_LOG.md`

- [ ] **Step 1: Update the headline test count in CLAUDE.md**

Count the net new JVM tests added (Task 6: 3–4; Task 7: 1; Task 8: 1 if A29 included) and update the "Headline count: NNNN JVM tests + 9 instrumented tests" line in the Testing section. Determine the exact number from the final `testDebugUnitTest` run, don't guess.

Run: `./run-gradle.sh testDebugUnitTest > /tmp/t11.log 2>&1; grep -i "tests completed\|BUILD" /tmp/t11.log | tail -3`
Then update CLAUDE.md's count to match.

- [ ] **Step 2: Add the CHANGELOG entry**

Add to `CHANGELOG.md`'s `[Unreleased]` section a bullet summarizing #26: measurement infra (Baseline Profile + Macrobenchmark modules + profileinstaller), the A28/A31 GC-churn fixes (+ A29 if included), the battery-audit + startup-baseline docs, the #124 guard reconciliation, and the test-count delta.

- [ ] **Step 3: Update source-files.md + structure.md**

Add entries for `:baselineprofile` / `:macrobenchmark` modules, the new test files (`CollisionSystemScratchTest`, `ChronoOverlayPaintTest`), and `docs/performance/`. Note the project is no longer single-module.

- [ ] **Step 4: Update STATE.md + append RUN_LOG.md**

In `docs/agent/STATE.md`: move the objective forward (Gate G in-repo slice shipped; remaining Gate-G battery line deferred). Add a "Recently shipped" entry. Add the A28/A31 fixes + the new modules to fragile zones / capabilities as appropriate. Append a RUN_LOG entry describing the session.

- [ ] **Step 5: Final full build + commit**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > /tmp/t11b.log 2>&1; tail -n 8 /tmp/t11b.log`
Expected: BUILD SUCCESSFUL.

```bash
git add CLAUDE.md CHANGELOG.md docs/steering/source-files.md docs/steering/structure.md docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs(#26): doc-sync + STATE/RUN_LOG — Gate G in-repo slice (perf infra + A28/A31 fixes)"
```

> Note: this plan does NOT bump versionCode or ship a release — #26's in-repo slice lands on `main` and rides the next routine `v*` release tag. The benchmark modules are dev-tooling; they don't affect the shipped AAB (profileinstaller is the only shipping addition, and the committed baseline profile is a pure win).

---

## Self-review notes (for the plan reviewer)

- **Spec coverage:** measurement infra (Tasks 2–5), A28 (Task 6), A31 (Task 7), A29 conditional (Task 8), #124 guard (Task 1 + verified Task 5), battery-audit + startup docs (Task 9), plan-FORWARD deferral (Task 10), doc-sync (Task 11). A30 is already-fixed (no task). A11/A18 excluded (no task, by design).
- **Fragile zones touched:** #124 (Task 1), #118 `entitiesLock` (Task 6 — partition stays under-lock), #125 (Task 6 — buffers per-tick, not cross-frame; corpse parity test). All flagged in task headers.
- **Device-dependent steps** (Task 9 Steps 2–3): explicitly allow a "record-as-pending" fallback rather than fabricating data — the harness still satisfies "Baseline Profiles implemented."
- **Known incidental risks for the executor:** exact entity constructor signatures (Task 6 test) and the connected-device task names (Task 9) must be confirmed against the real code/`tasks --all` output — both are called out inline.
