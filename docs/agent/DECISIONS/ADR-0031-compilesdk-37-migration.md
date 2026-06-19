# ADR-0031: compileSdk 36 → 37 migration + dependency unblock

Status: Accepted (2026-06-19)

## Context

The project pinned `compileSdk = 36` across all three Android modules (`:app`, `:baselineprofile`,
`:macrobenchmark`). This was a **deliberate, documented constraint** (catalog comment + STATE/CHANGELOG/
RUN_LOG): several AndroidX artifacts declare an AAR-metadata floor of `compileSdk ≥ 37`, so the pin held
back a recurring set of dependency bumps — core-ktx 1.19.0 (#199, closed-deferred), lifecycle 2.11.0,
sqlite-ktx 2.6.2. The pin was originally adopted alongside the Health Connect 1.1.0 decision (#33), whose
catalog comment said "revisit a 1.2.x beta/stable post-launch when compileSdk moves to 37."

The recurring cost surfaced concretely: Dependabot's grouped `all-gradle` PR (#288) failed CI at
`:app:checkDebugAarMetadata` because its core-ktx/lifecycle bumps require compileSdk 37. The developer
asked to "nip this in the bud" — raise compileSdk and unblock the gated deps in one verified PR.

AGP 9.2.1 already satisfied the "AGP ≥ 9.1" half of the core-ktx/HC upgrade gate; only the compileSdk-37
half remained.

## Decision

**Raise `compileSdk` 36 → 37 in all three modules.** Keep `targetSdk = 36` and `minSdk = 34`.

- This is a **compile-time** change: it changes the SDK we compile against (new APIs visible, new lint
  checks run) but NOT the runtime behavior the OS applies to the app — that is `targetSdk`, which stays 36.
  Raising `targetSdk` is a separate, behavioral decision that requires on-device verification of Android-17
  OS behavior changes and is explicitly out of scope here.

**Unblock the gated dependency pins:** core-ktx 1.17.0 → 1.19.0 (closes #199), lifecycle 2.10.0 → 2.11.0,
sqlite-ktx 2.4.0 → 2.6.2.

**Health Connect stays at 1.1.0.** HC 1.2.x is still alpha-only (`1.2.0-alpha04` latest; no beta/stable).
The compileSdk-37 half of its gate is now cleared, but the #33 policy (no alpha AndroidX on a load-bearing
dependency) still holds. The catalog rationale comment was rewritten to record this: the HC gate is now
"reaches beta/stable", not "compileSdk moves to 37".

## Alternatives considered

- **A — Pin only (compileSdk 37, no dep bumps).** Smallest diff; the deps would then flow via Dependabot
  #288. Rejected per the developer's choice: doing the unblock here proves the migration end-to-end and
  fully "nips it", rather than leaving #288 to re-verify.
- **B — Also raise targetSdk to 37.** Rejected: behavioral, needs device testing of new OS behaviors;
  separate concern.
- **C — Also raise Health Connect to 1.2.x.** Rejected: 1.2.x is alpha-only; contradicts the #33
  no-alpha-on-load-bearing-deps policy.
- **D — Fold in the rest of #288's routine bumps.** Rejected: conflates a deliberate architectural change
  with routine bumps; those aren't compileSdk-gated and ride #288's grouped PR post-merge.

## Consequences

- **Positive:** unblocks core-ktx/lifecycle/sqlite (and any future bump that wanted compileSdk 37);
  removes the recurring Dependabot failure mode; closes #199. compileSdk 37 surfaced **zero new lint
  findings** and the full suite (1126 JVM) + R8 release build pass.
- **Negative / tradeoffs:** API 37 (Android 17) is recent; mitigated by keeping targetSdk at 36
  (compile-only) and by verifying lint/tests/R8 at 37. No lint baseline exists, so any future lint check
  introduced at a higher SDK hits the PR gate directly (acceptable — caught in-PR). CI must auto-provision
  platform 37 (`setup-android` takes no platform pin; AGP downloads what compileSdk requests — confirmed by
  the PR's own green CI run).
- **Follow-ups:** targetSdk 37 (behavioral, device-tested) when warranted; Health Connect 1.2.x once it
  reaches beta/stable; the rest of #288's bumps ride Dependabot's grouped PR.

## Verification

API 37 confirmed published on the stable SDK channel. Locally (platform 37 installed via
`sdkmanager "platforms;android-37.0" "build-tools;37.0.0"`): `testDebugUnitTest` (1126) + `lintDebug`
(0 new findings) + `assembleDebug` + `:baselineprofile:assemble` + `:macrobenchmark:assemble` + a full
`:app:assembleRelease` (R8/minify at compileSdk 37) all BUILD SUCCESSFUL. `releaseRuntimeClasspath`
resolves core-ktx → 1.19.0, lifecycle → 2.11.0, sqlite-ktx → 2.6.2 (no clamp). CI (PR gate + connected
lane) green on the PR.

## Links

- Spec: `docs/superpowers/specs/2026-06-19-compilesdk-37-migration.md`
- Plan: `docs/superpowers/plans/2026-06-19-compilesdk-37-migration.md`
- Commit(s): (this PR)
- Related: #199, #288 (Dependabot all-gradle), #33 (HC 1.1.0), ADR-0025 (multi-module / benchmark tooling)
