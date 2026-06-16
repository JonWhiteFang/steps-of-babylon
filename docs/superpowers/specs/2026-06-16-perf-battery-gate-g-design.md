# Design Spec — #26 Performance & Battery (Gate G, in-repo slice)

**Date:** 2026-06-16
**Issue:** [#26](../../../issues/26) — "Improve mobile runtime performance, startup time, and battery efficiency" (roadmap item V1X-23)
**Gate:** Closed-Test Readiness Gate **G. Performance & battery** (`docs/plans/plan-FORWARD.md`)
**Status:** spec — pending Adversarial Review Gate before a plan is written.

---

## 1. Problem & framing

Mobile incremental/walking games are opened in frequent short bursts and judged heavily on startup
speed, runtime smoothness, and battery cost. Issue #26 (V1X-23) asks for Baseline Profiles, startup
profiling, recomposition/bitmap/Room profiling, a battery audit, and OEM device validation.

**The fundamental scope boundary:** several of #26's acceptance criteria — overnight idle-drain,
background-process behaviour on Samsung/Xiaomi/OnePlus — **cannot be closed inside the repository.**
They require physical devices and developer judgment. This spec therefore scopes the **code-addressable
in-repo slice** and explicitly records the device-measured battery/OEM line as a **deferred manual pass**
(never silently dropped — it stays a tracked Gate-G line in `plan-FORWARD.md`).

**This round's scope (developer-chosen): measurement infrastructure + safe runtime fixes.**

In scope:
1. **Measurement infrastructure** — `androidx.profileinstaller` in `:app`, the `androidx.baselineprofile`
   plugin, a `:baselineprofile` generator module (commits `baseline-prof.txt`), and a `:macrobenchmark`
   module (cold-start `StartupTimingMetric` + a Home→Workshop→Battle journey). Generated/run **locally on
   a real device**; **not** gated in CI (emulator timing is noisy — Google's own guidance).
2. **Safe runtime fixes** — the already-identified, behaviour-preserving GC-churn hotspots that do NOT
   touch a fragile economy/concurrency invariant: audit **#28** (CollisionSystem per-frame list
   allocations) and **#31** (CHRONO_FIELD per-frame `Paint`), plus **#29** (missing
   `distinctUntilChanged` on the shared profile Flow) **if it lands without perturbing any ViewModel
   test**.
3. **Battery audit document** — a written inventory of every wake/wakeup source + a measurement procedure
   + candidate tunings stated as hypotheses (NOT applied this round).
4. **Startup baseline document** — the measured cold-start number (None vs BaselineProfiles).

Explicitly OUT of scope this round (deferred, with rationale):
- **Cadence tuning** (notification 30s→60s, HC sync 15→30 min). Behaviour-changing; needs device
  verification that cross-validation accuracy / step-freshness isn't degraded, which can't be fully done
  in-repo. The battery-audit doc specs it for a future PR.
- **Audit finding #11/#18 — caching `getAliveEnemies()` per frame.** This is the audit's headline perf
  win, but its recommended fix **directly violates the #125 fragile-zone invariant** ("`getAliveEnemies()`
  must NOT be cached across a frame — `takeDamage` re-fires `onDeath` on a corpse; a shared snapshot
  double-credits kills", guarded by `R125` GameEngineTest). Doing it safely needs the #146-style
  "guard the corpse across the orb/UW hit paths" refactor — a behaviour-sensitive economy change.
  **Excluded from a "safe fixes" round**; stays in #128 / a dedicated follow-up.
- **Recomposition / bitmap profiling fixes** — the *tooling* this round produces is what makes those
  measurable; any fixes they reveal are follow-ups, not this PR.
- **OEM device matrix + overnight idle-drain** — physical-device, developer-judgment slice.

### Ground-truth verification (performed while writing this spec, against `main`)

- No `:macrobenchmark` / `:baselineprofile` module, no `androidx.profileinstaller`, no `baseline-prof.txt`,
  no startup tracing exist today (`settings.gradle.kts` includes only `:app`; catalog has no profile/
  benchmark entries).
- Instrumented CI lane runs `connectedDebugAndroidTest` on an **API-34 emulator** (reactivecircus runner,
  `.github/workflows/instrumented.yml`). A Macrobenchmark module *could* slot into this infra (V1X-08
  enabled `androidTest`), but emulator timings are unreliable → not gated.
- **#28 is still open:** `CollisionSystem.checkCollisions` (`CollisionSystem.kt:24-26`) allocates three
  `filterIsInstance<…>().filter{}` lists per call; the call site is `GameEngine.kt:478`, already **inside
  `synchronized(entitiesLock)`** (lock taken at `GameEngine.kt:441`). `CollisionSystem` is a Kotlin
  `object` (singleton) — scratch lists therefore must live on the **engine instance**, not the object.
- **#31 is still open:** `GameEngine.render()` allocates `android.graphics.Paint()` per frame for the
  CHRONO_FIELD overlay (`GameEngine.kt:512-513`), beside already-cached `hpPercentPaint`/`bossCountdownPaint`.
- **#30 is ALREADY FIXED** — `StatsViewModel.buildBars` QUARTER branch (`StatsViewModel.kt:103-122`)
  already parses each row once in a single O(history) pass (carries a `#30` comment). **Dropped from
  this spec's fix set.**
- **#29 is still open:** `distinctUntilChanged` appears nowhere in `app/src/main`. `PlayerRepositoryImpl`
  `observeProfile/observeWallet/observeTier` (`:18-26`) all map `dao.get()` with no distinct guard.

---

## 2. Goals / non-goals

**Goals**
- A repeatable, committed Baseline Profile covering the most-used path (**Home → Workshop → Battle**).
- A documented cold-start baseline number (None vs BaselineProfiles) → satisfies "startup metrics documented".
- A documented battery-audit *process* + wake-source inventory → satisfies "battery profiling process exists"
  and "hotspots documented and prioritised".
- Eliminate the two confirmed, fragile-zone-safe GC-churn hotspots (#28, #31) with **provably identical
  behaviour**.
- Keep JVM + CI green; do not weaken any fragile-zone invariant (#118, #124, #125).

**Non-goals**
- Closing the device-measured battery / OEM-matrix Gate-G line (manual, deferred).
- Any cadence/behaviour tuning of services or workers.
- The `getAliveEnemies()` caching refactor (#11/#18) — excluded as above.
- Adding perf-timing assertions to the CI gate.

---

## 3. Architecture — measurement infrastructure

Follows the AndroidX-recommended **two-test-module** split (baseline-profile generation and
macrobenchmark are distinct plugin responsibilities and are kept separate).

### 3.1 `:app` changes
- Apply `alias(libs.plugins.androidx.baselineprofile)`.
- Add `implementation(libs.androidx.profileinstaller)` (so the committed profile is installed at runtime).
- Add a `baselineProfile { }` consumer block referencing `:baselineprofile`.
- The committed profile lands at `app/src/main/baseline-prof.txt` (and `startup-prof.txt` if generated).

### 3.2 New module `:baselineprofile` (`com.android.test`)
- `BaselineProfileGenerator` (Macrobenchmark `BaselineProfileRule`) drives the critical user journey:
  cold launch → Home → navigate to Workshop → enter a Battle → let a wave run briefly. This is the path
  whose classes/methods get AOT-compiled.
- Output consumed by `:app`'s `baselineProfile { }` and committed.

### 3.3 New module `:macrobenchmark` (`com.android.test`)
- `StartupBenchmark` — `StartupTimingMetric`, cold/warm/hot, comparing `CompilationMode.None()` vs
  `CompilationMode.Partial(BaselineProfiles())` to quantify the profile's win.
- `JourneyBenchmark` — `FrameTimingMetric` over the Home→Workshop→Battle journey (jank visibility).
- Targets a `:app` **`benchmark`-style release variant** that is `profileable` and not debuggable
  (standard Macrobenchmark setup).

### 3.4 Catalog / Gradle plumbing
- New `gradle/libs.versions.toml` entries: `androidx.profileinstaller`, `androidx.benchmark.macro.junit4`,
  `androidx.test.uiautomator` (UiAutomator drives the journey), and the `androidx.baselineprofile` +
  (if needed) `androidx.benchmark` plugin aliases. Versions resolved from the catalog only (never hardcoded).
- `settings.gradle.kts` includes `:baselineprofile` and `:macrobenchmark`.

### 3.5 Fragile-zone reconciliation — #124 license-key guard (load-bearing)
The `androidx.baselineprofile` / benchmark setup creates extra release-type variants
(`benchmarkRelease`, `nonMinifiedRelease`) whose Gradle task names (`assembleBenchmarkRelease`, etc.)
**match the existing `^(bundle|assemble|package).*Release$` regex** in `app/build.gradle.kts:158` — which
would falsely trip the **#124 fail-closed license-key guard** and break benchmark builds.

**Fix (preserves the #124 invariant):** narrow the guard so it fires only on the **shippable** variants
and explicitly excludes the benchmark/non-minified ones. Concretely, match the real release artifact tasks
(`assembleRelease`/`bundleRelease`/`packageRelease` — i.e. the `Release` variant produced for shipping)
and exclude any task whose name contains `Benchmark` or `NonMinified`. A real release with a blank
`play.licenseKey` **still hard-fails** (the invariant #124 protects is unchanged); we only stop a
benchmark build from a false positive.

- This is a fragile-zone touch and is called out for the Adversarial Review Gate.
- A guard for the guard: the plan adds a check (test or documented manual verification) that
  `bundleRelease` with a blank key still throws, and `assembleBenchmarkRelease` does not.

### 3.6 CI posture
- PR gate + instrumented lane must continue to compile the workspace (now incl. the two new modules) and
  stay green. **No perf-timing assertions are added to CI** (emulator numbers flake). Baseline-profile
  generation and benchmarks run **locally on a real device** by the developer.
- Verify the new `com.android.test` modules don't perturb `assembleDebug` / `testDebugUnitTest` / the
  release lane.

---

## 4. Architecture — safe runtime fixes

All three are behaviour-preserving and each carries an explicit risk gate.

### 4.1 #28 — CollisionSystem per-frame list allocations
- **Site:** `CollisionSystem.checkCollisions` (`CollisionSystem.kt:24-26`); caller `GameEngine.kt:478`,
  inside `synchronized(entitiesLock)` (`:441`).
- **Fix:** replace the three `filterIsInstance<…>().filter{}` allocations with **three reusable
  `ArrayList` scratch buffers owned by the `GameEngine` instance** (NOT the `CollisionSystem` object —
  it's a singleton; instance-owned buffers avoid cross-engine shared mutable state). Per tick: `clear()`
  the three buffers, do **one `for (e in entities)` partition pass** by type + `isAlive`, hand the
  buffers to `simulation.detectProjectileEnemyHits` / `detectZigguratHits`.
- **Signature:** `checkCollisions` takes the three pre-filled typed lists (the partition moves to the
  caller under-lock), keeping `CollisionSystem` a thin stateless adapter.
- **Risk gate:** the partition pass iterates `entities` → **must remain under `entitiesLock`** (#118).
  The simulation sweep contract is unchanged (it already receives `List<…>`). Behaviour must be
  byte-identical.
- **Regression:** existing `CollisionSystemTest` / `SimulationTest` collision tests stay green; add a
  test asserting the scratch-buffer partition yields the same projectile→enemy and enemy-projectile→
  ziggurat hit outcomes as the old `filterIsInstance` path (incl. dead-entity exclusion). Verify buffers
  are cleared between ticks (no stale carry-over).

### 4.2 #31 — CHRONO_FIELD per-frame Paint allocation
- **Site:** `GameEngine.render()` (`GameEngine.kt:512-513`).
- **Fix:** hoist the overlay `Paint` to a cached `private val chronoOverlayPaint` field next to
  `hpPercentPaint`/`bossCountdownPaint`; reuse it in `render()`.
- **Colour:** preserve the current observable colour value exactly (`0x222196F3`). The audit notes this
  literal lacks the explicit `.toInt()`/alpha the other ARGB literals carry — **do not silently change
  the rendered colour**; record the alpha question in the plan and resolve it only with an intentional,
  reviewed decision (default: keep current behaviour).
- **Risk gate:** pure render path, no game-logic change. No JVM test (Canvas) — **on-device visual
  verification** with a CHRONO_FIELD UW active is the acceptance check.

### 4.3 #29 — missing distinctUntilChanged on the shared profile Flow (include-if-clean)
- **Site:** `PlayerRepositoryImpl.observeProfile/observeWallet/observeTier` (`:18-26`), all mapping
  `dao.get()` (Room re-emits on every `player_profile` write, incl. high-frequency counter writes).
- **Fix:** map to the narrow projection **then** `.distinctUntilChanged()`, so a step-balance write
  doesn't wake a tier-only observer and unrelated counter writes don't re-fire every screen's combine
  mapper.
- **Risk gate:** behaviour-preserving for genuine value changes — it only suppresses no-op re-emissions.
  But this Flow is consumed by every screen ViewModel. **Decision rule: include only if it lands without
  perturbing any existing ViewModel/repository test; if any test's emission-count expectations break in a
  way that signals a behavioural shift, defer #29 to a follow-up** (it is the lowest-value of the three
  and not required to satisfy Gate G).
- **Regression:** a `PlayerRepositoryImpl` test asserting that two identical-value writes produce one
  downstream emission, and that a real value change still emits.

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
- #28: `CollisionSystemTest` / `SimulationTest` — scratch-buffer partition produces identical collision
  outcomes; buffers cleared between ticks.
- #29 (if included): `PlayerRepositoryImpl` distinct-emission test.
- #31: no JVM test (Canvas render path) — on-device visual only.
- Headline test count rises by the new tests; update CLAUDE.md's count line.

**Macrobenchmark / Baseline Profile (local, real device — not CI):**
- Generate + commit `baseline-prof.txt` for the Home→Workshop→Battle journey.
- Capture the cold-start baseline (None vs BaselineProfiles) into **`docs/performance/startup-baseline.md`**.

**CI:**
- PR gate + instrumented lane compile the new modules and stay green.
- No perf-timing assertions added.
- Verify the #124 guard change: `bundleRelease` with a blank key still throws; `assembleBenchmarkRelease`
  does not.

**Fragile-zone touches flagged for review:**
- #124 guard regex/predicate (must preserve fail-closed on real releases).
- #118 `entitiesLock` discipline on the #28 partition pass (must stay under-lock).
- #125 exclusion rationale (we are NOT caching `getAliveEnemies()`).

**Acceptance for this PR = the Gate-G in-repo slice:**
- Baseline Profile implemented + committed.
- Startup baseline documented.
- Battery-audit process + wake-source inventory documented.
- #28 + #31 fixed (#29 if clean); JVM + CI green.
- The device-measured battery / OEM line remains an **explicit deferred manual pass** recorded in
  `plan-FORWARD.md` (developer judgment; cannot close in-repo).

---

## 7. Open questions for the review gate

1. Is narrowing the #124 guard predicate (exclude `*Benchmark*`/`*NonMinified*`, keep fail-closed on
   shippable `Release`) acceptable, or is a separate product-flavour/variant-filter approach safer?
2. Should `:baselineprofile` and `:macrobenchmark` be two modules (AndroidX-recommended, chosen here) or
   one combined module for a smaller footprint?
3. Confirm #29 is worth the blast-radius risk on the shared profile Flow, or should it be deferred from
   the outset and the round limited to #28 + #31?

---

## 8. References

- Issue #26 / V1X-23 (`docs/plans/plan-V1X-roadmap.md:1392`).
- Gate G (`docs/plans/plan-FORWARD.md:64`).
- Audit findings #11/#18/#28/#29/#30/#31 (`docs/external-reviews/2026-06-10-multi-agent-code-audit.md`).
- Fragile zones #118 / #124 / #125 / #146 (`docs/agent/STATE.md`, CLAUDE.md).
