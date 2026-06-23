# Plan — Batch D1: Release/CI Config Hardening (audit findings, 2026-06-23)

**Status:** REVIEWED (adversarial review gate passed 2026-06-23 — see "Review outcome" at end)
**Scope:** CI/release workflow + gradle-config hardening. **No app/Kotlin/schema change.** All edits are in
`.github/workflows/*.yml`, `gradle.properties`, and `app/build.gradle.kts` (NDK pin only).
**Source:** Batch D of the audit-tracker triage (HEAD `617babd`; re-grounded at `13d19c2`).
**Findings closed:** #262 **L39, L68, L73, L74, L75, L71, L50, L69** + a ktlint-job split (CI-speed, developer-requested).
**Split from D2:** the additive tooling (L77 SCA + #218 Kover) is a separate PR — new CI capability that can
be flaky, isolated so it can't block these 8 low-risk config fixes.

## Decisions (developer-approved, this session)
- **D1 = release/CI config hardening + the ktlint-job split.** D2 (SCA + Kover) ships separately.
- **L50 gradle flags: `org.gradle.parallel=true` + `org.gradle.caching=true` only** — NOT
  `configuration-cache` (fragile on AGP 9 / Hilt-KSP / the benchmark plugins).
- **L73: cert-fingerprint assertion** — keep `jarsigner -verify` but additionally assert the AAB's signer
  cert matches the upload keystore.
- **CI parallelism: hoist ktlint into its own lightweight job** (no Gradle/SDK/compile → fails fast) +
  the L50 flags. Compile-bound steps (unit/lint/assemble/detekt) stay in one job (splitting them would
  duplicate Kotlin compilation across cold runners — higher cost, marginal wall-clock gain).

## ⚠️ Grounding notes that shape the plan (verified at HEAD `13d19c2`)
- **L71 must use `lintRelease`, NOT `assembleRelease`.** `app/build.gradle.kts:163-189` has a
  `gradle.taskGraph.whenReady` guard that **hard-fails** any task matching `^(bundle|assemble|package).*Release$`
  when `play.licenseKey` is blank (#124 fail-closed). The PR gate has no `PLAY_LICENSE_KEY` secret → blank →
  `assembleRelease` would FAIL the build. `lintRelease` does NOT match that regex (it's a `lint` task), so it
  lints the release variant's merged/minified/shrunk config **without** tripping the guard and **without**
  needing signing secrets. (Verified the regex + the `lint{}` block.)
- **L73 needs no new secret.** The upload keystore is already decoded in the release job
  (`release/upload-keystore.jks` from `UPLOAD_KEYSTORE_BASE64`). Derive the expected SHA-256 cert fingerprint
  from the keystore via `keytool -list`, extract the AAB's signer cert fingerprint, and assert equality —
  proving the AAB was signed by *that* keystore. No fingerprint secret required.
- **L75 (`contents: write` on dependency-submission.yml):** the `gradle/actions/dependency-submission`
  action pushes the dependency graph via the Dependency-submission API, which **requires `contents: write`**.
  This is the action's documented required scope, **not** an over-grant → **L75 is effectively a non-issue;
  document and close, do NOT narrow** (narrowing would break graph submission). (Flag for reviewer.)
- **L74 confirmed LIVE:** `release.yml` has no `concurrency:` block (ci.yml + pages.yml do).

## Edit list (each grounded at HEAD `13d19c2`)

### release.yml
1. **L74 — add a release concurrency guard.** Top-level (after `permissions`):
   ```yaml
   concurrency:
     group: release-${{ github.ref }}
     cancel-in-progress: false
   ```
   `cancel-in-progress: false` (NOT true) — a release upload must never be interrupted mid-flight; this
   serializes overlapping `v*` tag pushes instead of cancelling one. (Mirrors pages.yml's choice.)
2. **L68 — tag↔versionCode/versionName consistency guard.** New step BEFORE "Build release bundle"
   (`release.yml`, after the wrapper-validation/setup, ideally right after checkout): parse the committed
   `versionName` from `app/build.gradle.kts`, compare to the pushed tag (`$GITHUB_REF_NAME`, e.g. `v1.0.11`
   → `1.0.11`). Fail with `::error::` if they mismatch. Skip the check on `workflow_dispatch` (no tag).
   ```
   tag="${GITHUB_REF_NAME#v}"
   vn=$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
   if [ "$GITHUB_EVENT_NAME" = "push" ] && [ "$tag" != "$vn" ]; then
     echo "::error::Tag $GITHUB_REF_NAME ($tag) != committed versionName ($vn) in app/build.gradle.kts"; exit 1
   fi
   ```
   (versionName is the source of truth the human bumps; comparing it to the tag catches a tag/commit mismatch
   before a wrong-version AAB ships. versionCode is not in the tag, so versionName is the right comparand.)
3. **L73 — cert-fingerprint assertion.** Replace the bare "Verify signature" step with: (a) keep
   `jarsigner -verify` (integrity), (b) extract the upload keystore's cert SHA-256 — **scope to the signing
   alias explicitly** (REVIEW): `keytool -list -v -keystore release/upload-keystore.jks -alias "$KEY_ALIAS"
   -storepass "$STORE_PASSWORD"` (the `KEYSTORE_KEY_ALIAS` secret already exists — no new secret), grep the
   `SHA256:` line; (c) extract the AAB's signer cert SHA-256 — **use `keytool -printcert -jarfile
   app/build/outputs/bundle/release/app-release.aab`** (REVIEW: prefer this over parsing `jarsigner -verbose`
   output — robust, single SHA256 line) and grep `SHA256:`; (d) normalize both (strip `SHA256:`/whitespace/
   colons, upper-case) and assert equality, fail `::error::` otherwise. Runs AFTER "Decode signing keystore".
   Pins the **identity**, not just integrity. Document: Play App Signing re-signs server-side, but asserting
   the *upload* key catches a wrong-key push before Play rejects it.
4. **L39 — secret-file cleanup.** Add a final `if: always()` step that removes the materialized secret files:
   `release/upload-keystore.jks`, `keystore.properties`, `local.properties` (the AdMob/license lines).
   `if: always()` so cleanup runs even on a mid-lane failure. (Hosted runners are ephemeral so the residual
   risk is low, but explicit cleanup is the documented best practice + defends against a future
   artifact-upload step accidentally capturing them.)

### ci.yml
5. **L71 — release-variant lint.** Add `lintRelease` to the build-and-test gate (gated on
   `needs.changes.outputs.code == 'true'`). Simplest: extend the existing "Lint, unit tests, debug build"
   step to `./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug --stacktrace`. **REVIEW (wording
   precision):** `lintRelease` runs Android lint over the **release variant's sources, manifest, merged
   resources, and release lint config** — it does NOT inspect the post-R8/post-shrink *output* (R8 runs only
   during `assemble`/`bundleRelease`). The value is catching release-variant-only lint that `lintDebug`
   skips (release manifest/resource config, release-only `isMinifyEnabled` lint warnings), NOT auditing the
   minified bytecode. Does NOT trip the license guard (`lintRelease` ∉ the `^(bundle|assemble|package).*Release$`
   regex) and needs no signing secrets. **Reviewer call:** if it materially slows the gate, make it its own
   step so timing is visible. **NOTE:** there is no Android `lint{}` baseline — if `lintRelease` surfaces NEW
   release-only errors (esp. the promoted `HardcodedText`), triage them before merge (run locally first).
6. **ktlint-job split (CI speed).** Extract the ktlint step into its own job `ktlint` (parallel to
   `build-and-test`, also `needs: changes`, gated on `code == 'true'`): checkout + the curl/SHA-pin/chmod +
   `./lint-kotlin.sh`. No JDK/Android-SDK/Gradle setup (ktlint is a standalone binary). Remove the ktlint
   step from `build-and-test`. Net: a formatting nit fails in ~30s instead of after the ~6-min compile, and
   it's off the critical path. (detekt STAYS in build-and-test — it's a Gradle task needing type resolution,
   so splitting it would duplicate compilation.)
   - **Branch-protection note:** if `build-and-test` is the named required status check, adding a new
     required `ktlint` job may need the repo's branch-protection settings updated. Flag this — the PR can
     merge on the existing check, and the new job will report; making it *required* is a separate settings
     step (document, don't assume repo-admin changes).

### gradle.properties
7. **L50 — build-perf flags.** Append:
   ```
   org.gradle.parallel=true
   org.gradle.caching=true
   ```
   (NOT `configuration-cache` — see decision.) Add a one-line comment. **Verify a clean build still passes**
   (parallel + cache are broadly safe, but confirm no task has an undeclared-input/output issue that
   parallelism surfaces).

### app/build.gradle.kts
8. **L69 — pin the NDK version.** The release block sets `ndk { debugSymbolLevel = "FULL" }` (line 127-129)
   but no `android.ndkVersion`, so symbol generation depends on whatever NDK the runner image ships
   (non-reproducible). Add `ndkVersion = "<pinned>"` in the `android {}` block. **Reviewer/grounding action:**
   pick a version that (a) ships in the `android-actions/setup-android@v4.0.1` runner image and (b) is
   compatible with AGP 9.2.1 — default to the AGP-recommended NDK (likely `27.x` or `28.x`; confirm against
   the AGP 9.2 docs / runner image rather than guessing). If a clean pin isn't confirmable without a CI
   round-trip, the fallback is to **document the dependency** and defer the pin — do NOT pin a version that
   isn't on the runner (that would break the release build). Flag for reviewer.

### L75 — no code change
9. **Document L75 as a non-issue** (see grounding note): `contents: write` is the dependency-submission
   action's required scope. Add a one-line clarifying comment to `dependency-submission.yml` confirming the
   scope is intentional/required, and close L75 in the tracker as "working as designed."

## Verification
- **No app/test change → test count unchanged (1256 JVM).** Run `./run-gradle.sh testDebugUnitTest` once to
  confirm the gradle.properties flags don't perturb the build (BUILD SUCCESSFUL, 1256, 0 failures).
- **`lintRelease` actually runs clean locally:** `./run-gradle.sh lintRelease` (no secret → confirm it does
  NOT trip the license guard, per the grounding note, and reports lint on the release variant). If it
  surfaces NEW release-only lint errors, triage them (may need a lint baseline or fixes — flag scope).
- **Build-perf flags:** `./run-gradle.sh clean assembleDebug` with the new flags → BUILD SUCCESSFUL, no new
  parallelism/cache warnings-as-errors.
- **YAML validity:** the release.yml steps are bash; lint them mentally / `actionlint` if available. The
  release lane only fully runs on a `v*` tag — so L68/L73/L39/L74 **cannot be exercised by the PR gate**;
  the PR proves only that ci.yml + gradle + lintRelease are green. **The release-lane steps get their real
  test on the next `v*` tag** — call this out in the PR so the developer knows.
- **ktlint job:** confirm the new job's `./lint-kotlin.sh` passes (baseline-gated, exit 0) and that removing
  it from build-and-test didn't drop coverage.

## PR Task-List (mandatory convention — sync current-state docs BEFORE STATE/RUN_LOG, then commit)
1. Apply edits 1–9.
2. Verify: `testDebugUnitTest` (1256, unchanged), `lintRelease` clean, `clean assembleDebug` with new flags green.
3. **Sync current-state docs:** CHANGELOG `[Unreleased]` entry. `docs/plans/plan-32-ci.md` + ADR-0018 — note
   the release-lane hardening (tag guard / cert assertion / secret cleanup / concurrency) + the ktlint job
   split + the lintRelease gate (these are the canonical CI/CD references). **REVIEW: while editing
   plan-32-ci.md, CORRECT the pre-existing misstatement at `plan-32-ci.md:130`** — it says the play.licenseKey
   guard "is scoped to `bundleRelease`/`assembleRelease`", but the actual guard is the broader regex
   `^(bundle|assemble|package).*Release$` (with Benchmark/NonMinified exclusions). Fix the description to the
   true scope while adding the lintRelease note (do not propagate the error). README/CLAUDE CI bullets if they
   enumerate the gate steps. **No test-count change.**
4. **Update `docs/agent/STATE.md`** (CURRENT objective; test count UNCHANGED at 1256) **+ append `RUN_LOG.md`.**
5. Commit on branch `ci/batch-d1-release-hardening`; open PR; check off L39/L68/L71/L73/L74/L75/L50/L69 in #262 on merge.

## Risk
**Low for the PR gate, deferred-verify for the release lane.** The ci.yml/gradle changes are PR-validated.
The release.yml changes (L68/L73/L39/L74) are **only exercised on a `v*` tag** — a bug there wouldn't surface
until the next release. Mitigations: each release step is defensively written (fail-closed with clear
`::error::`), the cert assertion is additive (jarsigner -verify stays), and the cleanup is `if: always()`.
The two items with real "could break something" potential: (a) **L69 NDK pin** — pinning a version not on the
runner would break the release build; mitigated by confirming against the runner image or deferring. (b)
**L71 lintRelease** — could surface pre-existing release-only lint errors that block the gate; mitigated by
running it locally first. No app behavior, no schema, no economy/engine change.

## Review outcome (adversarial gate, 2026-06-23)

Reviewed via a multi-agent `Workflow` (D1 + D2 reviewed concurrently; 4 dimensions × verify→skeptic at HEAD
`13d19c2`). For D1: the substantive design (L68 tag guard, L73 identity assertion, L39 cleanup, L74
concurrency, L50 flags, L69 NDK defer-if-unconfirmable, ktlint split) was **confirmed sound** — most findings
were the plan's own caveats being affirmed (L71-vs-license-guard, L50 safety, L69 deferral, ktlint
Gradle-free split all REFUTED as "fine in plan"). Three refinements applied:

- **L73 (partial):** pinned `-alias "$KEY_ALIAS"` in the keystore `keytool -list` (the literal omitted it),
  and standardized the AAB-cert extraction on `keytool -printcert -jarfile` (robust vs parsing
  `jarsigner -verbose`). Both already-available; no new secret.
- **L71 (partial, wording):** corrected "lints the R8/shrink/minified config" — `lintRelease` analyzes the
  release variant's sources/manifest/resources/lint-config, NOT the post-R8 output (R8 runs only in
  assemble/bundle). Added the "no lint baseline → triage new errors" note.
- **plan-32-ci.md:130 (confirmed nit):** that doc already misstates the license-guard scope as
  `bundleRelease`/`assembleRelease`; D1's doc-sync now CORRECTS it to the broad regex rather than
  propagating the error.

**L75 settled:** the review confirmed top-level least-privilege perms are already correct (`contents: read`
on ci.yml) and `contents: write` on dependency-submission.yml is the action's REQUIRED scope (it pushes the
dependency graph) — so L75 is document-and-close, not a narrowing. **Completeness:** the review confirmed the
other CI-adjacent LIVE findings are correctly elsewhere — A33 (dev-only pre-release deps), L37/L43/L38
(release-log-hardening = security/Batch G), L35 billing-anti-fraud (by-design, separately tracked) — none
belong in D1.
