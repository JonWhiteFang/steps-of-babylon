# Startup Baseline — Steps of Babylon (#26 / Gate G)

**Date:** 2026-06-16 · **Issue:** [#26](../../../issues/26) (Performance & battery, Gate G) · **Spec:** `docs/superpowers/specs/2026-06-16-perf-battery-gate-g-design.md`

Documents the startup-performance measurement for the Gate-G in-repo slice: the committed Baseline
Profile (generated + verified end-to-end) and the startup-timing measurement procedure + status.

---

## 1. Baseline Profile — GENERATED & COMMITTED ✅

A Baseline Profile was generated on a connected device and is committed to the repo. This satisfies the
issue's **"Baseline Profiles are implemented"** acceptance criterion.

- **Command:** `./run-gradle.sh :app:generateBaselineProfile` (drives `BaselineProfileGenerator` —
  cold launch → Home — via the `:baselineprofile` module on a connected, root-capable device).
- **Output (committed):** `app/src/release/generated/baselineProfiles/baseline-prof.txt` — this is the
  `androidx.baselineprofile` plugin's authoritative output location; the release build's
  `mergeReleaseBaselineProfile` / R8 step consumes it automatically, and `androidx.profileinstaller`
  installs it at first launch.
- **Profile size:** **18,804 rules**, of which **1,114 are app-specific** (`com/whitefang/stepsofbabylon/…`)
  — confirming the generator exercised the real app startup + Home composition, not just framework classes.
- **Device used:** `sdk_gphone64_arm64` emulator (Pixel_6 AVD, API 36 / Android 16, arm64, userdebug —
  root-capable, which `BaselineProfileRule` requires to compile + pull the profile).

### Generator scope note (deliberate)
`BaselineProfileGenerator` currently captures **cold launch + Home settle** — the resilient minimum that
works without UiAutomator label lookups. Deeper Home → Workshop → Battle navigation is a documented
on-device TODO (see the generator KDoc): add `device.findObject(By.text(...))` + click steps once the
on-device labels are confirmed, then re-run `generateBaselineProfile`. The current profile already covers
the highest-value path (process start + first-frame + Home), which is where Baseline Profiles deliver the
largest win.

The `generateBaselineProfile` run emitted a benign warning: *"No startup profile rules were generated …
includeInStartupProfile = true"*. This is expected — we did not opt into a separate startup profile; the
single baseline profile is sufficient for v1. Not an error.

---

## 2. Startup-timing numbers — measurement procedure + status

### Status: harness present; numbers are a deferred, tracked, developer-run on-device step (#385)

> **#385 (`perf-1`) status refresh (2026-07-03, Phase-4 tooling).** This deferral is now tracked as issue
> **#385**. The gap is unchanged and deliberate: capturing startup/frame **numbers** needs a non-debuggable
> `benchmark` build type on `:app` (a **fragile-zone** change to `app/build.gradle.kts` — it interacts with
> the #124 license-key fail-closed guard's task graph) **and a physical device** (emulator timings are
> unreliable and explicitly NOT CI-gated, per spec). It is a **one-time on-device developer step**, not a
> repo task an agent can complete headless — hence deferred rather than done this pass. The procedure below
> is the runbook for when that device pass happens; ADR-0025 records the original decision.

The `:macrobenchmark` module ships `StartupBenchmark` (cold-start `StartupTimingMetric`, comparing
`CompilationMode.None()` vs `CompilationMode.Partial(BaselineProfileMode.Require)`) and `JourneyBenchmark`
(`FrameTimingMetric`). However, capturing the timing **numbers** requires a non-debuggable, profileable
`benchmark` build of `:app`, which is **not wired in this round** — and deliberately so:

- A `com.android.test` module targets the app's **`debug`** variant by default, and Macrobenchmark refuses
  to measure a **debuggable** app (timings would be meaningless). Producing
  `connectedBenchmarkReleaseAndroidTest` requires adding a dedicated non-debuggable `benchmark` build type
  to `app/build.gradle.kts`.
- `app/build.gradle.kts` is a **fragile zone** (release signing config + AdMob production-ID wiring + the
  #124 license-key fail-closed guard + `ndk.debugSymbolLevel` all interact). Adding a release-derived build
  type there is a non-trivial, review-sensitive change.
- Per the spec, **emulator startup timings are unreliable and are explicitly NOT CI-gated**; the
  trustworthy numbers come from a **physical device**. Spending a fragile-zone change to obtain advisory
  emulator numbers is not justified for this round.

**Decision:** the Baseline Profile (§1) is the load-bearing Gate-G deliverable and is DONE. The startup
*timing* numbers are deferred to the same physical-device pass that closes Gate G's battery/OEM line
(see `docs/plans/plan-FORWARD.md`), where the `benchmark` build type can be added and the numbers captured
on real Samsung/Xiaomi/OnePlus/Pixel hardware.

### Procedure (when the benchmark build type is added, on a physical device)
1. Add a non-debuggable `benchmark` build type to `app/build.gradle.kts` (initWith `release`,
   `debuggable = false`, `signingConfig`, `matchingFallbacks = ["release"]`) — through the Adversarial
   Review Gate, since it touches the #124 guard's task graph.
2. `./run-gradle.sh :macrobenchmark:connectedBenchmarkReleaseAndroidTest` on a connected physical device.
3. Record the median `timeToInitialDisplayMs` for `startupNoCompilation` vs `startupBaselineProfile`.

### Honesty caveat for the recorded numbers (when captured)
The **None-vs-BaselineProfiles delta is an UPPER BOUND** on the real-world startup win:
`CompilationMode.None()` models an install with **no Play cloud profile**, whereas most Play-Store installs
eventually receive a cloud profile. The committed profile still delivers a real win on **day-one,
sideload, and cold-cache** launches — but the headline delta over `None()` overstates the steady-state
improvement. `CompilationMode.None()` remains the correct AndroidX reference baseline; only the documented
number needs this caveat.

---

## 3. Summary

| Item | Status |
|---|---|
| Baseline Profile generated + committed | ✅ done (18,804 rules; 1,114 app-specific) |
| Profile consumed at release build (profileinstaller + R8 merge) | ✅ wired (plugin-managed path) |
| Macrobenchmark startup + journey harness | ✅ present (`:macrobenchmark`) |
| Startup timing numbers | ⏸ deferred — needs a non-debuggable `benchmark` build type (fragile-zone change) + a physical device; emulator numbers are non-gating per spec |
| Deeper Home→Workshop→Battle generator nav | ⏸ on-device TODO (generator KDoc) |
