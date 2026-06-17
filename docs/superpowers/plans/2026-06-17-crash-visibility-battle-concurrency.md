# Crash Visibility + Battle Concurrency (#190 + #191) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make battle crashes visible and survivable for the closed soak — install a chaining global uncaught-exception handler + a local crash breadcrumb, guard the game-loop thread so an exception surfaces an honest "Battle error" state instead of silent process death, and close the two reachable battle CMEs (`EffectEngine` effect lists; `uwStates` on replay).

**Architecture:** Local-only diagnostics (no new dependency, no data egress). A `CrashBreadcrumbStore` (SharedPreferences, mirrors `OnboardingPreferences`) is written by both a chaining `Thread.setDefaultUncaughtExceptionHandler` (in `StepsOfBabylonApp`) and a `try/catch` in `GameLoopThread.run()`. A loop crash stops the loop, suppresses round chrome, surfaces a `battleError` overlay, and is engineered NOT to persist end-of-round state from the corrupt engine. #191 extends the established `#118` `entitiesLock` monitor pattern to `uwStates` and adds a private `effectsLock` inside `EffectEngine`.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, JUnit 5 (Jupiter, plain-JVM) + JUnit 4/Robolectric, mockito-kotlin, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-06-17-crash-visibility-battle-concurrency-design.md` (review-passed: 34 raised / 25 surviving / 9 refuted).

**Build/test commands (this repo):**
- JVM unit tests: `./run-gradle.sh testDebugUnitTest` (use `--tests "fully.qualified.ClassName"` to scope).
- Lint + assemble: `./run-gradle.sh lintDebug assembleDebug`.
- ⚠️ Gradle output is large — redirect to a temp log and inspect the tail/grep (see CLAUDE.md). Example:
  `./run-gradle.sh testDebugUnitTest --tests "...CrashBreadcrumbStoreTest" > /tmp/t.log 2>&1; tail -n 30 /tmp/t.log`

---

## File Structure

**New production files:**
- `app/src/main/java/com/whitefang/stepsofbabylon/data/diagnostics/CrashBreadcrumb.kt` — the immutable breadcrumb model.
- `app/src/main/java/com/whitefang/stepsofbabylon/data/diagnostics/CrashBreadcrumbStore.kt` — `@Singleton` SharedPreferences wrapper (`record`/`peek`/`clear`).
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleErrorOverlay.kt` — the non-dismissable "Battle error" overlay (mirrors `PauseOverlay`).

**New test files:**
- `app/src/test/java/com/whitefang/stepsofbabylon/data/diagnostics/CrashBreadcrumbStoreTest.kt` (Robolectric)
- `app/src/test/java/com/whitefang/stepsofbabylon/StepsOfBabylonCrashHandlerTest.kt` (plain JVM — chaining wrapper logic)
- `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/GameLoopThreadGuardTest.kt` (plain JVM)
- `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/effects/EffectEngineConcurrencyTest.kt` (plain JVM)

**Modified production files:**
- `StepsOfBabylonApp.kt` — inject store, install chaining handler.
- `data/DataDeletionManager.kt` — add `"crash_breadcrumb_prefs"` to `PREFS_NAMES`.
- `presentation/battle/GameLoopThread.kt` — store param, `onLoopError`, guarded loop.
- `presentation/battle/GameSurfaceView.kt` — construct store, pass to thread, forward + re-seed `onLoopError`.
- `presentation/battle/BattleViewModel.kt` — `onBattleLoopError` (set `battleError`, break poll, set `roundEnded`), poll via `uwSnapshot()`.
- `presentation/battle/BattleUiState.kt` — `battleError` field.
- `presentation/battle/BattleScreen.kt` — battle-error overlay + suppress round chrome on `battleError`.
- `presentation/battle/effects/EffectEngine.kt` — `effectsLock` + guarded add/update(deferred sweep)/render/clear.
- `presentation/battle/engine/GameEngine.kt` — `initUWs` under `entitiesLock`; new `uwSnapshot()`.
- `presentation/MainActivity.kt` — inject store, next-launch breadcrumb snackbar.
- `app/src/main/res/values/strings.xml` — overlay + snackbar copy.

**Modified test files:**
- `data/DataDeletionManagerTest.kt` — assert breadcrumb file cleared.
- `presentation/battle/engine/GameEngineConcurrencyTest.kt` — add the `uwStates` replay race test.
- `presentation/battle/BattleViewModelTest.kt` — `onBattleLoopError` flips `battleError` + no-persist-on-crash.

**Task order rationale:** Build the leaf store first (Task 1), wire its consumers (Tasks 2–3), then the loop guard that uses it (Task 4), then the surface/VM/UI integration (Tasks 5–6), then the two independent #191 concurrency fixes (Tasks 7–8). Tasks 7–8 have no dependency on 1–6 and could be done first, but are placed last so the whole crash-visibility spine lands as a unit.

---

## Task 1: `CrashBreadcrumb` model + `CrashBreadcrumbStore`

**Files:**
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/data/diagnostics/CrashBreadcrumb.kt`
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/data/diagnostics/CrashBreadcrumbStore.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/data/diagnostics/CrashBreadcrumbStoreTest.kt`

- [ ] **Step 1: Write the failing test**

Create `CrashBreadcrumbStoreTest.kt` (Robolectric — mirrors `OnboardingPreferencesTest` 1:1):

```kotlin
package com.whitefang.stepsofbabylon.data.diagnostics

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CrashBreadcrumbStoreTest {

    private fun newStore(): CrashBreadcrumbStore =
        CrashBreadcrumbStore(RuntimeEnvironment.getApplication() as Context)

    @Test
    fun `peek is null when nothing recorded`() {
        assertNull(newStore().peek())
    }

    @Test
    fun `record then peek round-trips the fields`() {
        val store = newStore()
        val ex = IllegalStateException("boom")
        store.record("GameLoop", ex, timestampMillis = 1234L)

        val b = store.peek()!!
        assertEquals(1234L, b.timestampMillis)
        assertEquals("GameLoop", b.threadName)
        assertEquals("java.lang.IllegalStateException", b.exceptionClass)
        assertEquals("boom", b.message)
        assertTrue("stack preview should mention the exception", b.stackPreview.contains("IllegalStateException"))
    }

    @Test
    fun `clear removes the breadcrumb`() {
        val store = newStore()
        store.record("main", RuntimeException("x"), 1L)
        store.clear()
        assertNull(store.peek())
    }

    @Test
    fun `newest record overwrites the previous one`() {
        val store = newStore()
        store.record("t1", RuntimeException("first"), 1L)
        store.record("t2", RuntimeException("second"), 2L)

        val b = store.peek()!!
        assertEquals(2L, b.timestampMillis)
        assertEquals("t2", b.threadName)
        assertEquals("second", b.message)
    }

    @Test
    fun `stack preview is truncated to MAX_STACK_CHARS`() {
        val store = newStore()
        // A throwable whose stackTraceToString() far exceeds the cap.
        val deep = RuntimeException("x".repeat(10_000))
        store.record("main", deep, 1L)
        assertTrue(store.peek()!!.stackPreview.length <= CrashBreadcrumbStore.MAX_STACK_CHARS)
    }

    @Test
    fun `record never throws on a null message`() {
        val store = newStore()
        // NullPointerException with no message — message is null.
        store.record("main", NullPointerException(), 1L)
        val b = store.peek()!!
        assertEquals("java.lang.NullPointerException", b.exceptionClass)
        assertNull(b.message)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.diagnostics.CrashBreadcrumbStoreTest" > /tmp/t1.log 2>&1; tail -n 30 /tmp/t1.log`
Expected: FAIL — `CrashBreadcrumbStore` / `CrashBreadcrumb` unresolved (compile error).

- [ ] **Step 3: Write the model**

Create `CrashBreadcrumb.kt`:

```kotlin
package com.whitefang.stepsofbabylon.data.diagnostics

/**
 * One persisted crash record (#190 REL-1). Local-only diagnostic — never uploaded.
 * `timestampMillis` is supplied by the caller (the store reads no clock, so it is
 * trivially testable); `stackPreview` is truncated to [CrashBreadcrumbStore.MAX_STACK_CHARS].
 */
data class CrashBreadcrumb(
    val timestampMillis: Long,
    val threadName: String,
    val exceptionClass: String,
    val message: String?,
    val stackPreview: String,
)
```

- [ ] **Step 4: Write the store**

Create `CrashBreadcrumbStore.kt`:

```kotlin
package com.whitefang.stepsofbabylon.data.diagnostics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local crash breadcrumb (#190 REL-1). Mirrors `OnboardingPreferences`:
 * @Singleton, constructor-injected, no Hilt module, SharedPreferences-backed.
 * Deliberately NOT Room — must not sync (cloud save #36) and a reinstall discards it.
 *
 * Single slot, newest-wins. Writes synchronously (`commit()`) because the process may be
 * dying when the global handler calls it. Every method is best-effort and never throws —
 * a diagnostic that crashes the crash handler is worse than useless.
 *
 * The backing file `crash_breadcrumb_prefs` is wiped by `DataDeletionManager` (#192-adjacent).
 */
@Singleton
class CrashBreadcrumbStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("crash_breadcrumb_prefs", Context.MODE_PRIVATE)

    fun record(threadName: String, throwable: Throwable, timestampMillis: Long) {
        runCatching {
            prefs.edit()
                .putLong(KEY_TS, timestampMillis)
                .putString(KEY_THREAD, threadName)
                .putString(KEY_CLASS, throwable.javaClass.name)
                .putString(KEY_MESSAGE, throwable.message)
                .putString(KEY_STACK, throwable.stackTraceToString().take(MAX_STACK_CHARS))
                .commit() // synchronous — the process may be exiting
        }
    }

    fun peek(): CrashBreadcrumb? {
        if (!prefs.contains(KEY_TS)) return null
        return CrashBreadcrumb(
            timestampMillis = prefs.getLong(KEY_TS, 0L),
            threadName = prefs.getString(KEY_THREAD, "") ?: "",
            exceptionClass = prefs.getString(KEY_CLASS, "") ?: "",
            message = prefs.getString(KEY_MESSAGE, null),
            stackPreview = prefs.getString(KEY_STACK, "") ?: "",
        )
    }

    fun clear() {
        runCatching { prefs.edit().clear().commit() }
    }

    companion object {
        const val MAX_STACK_CHARS = 4096
        private const val KEY_TS = "crash_ts"
        private const val KEY_THREAD = "crash_thread"
        private const val KEY_CLASS = "crash_class"
        private const val KEY_MESSAGE = "crash_message"
        private const val KEY_STACK = "crash_stack"
    }
}
```

Note on the null-message test: `KEY_MESSAGE` is written with `putString(..., null)`, which stores nothing for that key; `peek` reads it back as `null` via the default. `peek`'s presence check keys on `KEY_TS` (always written), so a null message does not make `peek()` return null.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.diagnostics.CrashBreadcrumbStoreTest" > /tmp/t1.log 2>&1; tail -n 30 /tmp/t1.log`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/data/diagnostics/ \
        app/src/test/java/com/whitefang/stepsofbabylon/data/diagnostics/
git commit -m "feat(#190): CrashBreadcrumbStore + model (local crash breadcrumb)"
```

---

## Task 2: Wipe `crash_breadcrumb_prefs` on "Delete All Data"

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/data/DataDeletionManager.kt:63-75`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/data/DataDeletionManagerTest.kt`

- [ ] **Step 1: Write the failing test**

Add a **new dedicated test method** to `DataDeletionManagerTest.kt`. It seeds `crash_breadcrumb_prefs`
directly (by literal file name) so it produces a genuine red→green: it fails before the `PREFS_NAMES`
edit (the file isn't wiped) and passes after.

> ⚠️ **Do NOT** instead "add the entry to the existing parameterised `deleteAllData clears all
> SharedPreferences` test." That test derives BOTH its seed and its assertion from `PREFS_NAMES`
> itself, so there is no separate expected-set to extend — the only edit would be the production
> change in Step 3, which leaves no failing-test state (breaks red-green). Use the dedicated method
> below.

> ⚠️ **Assertion arg order:** `DataDeletionManagerTest` is JUnit4/Robolectric (`import org.junit.Assert.*`),
> so `assertTrue` is **message-FIRST** (`assertTrue(String, boolean)`) — NOT the Jupiter
> condition-first order. The snippet below uses the correct JUnit4 order; the existing test at
> `DataDeletionManagerTest.kt:58` is the precedent.

The file's `@Before setUp()` already provides `context`, `manager`, and `activity` — reuse them
(do not re-create them in the test):

```kotlin
    @Test
    fun `deleteAllData clears the crash breadcrumb prefs`() {
        // Seed a breadcrumb directly via the same file name CrashBreadcrumbStore uses.
        context.getSharedPreferences("crash_breadcrumb_prefs", Context.MODE_PRIVATE)
            .edit().putString("crash_class", "x").commit()

        manager.deleteAllData(activity)

        assertTrue(
            "crash_breadcrumb_prefs must be cleared by deleteAllData",
            context.getSharedPreferences("crash_breadcrumb_prefs", Context.MODE_PRIVATE).all.isEmpty(),
        )
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.DataDeletionManagerTest" > /tmp/t2.log 2>&1; tail -n 30 /tmp/t2.log`
Expected: FAIL — assertion fails: the breadcrumb file still has the seeded `crash_class` entry (`crash_breadcrumb_prefs` is not yet in `PREFS_NAMES`, so `deleteAllData` doesn't wipe it).

- [ ] **Step 3: Add the prefs name**

In `DataDeletionManager.kt`, add the entry to the `PREFS_NAMES` list (after `"billing_anti_fraud",`):

```kotlin
        internal val PREFS_NAMES = listOf(
            "biome_prefs",
            "milestone_notification_prefs",
            "notification_prefs",
            "sound_prefs",
            "music_prefs",
            "anti_cheat_prefs",
            "step_ingestion",
            "db_key_prefs",
            "widget_data",
            "smart_reminders",
            "billing_anti_fraud",
            "crash_breadcrumb_prefs",
        )
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.data.DataDeletionManagerTest" > /tmp/t2.log 2>&1; tail -n 30 /tmp/t2.log`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/data/DataDeletionManager.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/data/DataDeletionManagerTest.kt
git commit -m "feat(#190): wipe crash_breadcrumb_prefs on Delete All Data"
```

---

## Task 3: Chaining global uncaught-exception handler

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/StepsOfBabylonApp.kt:15-19`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/StepsOfBabylonCrashHandlerTest.kt`

The handler logic is a pure function of (store, previous-handler). To keep it unit-testable without standing up an `Application`, extract it into a top-level `internal fun` and have `onCreate` call it. The test exercises the function directly with a fake store + a recording "previous" handler.

- [ ] **Step 1: Write the failing test**

Create `StepsOfBabylonCrashHandlerTest.kt` (plain-JVM Jupiter):

```kotlin
package com.whitefang.stepsofbabylon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StepsOfBabylonCrashHandlerTest {

    @Test
    fun `handler records a breadcrumb and still delegates to the previous handler`() {
        var recordedThread: String? = null
        var recordedThrowable: Throwable? = null
        val recordFn: (String, Throwable, Long) -> Unit = { t, ex, _ ->
            recordedThread = t; recordedThrowable = ex
        }

        var delegatedThread: Thread? = null
        var delegatedThrowable: Throwable? = null
        val previous = Thread.UncaughtExceptionHandler { t, ex ->
            delegatedThread = t; delegatedThrowable = ex
        }

        val handler = buildCrashHandler(previous, recordFn)

        val boom = RuntimeException("kaboom")
        val thread = Thread.currentThread()
        handler.uncaughtException(thread, boom)

        // Breadcrumb recorded with the thread name + throwable.
        assertEquals(thread.name, recordedThread)
        assertSame(boom, recordedThrowable)
        // Previous handler still invoked (Play vitals preserved).
        assertSame(thread, delegatedThread)
        assertSame(boom, delegatedThrowable)
    }

    @Test
    fun `handler tolerates a null previous handler and a throwing record fn`() {
        val throwingRecord: (String, Throwable, Long) -> Unit = { _, _, _ -> throw IllegalStateException("record blew up") }
        val handler = buildCrashHandler(previous = null, record = throwingRecord)
        // Must not throw even when record() throws and there is no previous handler.
        handler.uncaughtException(Thread.currentThread(), RuntimeException("x"))
        assertTrue(true)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.StepsOfBabylonCrashHandlerTest" > /tmp/t3.log 2>&1; tail -n 30 /tmp/t3.log`
Expected: FAIL — `buildCrashHandler` unresolved.

- [ ] **Step 3: Add `buildCrashHandler` + install it in `onCreate`**

Edit `StepsOfBabylonApp.kt`. Add imports (`android.util.Log`, the store), the injected field, the `onCreate` install call, and the top-level builder:

```kotlin
package com.whitefang.stepsofbabylon

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.whitefang.stepsofbabylon.data.diagnostics.CrashBreadcrumbStore
import com.whitefang.stepsofbabylon.service.StepSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StepsOfBabylonApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var crashBreadcrumbStore: CrashBreadcrumbStore

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
        StepSyncScheduler.schedule(this)
        // #190 REL-1: install a chaining global handler so any uncaught exception is recorded
        // locally before the platform handler terminates the process (preserves Play vitals).
        Thread.setDefaultUncaughtExceptionHandler(
            buildCrashHandler(
                previous = Thread.getDefaultUncaughtExceptionHandler(),
                record = { thread, ex, ts -> crashBreadcrumbStore.record(thread, ex, ts) },
            )
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

/**
 * Builds the chaining uncaught-exception handler (#190 REL-1). Extracted as a top-level
 * `internal fun` so the chaining + best-effort-record behaviour is JVM-unit-testable without
 * an Android `Application`. Records the breadcrumb FIRST (the process may die on delegation),
 * never throws, then delegates to [previous] so the platform crash / Play vitals is preserved.
 */
internal fun buildCrashHandler(
    previous: Thread.UncaughtExceptionHandler?,
    record: (threadName: String, throwable: Throwable, timestampMillis: Long) -> Unit,
): Thread.UncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, ex ->
    runCatching { record(thread.name, ex, System.currentTimeMillis()) }
    runCatching { Log.e("StepsOfBabylonApp", "Uncaught exception on ${thread.name}", ex) }
    previous?.uncaughtException(thread, ex)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.StepsOfBabylonCrashHandlerTest" > /tmp/t3.log 2>&1; tail -n 30 /tmp/t3.log`
Expected: PASS (2 tests). Note: `Log.e` returns 0 under `isReturnDefaultValues=true`, so it's safe in plain JVM (and wrapped in `runCatching` regardless).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/StepsOfBabylonApp.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/StepsOfBabylonCrashHandlerTest.kt
git commit -m "feat(#190): chaining global uncaught-exception handler"
```

---

## Task 4: Guard the game-loop thread

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/GameLoopThread.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/GameLoopThreadGuardTest.kt`

`GameLoopThread` takes `engine: GameEngine` (a final Kotlin class — mock it with `mock<GameEngine>()`; mockito-kotlin 5.x's default inline maker mocks final classes, and the repo already mocks final Kotlin classes like `BiomePreferences`/`MilestoneNotificationManager` in `BattleViewModelTest`). We add a `CrashBreadcrumbStore` constructor param and an `onLoopError` callback. The tests mock `GameEngine` to throw on `update()` (test 1) / `render()` (test 2), mock `SurfaceHolder`, run the real thread, join, then assert.

- [ ] **Step 1: Write the failing test**

Create `GameLoopThreadGuardTest.kt` (plain-JVM Jupiter; join-then-assert, no sleep race):

```kotlin
package com.whitefang.stepsofbabylon.presentation.battle

import android.view.SurfaceHolder
import com.whitefang.stepsofbabylon.data.diagnostics.CrashBreadcrumbStore
import com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicInteger

class GameLoopThreadGuardTest {

    @Test
    fun `an exception in update stops the loop, records a breadcrumb, fires onLoopError once`() {
        val engine = mock<GameEngine>()
        whenever(engine.update(any())).thenThrow(RuntimeException("engine boom"))
        val holder = mock<SurfaceHolder>() // lockCanvas() returns null → canvas block skipped

        val recordCount = AtomicInteger(0)
        val store = mock<CrashBreadcrumbStore>()
        whenever(store.record(any(), any(), any())).thenAnswer { recordCount.incrementAndGet(); Unit }

        val errorCount = AtomicInteger(0)
        val thread = GameLoopThread(holder, engine, store).apply {
            onLoopError = { errorCount.incrementAndGet() }
            isPaused = false
            isRunning = true
        }

        thread.start()
        thread.join(2_000) // join-then-assert: deterministic, no sleep race

        assertFalse(thread.isAlive, "loop thread must have stopped")
        assertFalse(thread.isRunning, "isRunning must be false after a loop crash")
        assertEquals(1, errorCount.get(), "onLoopError must fire exactly once")
        assertEquals(1, recordCount.get(), "breadcrumb must be recorded exactly once")
    }

    @Test
    fun `a render crash unlocks the canvas before propagating to the outer catch`() {
        // Spec §B1 load-bearing assertion: the inner lockCanvas/unlockCanvasAndPost try/finally
        // must stay strictly nested inside the outer try/catch, so a render() crash unlocks the
        // canvas (no frozen surface / ANR) before the throwable reaches the outer catch.
        val engine = mock<GameEngine>()
        // update() no-ops; render() throws. (update is called inside the same outer try.)
        whenever(engine.render(any())).thenThrow(RuntimeException("render boom"))
        val canvas = mock<android.graphics.Canvas>()
        val holder = mock<SurfaceHolder>()
        whenever(holder.lockCanvas()).thenReturn(canvas) // non-null → render() block is entered

        val store = mock<CrashBreadcrumbStore>()
        val errorCount = AtomicInteger(0)
        val thread = GameLoopThread(holder, engine, store).apply {
            onLoopError = { errorCount.incrementAndGet() }
            isPaused = false
            isRunning = true
        }

        thread.start()
        thread.join(2_000)

        assertFalse(thread.isAlive, "loop thread must have stopped after a render crash")
        // The inner finally must have unlocked the canvas before the outer catch fired.
        verify(holder).unlockCanvasAndPost(canvas)
        assertEquals(1, errorCount.get(), "onLoopError must fire exactly once on a render crash")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.battle.GameLoopThreadGuardTest" > /tmp/t4.log 2>&1; tail -n 30 /tmp/t4.log`
Expected: FAIL — `GameLoopThread` has no 3-arg constructor / no `onLoopError`.

- [ ] **Step 3: Add the store param, `onLoopError`, and the guard**

Edit `GameLoopThread.kt`. Change the constructor and wrap the loop body:

```kotlin
package com.whitefang.stepsofbabylon.presentation.battle

import android.util.Log
import android.view.SurfaceHolder
import com.whitefang.stepsofbabylon.data.diagnostics.CrashBreadcrumbStore
import com.whitefang.stepsofbabylon.domain.battle.engine.SimulationMath
import com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngine

class GameLoopThread(
    private val surfaceHolder: SurfaceHolder,
    private val engine: GameEngine,
    private val crashBreadcrumbStore: CrashBreadcrumbStore,
) : Thread("GameLoop") {

    @Volatile
    var isRunning: Boolean = false

    @Volatile
    var speedMultiplier: Float = 1f

    @Volatile
    var isPaused: Boolean = false

    /**
     * #190 REL-2: invoked (on the loop thread) when the guarded loop catches a throwable from
     * engine.update()/render(). The surface view forwards this to the ViewModel, which surfaces
     * a battle-error UI state. Null until wired.
     */
    @Volatile
    var onLoopError: ((Throwable) -> Unit)? = null

    var fps: Int = 0
        private set

    companion object {
        private const val TICK_NS = 16_666_667L // ~60 UPS (1e9 / 60)
        private const val TAG = "GameLoopThread"
    }

    override fun run() {
        var previousTime = System.nanoTime()
        var accumulator = 0L
        var frameCount = 0
        var fpsTimer = System.nanoTime()

        while (isRunning) {
            val currentTime = System.nanoTime()
            val elapsed = currentTime - previousTime
            previousTime = currentTime

            // #190 REL-2: guard the per-tick update + render. An uncaught exception here used to
            // kill the dedicated loop thread → silent process death. Now: record a breadcrumb,
            // stop the loop, and surface a battle-error state via onLoopError.
            try {
                if (!isPaused) {
                    accumulator += (elapsed * speedMultiplier).toLong()
                    accumulator = SimulationMath.clampAccumulator(accumulator, TICK_NS)
                    while (accumulator >= TICK_NS) {
                        engine.update(TICK_NS / 1_000_000_000f)
                        accumulator -= TICK_NS
                    }
                }

                var canvas = null as android.graphics.Canvas?
                try {
                    canvas = surfaceHolder.lockCanvas()
                    if (canvas != null) {
                        synchronized(surfaceHolder) {
                            engine.render(canvas)
                        }
                    }
                } finally {
                    canvas?.let {
                        try { surfaceHolder.unlockCanvasAndPost(it) } catch (_: Exception) {}
                    }
                }
            } catch (t: Throwable) {
                runCatching { crashBreadcrumbStore.record(name, t, System.currentTimeMillis()) }
                runCatching { Log.e(TAG, "Game loop crashed; stopping loop", t) }
                isRunning = false
                onLoopError?.invoke(t)
                break
            }

            // FPS counter
            frameCount++
            if (currentTime - fpsTimer >= 1_000_000_000L) {
                fps = frameCount
                frameCount = 0
                fpsTimer = currentTime
            }

            // Yield to avoid burning CPU if we're ahead
            val frameTime = System.nanoTime() - currentTime
            val sleepMs = (TICK_NS - frameTime) / 1_000_000
            if (sleepMs > 0) {
                try { sleep(sleepMs) } catch (_: InterruptedException) {}
            }
        }
    }
}
```

The inner `lockCanvas`/`unlockCanvasAndPost` try/finally stays strictly nested inside the new outer try/catch — so on a `render()` crash the canvas is unlocked by the finally before the throwable reaches the outer catch.

- [ ] **Step 4: (Do NOT expect green standalone) — proceed to Task 5, then run Tasks 4+5 together**

⚠️ **The 3-arg constructor change makes the whole debug source set fail to compile until Task 5
updates `GameSurfaceView`'s call site.** Gradle compiles main+test before running any `--tests` filter,
so a standalone Task-4 run here will fail at COMPILE (`GameSurfaceView.kt:99` still calls the 2-arg
`GameLoopThread(holder, engine)`). That is expected — **do not look for a green here.** Do Task 5 next;
the genuinely-passing run for both guard tests is **Task 5 Step 2** (Tasks 4+5 together).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/GameLoopThread.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/GameLoopThreadGuardTest.kt
git commit -m "feat(#190): guard the game-loop thread (record + stop + onLoopError)"
```

---

## Task 5: Wire the store + `onLoopError` through `GameSurfaceView`

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/GameSurfaceView.kt`

No new test file — `GameSurfaceView` is exercised by `GameSurfaceViewTest` (existing) and the integration is covered by the Task 4 guard test + Task 6 VM test. This task is the wiring that makes the app source set compile again.

- [ ] **Step 1: Construct the store, pass it to the thread, add the `onLoopError` forward + re-seed**

Edit `GameSurfaceView.kt`:

Add the import:
```kotlin
import com.whitefang.stepsofbabylon.data.diagnostics.CrashBreadcrumbStore
```

Add a store field near the other fields (after `private val soundManager: SoundManager`):
```kotlin
    // #190 REL-1/REL-2: built directly from Context (like AndroidStrings) — same SharedPreferences
    // file as the Hilt singleton, keyed by name, so both write the same breadcrumb. The loop thread
    // writes the breadcrumb directly (no dependency on onLoopError being set yet).
    private val crashBreadcrumbStore = CrashBreadcrumbStore(context)

    /**
     * #190 REL-2: forwarded to the current [GameLoopThread] AND re-seeded onto each new thread in
     * [surfaceCreated] (threads are recreated every surface lifecycle). Unlike pendingSpeed/Paused
     * — which are re-set on every toggle and so self-heal — this is set ONCE by the VM, so the
     * re-seed is load-bearing: without it the battle-error callback is lost after a background→resume.
     */
    @Volatile
    var onLoopError: ((Throwable) -> Unit)? = null
        set(value) {
            field = value
            gameThread?.onLoopError = value
        }
```

In `surfaceCreated`, pass the store to the constructor and seed `onLoopError` alongside `speedMultiplier`/`isPaused`:
```kotlin
        val thread = GameLoopThread(holder, engine, crashBreadcrumbStore).apply {
            speedMultiplier = pendingSpeed
            isPaused = pendingPaused
            onLoopError = this@GameSurfaceView.onLoopError
            isRunning = true
        }
        thread.start(); gameThread = thread
```

- [ ] **Step 2: Run Tasks 4 + 5 tests together (now the app source set compiles)**

Run:
```bash
./run-gradle.sh testDebugUnitTest \
  --tests "com.whitefang.stepsofbabylon.presentation.battle.GameLoopThreadGuardTest" \
  --tests "com.whitefang.stepsofbabylon.presentation.battle.GameSurfaceViewTest" > /tmp/t5.log 2>&1; tail -n 30 /tmp/t5.log
```
Expected: PASS — guard test green, existing `GameSurfaceViewTest` still green.

- [ ] **Step 3: Verify the app still assembles**

Run: `./run-gradle.sh assembleDebug > /tmp/t5b.log 2>&1; tail -n 15 /tmp/t5b.log`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/GameSurfaceView.kt
git commit -m "feat(#190): wire CrashBreadcrumbStore + onLoopError through GameSurfaceView"
```

---

## Task 6: `battleError` state, VM handler (stop + no-persist), overlay, chrome suppression, next-launch notice

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleUiState.kt:55`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleViewModel.kt`
- Create: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleErrorOverlay.kt`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleScreen.kt`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/BattleViewModelTest.kt`

- [ ] **Step 1: Write the failing VM tests**

Add to `BattleViewModelTest.kt` (uses the existing `createVm` + `installEngineForEndRound` helpers; `onBattleLoopError` will be a `@VisibleForTesting internal fun`):

```kotlin
    @Test
    fun `onBattleLoopError sets battleError`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onBattleLoopError(RuntimeException("loop boom"))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.battleError, "battleError must be set after a loop crash")
    }

    @Test
    fun `onBattleLoopError makes onCleared skip end-of-round persistence`() = runTest(dispatcher) {
        val vm = createVm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // Install an engine that reports wave progress (so onCleared would normally persist).
        val engine = installEngineForEndRound(vm)
        engine.init(1080f, 1920f, com.whitefang.stepsofbabylon.domain.model.ResolvedStats(), 1)
        engine.update(1f / 60f) // elapsedTimeSeconds > 0 → hasWaveProgress() true
        assertTrue(engine.hasWaveProgress())

        vm.onBattleLoopError(RuntimeException("loop boom"))
        advanceUntilIdle()

        // Drive onCleared via the existing helper (the prod call site is the framework).
        invokeOnCleared(vm)
        advanceUntilIdle()

        // No end-of-round persistence ran on the crashed round.
        assertEquals(
            0L, playerRepo.profile.value.totalRoundsPlayed,
            "a loop-crashed round must NOT persist end-of-round stats",
        )
        assertNull(vm.uiState.value.roundEndState, "no RoundEndState on a loop-crashed round")
    }
```

> Note: this reuses the file's existing `invokeOnCleared(vm)` helper (`BattleViewModelTest.kt:714`) —
> `onCleared` is `protected` in `ViewModel`, and that helper already does the `getDeclaredMethod` +
> `setAccessible` reflection. Do NOT inline a second copy of the reflection.

- [ ] **Step 2: Run to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.battle.BattleViewModelTest" > /tmp/t6.log 2>&1; tail -n 30 /tmp/t6.log`
Expected: FAIL — `battleError` and `onBattleLoopError` unresolved.

- [ ] **Step 3: Add `battleError` to `BattleUiState`**

In `BattleUiState.kt`, add the field to the `data class BattleUiState(...)` (after `userMessage`):

```kotlin
    val userMessage: String? = null,
    /**
     * #190 REL-2: set when the game-loop thread caught an exception and stopped. Drives a
     * non-dismissable "Battle error" overlay and suppresses all interactive round chrome.
     */
    val battleError: Boolean = false,
)
```

- [ ] **Step 4: Add `onBattleLoopError` + break the poll + suppress persistence in `BattleViewModel`**

In `BattleViewModel.kt`:

(a) In `startPollingEngine`, set the callback right after `this.engine = engine; this.surfaceView = surfaceView` line, before the `viewModelScope.launch`:
```kotlin
        surfaceView.onLoopError = { t -> onBattleLoopError(t) }
```

(b) Add a `battleError`-gated break at the TOP of the polling `while (true)` loop (just after `delay(200)` and the `eng` null-check is fine; place the break first so a crashed round stops pushing HUD state):
```kotlin
            while (true) {
                delay(200)
                if (_uiState.value.battleError) break   // #190: stop polling a crashed engine
                val eng = this@BattleViewModel.engine ?: break
                // ... rest unchanged ...
```

(c) **Make `roundEnded` `@Volatile`.** `onBattleLoopError` runs on the **loop thread** (it's invoked
via `GameSurfaceView.onLoopError`, which the loop thread calls in its catch), and it writes
`roundEnded`, which `onCleared` reads **directly** on the main thread (`BattleViewModel.kt:486`, not via
the StateFlow). This is the FIRST cross-thread write of `roundEnded` (today all writes are main-thread).
Without `@Volatile` there is no JMM happens-before edge guaranteeing the main thread sees the write.
This also matches the spec's "@Volatile/state flag" intent and the repo's convention (every cross-thread
engine field is `@Volatile`; #118). Change `BattleViewModel.kt:154`:
```kotlin
    @Volatile
    private var roundEnded = false
```

(d) Add the handler method (near `endRound` / `quitRound`), `@VisibleForTesting internal`:
```kotlin
    /**
     * #190 REL-2: the game-loop thread caught an exception and stopped. Fired on the loop thread
     * via [GameSurfaceView.onLoopError]; `_uiState.update` is thread-safe. We:
     *  - surface the battle-error overlay (`battleError = true`),
     *  - mark the round ended (`roundEnded` is `@Volatile`, see (c)) so the polling loop breaks AND
     *    `onCleared` skips end-of-round persistence — the engine state is corrupt, so its totals
     *    must NOT be committed.
     * We deliberately do NOT set `eng.roundOver = true` (that routes through `endRound` →
     * `runEndRoundPersistence`, committing the suspect numbers — the opposite of what we want).
     */
    @VisibleForTesting
    internal fun onBattleLoopError(t: Throwable) {
        roundEnded = true
        _uiState.update { it.copy(battleError = true) }
    }
```

> Setting `roundEnded = true` short-circuits `onCleared`'s `!roundEnded && hasWaveProgress()` guard (`BattleViewModel.kt:486`) so no persistence runs, and the polling loop's `battleError` break (b) stops the HUD updates. The `eventCollector` is cancelled when the polling coroutine exits (it already calls `eventCollector.cancel()` after the `while`). `playAgain` resets `roundEnded = false` (`BattleViewModel.kt:457`), so a post-crash replay path is unaffected — though in practice the only CTA on the battle-error overlay is "Return to menu" (no replay).

- [ ] **Step 5: Add the strings**

In `strings.xml`, add before `</resources>` (after `postround_leave_battle`):
```xml
    <string name="battle_error_title">Battle error</string>
    <string name="battle_error_body">Something went wrong and the battle had to stop. Your progress for this round wasn\'t saved.</string>
    <string name="battle_error_return">Return to menu</string>
    <string name="crash_notice_last_session">The game closed unexpectedly last time. Sorry about that!</string>
```

- [ ] **Step 6: Create the `BattleErrorOverlay`**

Create `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleErrorOverlay.kt` (mirrors `PauseOverlay`):

```kotlin
package com.whitefang.stepsofbabylon.presentation.battle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whitefang.stepsofbabylon.R

/**
 * #190 REL-2: non-dismissable overlay shown when the game loop crashed and stopped. The only
 * action is "Return to menu" (the loop is dead + engine state is suspect, so no retry). The
 * scrim itself doesn't catch touches — round chrome is suppressed by the caller via the
 * `battleError` gate, so there is nothing interactive behind it.
 */
@Composable
fun BattleErrorOverlay(onReturnToMenu: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1F14)),
        ) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.battle_error_title), style = MaterialTheme.typography.headlineLarge, color = Color(0xFFD4A843), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.battle_error_body), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onReturnToMenu, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.battle_error_return)) }
            }
        }
    }
}
```

- [ ] **Step 7: Suppress round chrome + render the overlay in `BattleScreen`**

In `BattleScreen.kt`:

(a) Add a chrome gate right after `val roundActive = state.roundEndState == null` (`:68`):
```kotlin
    val roundActive = state.roundEndState == null
    // #190 REL-2: when the loop has crashed, suppress ALL interactive round chrome — the scrim
    // overlay doesn't block touches, so leaving the rail/quit/UW bar composed would let a tester
    // drive the stopped engine through it.
    val showGameChrome = roundActive && !state.battleError
```

(b) Replace every `if (roundActive)` / `if (roundActive && ...)` chrome gate in the `Box` with `showGameChrome`. The exact sites (from the current file):
- `if (roundActive) { IconButton(... quitRound ...) }` (`:150`) → `if (showGameChrome)`
- `if (roundActive && state.uwSlots.isNotEmpty())` (`:160`) → `if (showGameChrome && state.uwSlots.isNotEmpty())`
- `if (roundActive) { BattleControlRail(...) }` (`:176`) → `if (showGameChrome)`
- `if (state.showUpgradeMenu && roundActive)` (`:197`) → `if (state.showUpgradeMenu && showGameChrome)`
- `if (state.isPaused && roundActive)` (`:210`) → `if (state.isPaused && showGameChrome)`

Leave the top-left HUD `Column` (`:129`, always shown) and the `PostRoundOverlay` block as-is — `battleError` and `roundEndState` are mutually exclusive in practice (a loop crash sets `battleError` while `roundEndState` is null), but the overlay (c) is drawn last so it sits on top regardless.

(c) Add the overlay as the last child BEFORE the `SnackbarHost` (`:225`):
```kotlin
        if (state.battleError) {
            BattleErrorOverlay(onReturnToMenu = onExitBattle)
        }

        // Snackbar last — stacks on top of every overlay ...
        SnackbarHost(
```

Add the import:
```kotlin
import com.whitefang.stepsofbabylon.presentation.battle.ui.BattleErrorOverlay
```

- [ ] **Step 8: Next-launch breadcrumb notice in `MainActivity`**

In `MainActivity.kt`:

Add the injected store with the other `@Inject` fields (near `onboardingPreferences`):
```kotlin
    @Inject lateinit var crashBreadcrumbStore: com.whitefang.stepsofbabylon.data.diagnostics.CrashBreadcrumbStore
```

Inside `setContent`, alongside the other top-level `LaunchedEffect`s that use `snackbarHostState` (after the `Scaffold` is set up — place it next to the existing `showStepPermissionSettingsHint` effect so it shares `snackbarHostState`), add:
```kotlin
                    // #190 REL-1: surface a one-time notice if the previous session crashed.
                    // Informational only — there is no in-app report channel to wire an action to.
                    LaunchedEffect(Unit) {
                        val crash = crashBreadcrumbStore.peek()
                        if (crash != null) {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.crash_notice_last_session)
                            )
                            crashBreadcrumbStore.clear()
                        }
                    }
```
`context` IS already in scope (used by the sibling permission-hint effect). **`R` is NOT imported in
`MainActivity` today** (the existing snackbars use raw string literals, not `R.string`) — so you MUST
add `import com.whitefang.stepsofbabylon.R` to the file's imports.

- [ ] **Step 9: Run the VM tests**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.battle.BattleViewModelTest" > /tmp/t6.log 2>&1; tail -n 30 /tmp/t6.log`
Expected: PASS (incl. the two new tests).

- [ ] **Step 10: Assemble (compiles Compose + resources)**

Run: `./run-gradle.sh lintDebug assembleDebug > /tmp/t6b.log 2>&1; tail -n 20 /tmp/t6b.log`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleUiState.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleViewModel.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/ui/BattleErrorOverlay.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleScreen.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/MainActivity.kt \
        app/src/main/res/values/strings.xml \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/BattleViewModelTest.kt
git commit -m "feat(#190): battle-error overlay + stop-and-no-persist on loop crash + next-launch notice"
```

---

## Task 7: #191 Trigger A — guard `EffectEngine` effect lists

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/effects/EffectEngine.kt`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/effects/EffectEngineConcurrencyTest.kt`

- [ ] **Step 1: Write the failing test**

Create `EffectEngineConcurrencyTest.kt` (plain-JVM Jupiter; mirrors `GameEngineConcurrencyTest`):

```kotlin
package com.whitefang.stepsofbabylon.presentation.battle.effects

import android.graphics.Canvas
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Issue #191 (CONC-1): EffectEngine's `effects` / `pendingEffects` lists are mutated cross-thread
 * — addEffect is called from coroutine threads (StepReward/BossKilled) while the loop thread
 * drains in update() and iterates in render(). Neither list was synchronized. This stress test
 * races addEffect against update()+render(); before the effectsLock fix it throws a CME within
 * milliseconds, after the fix it completes cleanly.
 */
class EffectEngineConcurrencyTest {

    /** A trivial Effect that never finishes (so the list keeps growing → maximises iteration overlap). */
    private class StubEffect : Effect {
        override val isFinished: Boolean = false
        override fun update(dt: Float) {}
        override fun render(canvas: Canvas) {}
    }

    @Test
    fun `addEffect racing update and render does not throw`() {
        val fx = EffectEngine(reducedMotion = false)
        val canvas = mock<Canvas>()
        val caught = AtomicReference<Throwable?>(null)
        val keepLooping = AtomicBoolean(true)

        val loopThread = Thread {
            try {
                while (keepLooping.get()) {
                    fx.update(1f / 60f)
                    fx.render(canvas)
                }
            } catch (t: Throwable) {
                caught.compareAndSet(null, t)
            }
        }
        loopThread.start()

        try {
            for (i in 0 until 200_000) {
                if (caught.get() != null) break
                fx.addEffect(StubEffect())
            }
        } catch (t: Throwable) {
            caught.compareAndSet(null, t)
        } finally {
            keepLooping.set(false)
            loopThread.join(5_000)
        }

        assertNull(
            caught.get(),
            "addEffect from another thread must not race update()/render() iteration. Got: ${caught.get()}",
        )
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngineConcurrencyTest" > /tmp/t7.log 2>&1; tail -n 30 /tmp/t7.log`
Expected: FAIL — `ConcurrentModificationException` captured (reproduces the live race).

- [ ] **Step 3: Add `effectsLock` + guard add/update/render/clear**

Edit `EffectEngine.kt`. Replace the body (keep `pool`/`screenShake`/`particlePaint` exactly as-is):

```kotlin
class EffectEngine(val reducedMotion: Boolean = false) {
    val pool = ParticlePool(200)
    private val effects = mutableListOf<Effect>()
    private val pendingEffects = mutableListOf<Effect>()
    val screenShake = ScreenShake()
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // #191 CONC-1: effects/pendingEffects are touched from coroutine threads (addEffect) and the
    // loop thread (update/render). Guard every structural touch with a private monitor. Mirrors the
    // GameEngine #118 entitiesLock pattern. pool/screenShake stay loop-confined (NOT guarded).
    private val effectsLock = Any()

    fun addEffect(effect: Effect) { synchronized(effectsLock) { pendingEffects.add(effect) } }

    fun update(dt: Float) {
        // Drain pending + snapshot under the lock. The per-effect update() runs OUTSIDE the lock
        // (no monitor held across effect logic). removeAll is deferred to AFTER the per-effect
        // update — preserving today's update→removeAll ordering exactly (no 1-frame lifetime shift).
        val snapshot: List<Effect> = synchronized(effectsLock) {
            effects.addAll(pendingEffects); pendingEffects.clear()
            effects.toList()
        }
        pool.updateAll(dt)
        snapshot.forEach { it.update(dt) }
        synchronized(effectsLock) { effects.removeAll { it.isFinished } }
        if (!reducedMotion) screenShake.update(dt)
    }

    fun render(canvas: Canvas) {
        pool.renderAll(canvas, particlePaint)
        val snapshot = synchronized(effectsLock) { effects.toList() }
        snapshot.forEach { it.render(canvas) }
    }

    fun clear() {
        synchronized(effectsLock) { effects.clear(); pendingEffects.clear() }
        pool.clear(); screenShake.reset()
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.battle.effects.EffectEngineConcurrencyTest" > /tmp/t7.log 2>&1; tail -n 30 /tmp/t7.log`
Expected: PASS.

- [ ] **Step 5: Run the existing battle/effects + engine tests for no regression**

Run:
```bash
./run-gradle.sh testDebugUnitTest \
  --tests "com.whitefang.stepsofbabylon.presentation.battle.*" \
  --tests "com.whitefang.stepsofbabylon.presentation.battle.engine.*" > /tmp/t7b.log 2>&1; tail -n 30 /tmp/t7b.log
```
Expected: PASS (no behaviour regression — the update→removeAll order is preserved).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/effects/EffectEngine.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/effects/EffectEngineConcurrencyTest.kt
git commit -m "fix(#191): guard EffectEngine effect lists with a private monitor (CONC-1)"
```

---

## Task 8: #191 Trigger B — `initUWs` under `entitiesLock` + `uwSnapshot()`

**Files:**
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngine.kt:598-610`
- Modify: `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleViewModel.kt:233`
- Test: `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngineConcurrencyTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `GameEngineConcurrencyTest.kt` (same plain-JVM Jupiter class):

```kotlin
    @Test
    fun `concurrent replay initUWs during update loop does not throw`() {
        val eng = engineWithOrbs(orbCount = 2)
        // Equip ≥1 UW so updateUWs iterates a non-empty uwStates each tick.
        val equipped = listOf(
            com.whitefang.stepsofbabylon.domain.model.OwnedWeapon(
                type = com.whitefang.stepsofbabylon.domain.model.UltimateWeaponType.DEATH_WAVE,
                isUnlocked = true, isEquipped = true,
            ),
        )
        eng.initUWs(equipped)
        val caught = AtomicReference<Throwable?>(null)

        val keepLooping = AtomicBoolean(true)
        val loopThread = Thread {
            try {
                while (keepLooping.get()) { eng.update(1f / 60f) }
            } catch (t: Throwable) {
                caught.compareAndSet(null, t)
            }
        }
        loopThread.start()

        // Main thread: repeatedly re-init the UW list (the playAgain path), structurally mutating
        // uwStates while the loop thread iterates it in updateUWs.
        try {
            for (i in 0 until 200_000) {
                if (caught.get() != null) break
                eng.initUWs(equipped)
            }
        } catch (t: Throwable) {
            caught.compareAndSet(null, t)
        } finally {
            keepLooping.set(false)
            loopThread.join(5_000)
        }

        assertNull(
            caught.get(),
            "Replay initUWs on the main thread must not race the loop thread's updateUWs iteration. " +
                "Got: ${caught.get()}",
        )
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngineConcurrencyTest" > /tmp/t8.log 2>&1; tail -n 30 /tmp/t8.log`
Expected: FAIL — a CME / IndexOutOfBounds captured from the `updateUWs` iteration vs `initUWs` clear/add race.

- [ ] **Step 3: Guard `initUWs` + add `uwSnapshot()`**

In `GameEngine.kt`, wrap `initUWs`'s body in `synchronized(entitiesLock)`:
```kotlin
    fun initUWs(equipped: List<OwnedWeapon>) {
        // #191 CONC-2: initUWs runs on the main thread (playAgain) while the loop thread iterates
        // uwStates in updateUWs under entitiesLock. Take the same monitor for mutual exclusion so
        // the main-thread clear/add can never race the loop-thread iteration. (Reentrant: harmless
        // if any future loop-thread path calls this.)
        synchronized(entitiesLock) {
            uwStates.clear()
            equipped.forEach {
                uwStates.add(
                    UWState(
                        type = it.type,
                        damageLevel = it.damageLevel,
                        secondaryLevel = it.secondaryLevel,
                        cooldownLevel = it.cooldownLevel,
                    ),
                )
            }
        }
    }
```

Add a snapshot accessor near `aliveEnemyCount()` (which already uses the same idiom):
```kotlin
    /**
     * #191 CONC-2: a thread-safe copy of [uwStates] for the 200ms polling read in BattleViewModel.
     * Snapshots the LIST STRUCTURE under [entitiesLock] (the only thing the replay race corrupts);
     * the scalar fields it reads for display are torn-read-tolerant (one stale cooldown frame is
     * cosmetic, never a crash).
     */
    fun uwSnapshot(): List<UWState> = synchronized(entitiesLock) { uwStates.toList() }
```

- [ ] **Step 4: Point the VM poll at `uwSnapshot()`**

In `BattleViewModel.kt:233`, change the poll read from `eng.uwStates.map { uw ->` to `eng.uwSnapshot().map { uw ->`:
```kotlin
                        uwSlots = eng.uwSnapshot().map { uw ->
```

- [ ] **Step 5: Run to verify it passes**

Run: `./run-gradle.sh testDebugUnitTest --tests "com.whitefang.stepsofbabylon.presentation.battle.engine.GameEngineConcurrencyTest" > /tmp/t8.log 2>&1; tail -n 30 /tmp/t8.log`
Expected: PASS (3 tests in the class).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngine.kt \
        app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/BattleViewModel.kt \
        app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/engine/GameEngineConcurrencyTest.kt
git commit -m "fix(#191): guard initUWs under entitiesLock + uwSnapshot() for the poll read (CONC-2)"
```

---

## Task 9: Full suite, docs sync, checkpoint

**Files:**
- Modify (docs): `CLAUDE.md` (headline test count), `CHANGELOG.md`, `docs/agent/STATE.md`, `docs/agent/RUN_LOG.md`, `docs/steering/source-files.md`, `docs/plans/plan-FORWARD.md` (Gate H status), and an ADR if warranted.

- [ ] **Step 1: Run the full JVM suite + lint + assemble**

Run: `./run-gradle.sh testDebugUnitTest lintDebug assembleDebug > /tmp/full.log 2>&1; tail -n 30 /tmp/full.log`
Expected: `BUILD SUCCESSFUL`, all tests green. Capture the new total test count (was 1054 JVM) for the CLAUDE.md headline.

- [ ] **Step 2: Sync current-state docs (PR Task-List Convention, BEFORE the STATE/RUN_LOG update)**

Per CLAUDE.md's PR Task-List Convention, audit and touch only what this PR invalidates:
- `CLAUDE.md` — update the headline test count (Testing section); add a one-line fragile-zone note for the new `effectsLock` (EffectEngine) and `uwSnapshot()` if appropriate (mirror the #118 entry).
- `CHANGELOG.md` — add a `[Unreleased]` entry for #190/#191 (crash visibility + two battle CMEs).
- `docs/steering/source-files.md` — add `CrashBreadcrumbStore.kt`, `CrashBreadcrumb.kt`, `BattleErrorOverlay.kt`; note the modified files' new responsibilities.
- `docs/plans/plan-FORWARD.md` — flip Gate H #190/#191 from blocker → done (leave #192/#193/#194/#195 as-is).
- No schema change → do NOT touch `docs/database-schema.md`. No dep change → do NOT touch `tech.md`/`lib-*.md`.

- [ ] **Step 3: Add/update an ADR if warranted**

A new ADR for "local crash breadcrumb + guarded loop + loop-crash no-persist policy" is warranted (it's a non-trivial decision: chosen over a remote SDK; stop-and-surface over skip-frame; no-persist-on-crash). Create `docs/agent/DECISIONS/ADR-0026-*.md` summarizing the decision + the alternatives rejected.

- [ ] **Step 4: Update STATE.md + append RUN_LOG.md, then commit the doc sweep**

Run the `/checkpoint` skill (it performs the STATE/RUN_LOG writes + doc-drift sweep), or do it manually:
```bash
git add CLAUDE.md CHANGELOG.md docs/
git commit -m "docs(#190,#191): sync current-state docs + ADR-0026 (crash visibility + battle concurrency)"
```

- [ ] **Step 5: Push the branch + open the PR**

```bash
git push -u origin fix/190-191-crash-visibility-battle-concurrency
gh pr create --fill --base main
```
The PR description should summarise: #190 (chaining handler + breadcrumb + guarded loop + no-persist-on-crash + next-launch notice) and #191 (EffectEngine effectsLock + initUWs under entitiesLock). Link both issues with `Closes #190` / `Closes #191`.

---

## Self-Review notes (coverage map)

- **Spec §A1 (store)** → Task 1. **§A2 (handler)** → Task 3. **§A3 (Delete-All-Data wipe)** → Task 2.
- **Spec §B1 (loop guard)** → Task 4. **§B2 (wiring + re-seed + onBattleLoopError stop/no-persist)** → Tasks 5 (surface) + 6 (VM). **§B3 (overlay + chrome suppression)** → Task 6.
- **Spec Part C (next-launch notice)** → Task 6 Step 8.
- **Spec §D1 (EffectEngine)** → Task 7. **§D2 (uwStates)** → Task 8. **§D3 (tests)** → Tasks 7+8 tests.
- **Spec test plan** → CrashBreadcrumbStoreTest (T1), chaining-handler test (T3), GameLoopThread guard test (T4), EffectEngineConcurrencyTest (T7), uwStates replay test (T8), VM battle-error + no-persist (T6).
- **Type consistency:** `record(threadName, throwable, timestampMillis)` used identically in Tasks 1/3/4; `onLoopError: ((Throwable) -> Unit)?` identical in Tasks 4/5; `onBattleLoopError(t)` / `battleError` / `uwSnapshot()` / `showGameChrome` consistent across Tasks 6/8.
