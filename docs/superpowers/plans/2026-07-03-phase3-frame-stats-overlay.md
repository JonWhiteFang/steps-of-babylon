# Plan — Phase 3 tooling, PR-2: DEBUG frame-stats overlay (#384)

**Tracker:** #389 Phase 3. **This PR (PR-2):** #384 (`perf-2`) only.
**Predecessor:** PR-1 (#402, merged `aa2b50c`) shipped #373/#375/#381/#382. This branch is cut from the
updated `main` per the sequential-merge rule.

**Branch:** `tooling/phase3-frame-stats-overlay`
**Fragile zone:** YES — touches `presentation/battle/GameLoopThread.kt` (the #190 game-loop crash guard +
the #126 accumulator clamp live here). **The mandatory `concurrency-reviewer` lane applies** (the agent's
scope explicitly names `GameLoopThread`; the `guard-sensitive-edits.sh` tier-4 advisory fires on this path).

---

## The ask (#384, verbatim intent)
`GameLoopThread` computes per-frame `frameTime` **only** to derive its sleep (line 88) — no min/avg/max,
dropped-frame, or UPS-vs-60 signal is retained. On a low-end device the loop could silently drop below
60 UPS with no dev-build signal. Fix: accumulate rolling min/avg/max + dropped-frame count behind a
`BuildConfig.DEBUG` overlay, **reusing the already-computed `frameTime`** — **zero release cost.**

## Ground truth (verified this session)
- `GameLoopThread.run()` (`presentation/battle/GameLoopThread.kt`): fixed-timestep loop. `TICK_NS =
  16_666_667` (~60 UPS). Per-tick `update()`/`render()` are wrapped in the **#190 REL-2 try/catch**
  (records a breadcrumb, stops the loop, fires `onLoopError` on a throw). The render happens inside
  `synchronized(surfaceHolder) { engine.render(canvas) }`, itself inside an inner
  `lockCanvas`/`unlockCanvasAndPost` try/finally that is **strictly nested inside** the outer #190 catch
  (a render crash must unlock first — pinned by `GameLoopThreadGuardTest`'s second test).
- `frameTime = System.nanoTime() - currentTime` at **line 88** already measures the update+render cost of
  the frame just completed; used only for `sleepMs`.
- `BuildConfig` is enabled (`buildConfig = true` in `app/build.gradle.kts`); `BuildConfig.DEBUG` is the
  standard debug/release discriminator (release build sets it false → the whole overlay is dead code R8
  strips). Package `com.whitefang.stepsofbabylon` → `com.whitefang.stepsofbabylon.BuildConfig`.
- `BuildConfig` is already consumed in `main/` for several fields (`USE_REAL_ADS`, `USE_REAL_BILLING`,
  `PLAY_STORE_URL`, `PLAY_LICENSE_KEY`, `AD_UNIT_*`, `VERSION_*`); `BuildConfig.DEBUG` **specifically** has
  no existing use, so this introduces that idiom — import `com.whitefang.stepsofbabylon.BuildConfig`.
  (R8-strip reasoning rests only on `BuildConfig.DEBUG` being `false` in release, independent of the above.)
- Guard test: `GameLoopThreadGuardTest` (JVM, Mockito) — two tests pinning the #190 crash-guard behaviour.

## Design (confined to GameLoopThread; loop-thread-only state; no new lock)

### Why this shape
The frame-stats state is **written and read only on the loop thread** — it never crosses a thread
boundary — so it needs **no lock and no `@Volatile`** (plain fields on the thread instance, mutated only
inside `run()`). This is the critical concurrency point the reviewer must confirm: we are NOT adding
shared mutable state, NOT taking a new monitor, NOT touching `entitiesLock`/`effectsLock`/`surfaceHolder`
ordering. The overlay draws on the canvas the loop already holds.

### Mechanics
1. **A small `FrameStats` holder** (private, loop-thread-confined) accumulating over a rolling window:
   `minMs`, `maxMs`, a running mean (sum + count, reset per window), `droppedFrames` (count of frames whose
   `frameTime > TICK_NS` — i.e. couldn't sustain 60 UPS), and the last computed values for display. Reset
   the window every N frames (e.g. 60 — ~1s) so min/max track *recent* behaviour, not all-time.
   **Prefer a tiny dedicated class** `presentation/battle/FrameStats.kt` (pure, no Android) so it is
   **JVM-unit-testable** (mirrors the repo's "pure logic is testable" preference) — the accumulation math
   (min/avg/max/dropped, window reset) is verified directly, and `GameLoopThread` just feeds it `frameTime`.
2. **Feed it the EXISTING `frameTime`** — no new timing call. Right after line 88 computes `frameTime`,
   call `frameStats.record(frameTime, TICK_NS)`. (This is OUTSIDE the render/canvas block and OUTSIDE the
   #190 try/catch — it's pure arithmetic that cannot throw, so it does not widen the guard's surface.)
3. **Draw the overlay INSIDE the existing render path, DEBUG-only.** In the
   `synchronized(surfaceHolder) { engine.render(canvas) }` block, after `engine.render(canvas)`, add
   `if (BuildConfig.DEBUG) frameStatsRenderer.draw(canvas, frameStats.snapshot())`. It shows the PREVIOUS
   window's stats (one window stale — standard for FPS overlays; acceptable). It stays INSIDE the #190
   outer try/catch (so an overlay bug can't cause silent death — it'd be caught + surfaced like any render
   crash) and INSIDE the inner canvas try/finally (so the canvas still unlocks). **Do NOT move it outside
   either guard.**
4. **Overlay drawing** — a tiny `presentation/battle/FrameStatsOverlay.kt` owning its own `Paint` (created
   ONCE, not per-frame — matches the #26 A31 "no per-frame Paint alloc" fragile-zone rule). Draws 2–3 lines
   of text top-left (UPS estimate, min/avg/max ms, dropped count). Loop-thread-confined (only the loop
   thread ever calls `draw`), so its `Paint` needs no synchronization.

### What this deliberately does NOT do
- Does NOT change the #190 try/catch structure, the inner canvas try/finally nesting, the #126 accumulator
  clamp, or any lock/monitor. Does NOT add shared state or a new lock. Does NOT touch `GameEngine` /
  `BattleRenderer` / `EffectEngine` (keeps the change off the `entitiesLock`/`effectsLock` surface).
- Does NOT ship in release: `BuildConfig.DEBUG` gates the draw; R8 strips the dead branch. The `FrameStats`
  accumulation is a few arithmetic ops per frame — negligible, but if we want *literally* zero release cost
  we also gate the `record(...)` call behind `BuildConfig.DEBUG` (decision: gate BOTH `record` and `draw`
  behind `BuildConfig.DEBUG` so release does zero extra work). **Chosen: gate both.**

### Files
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/FrameStats.kt` — NEW, pure
  (min/avg/max/dropped accumulation + window reset + immutable `snapshot()`).
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/FrameStatsOverlay.kt` — NEW, owns a
  cached `Paint`, `draw(canvas, snapshot)`.
- `app/src/main/java/com/whitefang/stepsofbabylon/presentation/battle/GameLoopThread.kt` — add the
  `frameStats` field + `if (BuildConfig.DEBUG) { record; draw }` calls at the two points above. Minimal,
  guard-structure-preserving diff.
- `app/src/test/java/com/whitefang/stepsofbabylon/presentation/battle/FrameStatsTest.kt` — NEW JVM test for
  the pure accumulation (record N frames → assert min/avg/max/dropped; window reset; empty/no-frames
  snapshot). This is idiomatically **several `@Test` methods** (cf. `SimulationMathTest`'s 6 tests for the
  single `clampAccumulator` fn), NOT one — so the test-count delta is a range, not +1 (see SCOPE-1 below).
- `app/src/test/java/…/battle/GameLoopThreadGuardTest.kt` — **ADD a third test** (SCOPE-4): the DEBUG overlay
  draw path is new surface inside the #190 guard; pin it — a throwing overlay `draw` must stop the loop,
  unlock the canvas, and fire `onLoopError` exactly once (same shape as the existing render-crash test).
  Because the draw is `BuildConfig.DEBUG`-gated and unit tests run the debug variant (`DEBUG==true`), the
  test can drive the overlay path. Regression-pins the guard over the new code instead of asserting-by-structure.

### Risks / mitigations (the concurrency-reviewer's checklist)
- **Widening the #190 guard / breaking the canvas unlock:** the overlay draw sits inside BOTH existing
  guards; `record()` is outside them but is non-throwing arithmetic. `GameLoopThreadGuardTest`'s two tests
  must still pass unchanged (a render-path crash still unlocks + fires `onLoopError` once). Re-run them.
- **New shared state / lock:** NONE — `frameStats` is loop-thread-confined (asserted in the reviewer lane +
  the KDoc). No `@Volatile`, no `synchronized`. If the reviewer finds any cross-thread read, redesign.
- **Per-frame `Paint` alloc:** the overlay caches its `Paint` (one instance), per #26 A31.
- **Release cost:** both `record` + `draw` gated behind `BuildConfig.DEBUG` → zero release work; R8 strips.
- **`GameLoopThreadGuardTest` uses a `mock<GameEngine>()`** — adding fields to `GameLoopThread` must not
  break its constructor (`GameLoopThread(holder, engine, store)` stays the same; new fields are internal).

## Task list (after this plan passes the Adversarial Review Gate + concurrency-reviewer lane)
1. `FrameStats.kt` (pure) + `FrameStatsTest.kt` (several `@Test` methods — min/avg/max/dropped, window
   reset, empty snapshot); run green.
2. `FrameStatsOverlay.kt` (cached Paint).
3. Wire into `GameLoopThread` (DEBUG-gated record + draw); keep the guard structure byte-for-byte where
   possible.
4. Re-run `GameLoopThreadGuardTest` (existing two tests stay green — guard unchanged) + **ADD a third test**
   pinning the overlay-draw crash path (throwing `draw` → loop stops, canvas unlocks, `onLoopError` once).
5. Full local gate: `testDebugUnitTest :app:koverVerifyDebug :app:detekt assembleDebug` + `./lint-kotlin.sh`.
   (Note: `koverVerifyDebug` scopes `presentation.battle.engine.*` — `GameLoopThread` is in
   `presentation.battle.*`, NOT `.engine` [confirmed: package line 1], so OUTSIDE the ratchet scope; the new
   files won't move the floor. Confirm the ratchet still passes.)
6. Sync current-state docs (source-files.md new entries; CLAUDE.md only if a stable fact changed; CHANGELOG
   `[Unreleased]`; **set the headline test count to whatever the green run reports** — expect ~+4-6 from the
   new `FrameStatsTest` methods + the third guard test, NOT a pinned +1). Update STATE.md + append RUN_LOG.md.
7. Commit; push; open PR-2; merge on green.

## Acceptance criteria
- Debug build shows a frame-stats overlay (min/avg/max ms + dropped-frame count + UPS estimate); release
  build shows nothing and does zero extra work (both `record` + `draw` behind `BuildConfig.DEBUG`).
- `FrameStatsTest` green (pure accumulation math verified).
- `GameLoopThreadGuardTest` existing two tests still green (the #190 crash guard + canvas-unlock nesting
  unchanged) + a NEW third test pins the overlay-draw crash path (throwing draw → loop stops, canvas
  unlocks, `onLoopError` once).
- `frameStats` is loop-thread-confined — no new lock, no `@Volatile`, no shared mutable state (reviewer-confirmed).
- Overlay `Paint` is cached (no per-frame alloc).
- Full local gate green; docs synced; **headline test count updated to the actual green-run number** (not a
  pre-pinned value). No schema change; no versionCode bump.
