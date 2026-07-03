# Plan — Phase 3 tooling guards, PR-1 (non-fragile batch)

**Tracker:** #389 Phase 3. **This PR (PR-1):** #373 + #375 + #381 + #382.
**Deferred to PR-2 (separate, fragile zone):** #384 (GameLoopThread frame-stats overlay).
**Left blocked (documented in-issue):** #396 (detekt nested-lock rule — needs a stable custom-rule API + new module).

**Branch:** `tooling/phase3-nonfragile-guards`
**HEAD at authoring:** v1.0.12 / vc 28 / schema v12 / 1302 JVM + 9 instrumented tests.
**Scope discipline:** dev-tooling / build-config / new-test only. **Zero production runtime code changes**
(that is precisely why these four batch together and #384 is isolated). No schema change, no versionCode bump.

---

## Why these four together

All four are build-config or test additions with no shipped-code impact:
- #373 — Gradle Kover DSL + CI wiring.
- #375 — one `debugImplementation` dependency (never in the release variant).
- #381 — one new JVM/Robolectric test.
- #382 — a build-file comment refresh + one new JVM architecture test.

None touches `presentation/battle/**`, a DAO, a repo impl, or a currency path, so the mandatory
`concurrency-reviewer` lane is **not** triggered for PR-1 (it *is* for PR-2 / #384). PR-1 still gets the
standard Adversarial Review Gate (leaner fan-out — these are low-blast-radius).

---

## Ground truth gathered (verified this session, do not re-derive)

**Kover** — plugin `org.jetbrains.kotlinx.kover` **0.9.8** applied at `app/build.gradle.kts:12`; report-only
today. CI runs `:app:koverXmlReport :app:koverHtmlReport` (non-gating) at `.github/workflows/ci.yml:126-128`.
**Freshly-measured LINE coverage** (`./run-gradle.sh :app:koverXmlReport`, report Jul 3 08:17, parsed from
`app/build/reports/kover/report.xml`) for the four scoped packages:

Note `groupBy = PACKAGE` treats each fully-qualified package **separately** — `domain.battle` is not one
aggregate but two rows (`.engine` 100%, `.entity` 96.10%). Table shows covered/total (all four columns are
*covered*, not missed):

| Package (JVM/`.`-notation) | LINE | covered/total |
|---|---|---|
| `com.whitefang.stepsofbabylon.data.repository` | **56.94%** | 246 / 432 |
| `com.whitefang.stepsofbabylon.domain.usecase` | **98.73%** | 622 / 630 |
| `com.whitefang.stepsofbabylon.presentation.battle.engine` | **92.15%** | 704 / 764 |
| `com.whitefang.stepsofbabylon.domain.battle.engine` | **100.00%** | 101 / 101 |
| `com.whitefang.stepsofbabylon.domain.battle.entity` | **96.10%** | 74 / 77 |

**DSL — VERIFIED EMPIRICALLY against the 0.9.8 plugin jar + live probe builds this session (supersedes the
earlier context7 guess, which was WRONG).** Disassembling `kover-gradle-plugin-0.9.8.jar`:
- `KoverVerifyRule` (the rule scope) exposes ONLY `groupBy`, `bound`/`minBound`/`maxBound`, `disabled` —
  **there is NO `filters` on a rule.** A `rule { filters { … } }` is a configuration-time compile error
  (`'fun filters(...)' cannot be called in this context` — reproduced). So the original plan's "four rules,
  four per-package `filters` scopes" is **impossible in 0.9.8**.
- `filters { includes { classes(...) } }` exists only on `KoverReportSetConfig` (i.e. `reports.total`,
  `reports.variant("<name>")`) and on top-level `reports.filters` — and it is **shared by that set's
  `koverXmlReport`/`koverHtmlReport` AND `koverVerify`**. Filtering `total` therefore **narrows the #218
  informational whole-app report** (probed: `koverXmlReport` dropped 49 → 5 packages) — a regression of #218's
  stated "show which surfaces are uncovered" purpose. So filtering `total` is OUT.
- Top-level `reports.verify` with `groupBy = PACKAGE` evaluates **every** package app-wide (probed: it listed
  all 47, incl. the 0%-covered generated Hilt/DI/settings/help packages) → a per-package floor there is
  unshippable.

**RESOLVED design (probed working): a filtered `reports.variant("debug")` set.** The Android Kover plugin
auto-creates per-build-variant report+verify tasks (`koverVerifyDebug`, `koverXmlReportDebug`, …) alongside
the aggregate `total`/`koverVerify`. Configuring `reports { variant("debug") { filters { includes { … } }
verify { rule { … } } } }`:
- scopes **`koverVerifyDebug`** to exactly the fragile packages (probed: floor-99 failure listed ONLY
  `data.repository`, `domain.usecase`, `presentation.battle.engine`, `domain.battle.entity` — `domain.battle.engine`
  at 100% can't violate so it's silent-but-in-scope), giving **real per-package `groupBy=PACKAGE` protection**; and
- leaves **`koverXmlReport` (`total`) UNFILTERED at 49 packages** (probed) → **#218 preserved, no regression.**

CI will call **`:app:koverVerifyDebug`** (the scoped task), NOT the aggregate `:app:koverVerify`.

_(confirmed working bound shape:)_ `bound { minValue = N; coverageUnits = CoverageUnit.LINE;
aggregationForGroup = AggregationType.COVERED_PERCENTAGE }` inside `rule("name") { groupBy = …; bound { … } }`,
which lives under `kover { reports { variant("debug") { filters { … } verify { rule(…) } } } }`.
`koverVerifyDebug` fails the build on breach.

**LeakCanary** — no entry in `libs.versions.toml` today. Latest stable on Maven Central = **2.14**
(3.0 line is alpha-only). Consumed as `debugImplementation` — auto-installs, zero config, never ships.

**Room** — `2.8.4`; `room-testing` already a `testImplementation` dep
(`app/build.gradle.kts:334`). Schemas exported to `app/schemas/` (`1.json`…`12.json`) via
`room { schemaDirectory("$projectDir/schemas") }` (`:236`). **No** test `assets` srcDir wires those JSONs
today. Existing migration tests (`Migration11To12Test`, `DataTransformMigrationsTest`) run on the
**JVM/Robolectric** lane using **direct `migrate()`** calls — they assert *data transforms*, not schema
shape, and the module comment says they deliberately avoided `MigrationTestHelper` + schema-asset wiring.
`AppMigrations.ALL` = `MIGRATION_7_8 … MIGRATION_11_12`; `AppDatabase` `@Database(version = 12)`.
`AppMigrations.validateChain(...)` (pure) + `MigrationChainTest` already guard version-contiguity (#237).

**lint{}** — block at `app/build.gradle.kts:218-226`; comment claims "~110 hardcoded Compose strings"
(stale — i18n #34 is complete). The **5 real single-line `Text("…")` string-literal call sites in
`presentation/`** are ALL `$`-interpolations (numeric/badge, not prose): `HomeScreen.kt:173` + `:261`
(badge counts), `UltimateWeaponBar.kt:66` (cooldown int), `TierSelector.kt:69` (`$t`) + `:70` (`${…}x`).
(`BattleRenderer.kt:89` is a `canvas.drawText("$pct%", …)` — a Canvas draw, NOT a Compose `Text()`; the
earlier draft mis-listed it.) `HardcodedText` is promoted to `error` but is XML-only.

**CORRECTION (review finding — the earlier "zero prose exists" claim was FALSE):** there ARE two real
hardcoded prose `Text("MAX")` literals that ship today — `LabsScreen.kt:155-156` and
`UltimateWeaponScreen.kt:227-228` — both **multi-line** (`Text(\n  "MAX",\n  …)`). A resource already exists
for them: `R.string.upgrade_max` = `"MAX"` (`strings.xml:54`). They are un-flagged by a single-line scan only
because they span two lines. So "green at HEAD" for #382's guard is a property of the *single-line scan
scope*, NOT proof the tree is prose-free. This is handled explicitly in #382 below (documented boundary +
KDoc note; NOT silently ignored).

**Existing arch-test idiom** — `architecture/PresentationPurityTest.kt` (JUnit **Jupiter**, no third-party
dep) walks a source tree with `File(...).walkTopDown().filter { extension == "kt" }` and asserts on
matched lines. #382's new guard mirrors this exactly.

**CI note** — CI invokes `./gradlew` directly (not `run-gradle.sh`). PR gate job = `build-and-test`;
Kover step is `:app:koverXmlReport :app:koverHtmlReport`.

---

## Finding #373 — scoped `koverVerify` ratchet on the fragile concurrency/economy zones

### Mechanism (rewritten after empirical DSL discovery — see Ground Truth above)
The per-rule-`filters` design in the first draft **does not compile in Kover 0.9.8**. The proven-working
mechanism is a **filtered `reports.variant("debug")` set** whose `verify` block gates the auto-generated
**`koverVerifyDebug`** task, scoped to the four fragile packages, leaving the aggregate `total`/`koverXmlReport`
UNFILTERED so #218's whole-app informational report is untouched.

### The shipping design — TWO rules in the one filtered `debug` set (proven this session)
A single `groupBy = PACKAGE` rule applies the SAME `minValue` to every in-scope package, so with the packages
spanning 56.94→100% one PACKAGE floor must sit below the lowest (~54) — too loose for the 92–100% zones. But a
report set may hold **multiple rules**, and probe #5 confirmed two rules in one set are BOTH evaluated. So the
design combines two complementary floors over the same filtered scope:

- **Rule A — `groupBy = APPLICATION`, floor 85.** A single blended % over all four packages. Probe #5 read the
  live blend at **87.18%** (matches the computed union 1747/2004). Floor 85 (= 87.18 − ~2 churn margin, rounded)
  catches AGGREGATE erosion — e.g. `domain.usecase` sliding 98→70 drops the blend below 85 and reds the build,
  even though no single package cratered to 54.
- **Rule B — `groupBy = PACKAGE`, floor 54.** Catches any SINGLE package collapsing below 54 (e.g.
  `data.repository` 57→40) that the blend might mask. Probe #5 confirmed floor 54 passes today (all four ≥54).

Together: the blend (A) resists slow multi-package erosion; the per-package floor (B) resists a single-package
collapse. Both live in the ONE `variant("debug")` filtered set → gated by `koverVerifyDebug`; `total`/#218
untouched. This is strictly stronger than a lone PACKAGE-54 rule and needs no fragile two-variant split.

> **Deviation from the issue text — flag for sign-off (review finding `373-floor-contradicts-issue-text`):**
> #373 says "floor = current measured %". This plan uses **measured − ~2** (a churn margin) so trivial churn
> doesn't red the build. Deliberate ratchet-vs-zero-tolerance call. Default if unspecified: ship as planned;
> the floor is a ratchet to be raised as coverage climbs.

### Ratchet-floor values (from probe-measured numbers)
- **Rule A blended floor = 85** (measured blend 87.18%, −~2).
- **Rule B per-package floor = 54** (governed by the lowest package `data.repository` 56.94%, −~2).
- Per-package measured for reference: `data.repository` 56.94, `domain.usecase` 98.73,
  `presentation.battle.engine` 92.15, `domain.battle.engine` 100, `domain.battle.entity` 96.10.

> Ratchet, not target: raise the floors in a follow-up as coverage climbs (noted in the DSL comment).

### Files
- `app/build.gradle.kts` — additive `kover { reports { variant("debug") { filters { includes { classes(<4
  fully-qualified globs>) } } verify { rule("…blended") { groupBy = APPLICATION; bound { minValue = 85; … } }
  rule("…per-package") { groupBy = PACKAGE; bound { minValue = 54; … } } } } } }`. No `kover {}` block exists
  today. Enums resolve fully-qualified (`kotlinx.kover.gradle.plugin.dsl.GroupingEntityType.APPLICATION` /
  `.PACKAGE`, `CoverageUnit.LINE`, `AggregationType.COVERED_PERCENTAGE`) — proven to compile this session.
- `.github/workflows/ci.yml` — add **`:app:koverVerifyDebug` as a SEPARATE step immediately AFTER** the
  existing report step (NOT folded into one invocation). Rationale (review finding
  `373-ci-report-before-verify-not-guaranteed`): a merged `koverXmlReport … koverVerifyDebug` command does not
  guarantee the report is written before a verify failure aborts the build. A separate, later step guarantees
  the `if: always()` artifact upload captures the report regardless of verify outcome. Inside the
  `needs.changes.outputs.code == 'true'` gate. (Note: CI must call `koverVerifyDebug`, the scoped task — NOT
  the aggregate `koverVerify`, which is unfiltered/whole-app and would fail on the 0% packages.)

### Package-scope precision (verify empirically — proven approach this session)
- `data.repository.*` must NOT pull in `domain.repository`; `presentation.battle.engine.*` must NOT pull in
  `presentation.battle.effects`/`entities`/`ui`. Fully-qualified prefixes are exact (probed: the floor-99
  failure listed only the intended packages).
- `domain.battle.*` spans `.engine` + `.entity` subpackages (probed: both appeared under the filter).
- **Verification step (already validated this session, repeat at impl):** transiently set the floor to 99 →
  confirm `koverVerifyDebug` fails listing ONLY the intended packages → revert to the real floor.

### Risks / mitigations
- **Option 2 release-variant report may be empty** (unit tests are debug-only) → Option 2 infeasible → fall
  back to Option 1. **Resolve by prototype before committing Option 2.**
- **Floor too tight** → flaky red on churn. Mitigated by −2 margin; widen a floor in a follow-up if churny.
- **DSL enum resolution** — proven to compile this session with fully-qualified names; a wrong path is a
  configuration-time failure caught by any Gradle run.

---

## Finding #375 — LeakCanary (`debugImplementation`)

### Change
- `gradle/libs.versions.toml` — add `leakcanary = "2.14"` under `[versions]` and
  `leakcanary = { group = "com.squareup.leakcanary", name = "leakcanary-android", version.ref = "leakcanary" }`
  under `[libraries]`. Place near other testing/tooling entries; add a one-line comment
  (`# #375: debug-only leak detection for the loop-thread/FGS/SurfaceView retention topology; never shipped`).
- `app/build.gradle.kts` — add `debugImplementation(libs.leakcanary)` in the `dependencies { }` block, near
  the other `debugImplementation` entries (`compose.ui.tooling`, `compose.ui.test.manifest`).

### Why 2.14 (stable) not 3.0-alpha
The repo's tooling convention (see the benchmark-plugin ADR-0025 and detekt ADR-0037 notes) is to avoid alpha
lines unless a stable line is incompatible. LeakCanary 2.14 stable supports the app's minSdk 34 / AGP 9 /
Kotlin 2.3 fine (it's a debug-only instrumentation lib, not a compiler plugin). No reason to take 3.0-alpha.

### Risks / mitigations
- **Debug APK size / build-time bump** — expected and acceptable (debug only; never in release). Acceptance
  build (`assembleDebug`) confirms it resolves and installs.
- **LeakCanary auto-init via its own ContentProvider** — no code wiring needed; it self-installs in debug.
  Confirm no manifest-merger conflict on `assembleDebug`.
- **Dependabot** — will now track leakcanary bumps; that's fine (matches the repo's managed-pins posture).

---

## Finding #381 — full-chain migration schema-shape test (v7 → v12)

### The value this adds (distinct from existing tests)
Existing `Migration11To12Test` / `DataTransformMigrationsTest` assert **data transforms** via direct
`migrate()`. `MigrationChainTest` (#237) asserts **version contiguity** (pure, no DB). **Neither validates
that the recreate-table `CREATE` statements produce a schema whose *shape* matches the committed
`app/schemas/*.json`.** A drifted `CREATE` (wrong column order/type/nullability/index) crashes a real user's
upgrade but passes every current test. #381 closes exactly that gap by running the whole chain and validating
the terminal schema against the committed v12 JSON.

### PRIMARY RISK — feasibility of `MigrationTestHelper` on the JVM/Robolectric lane (resolve FIRST)
The issue permits "androidTest (MigrationTestHelper) **or JVM equivalent**" and its verify command
(`testDebugUnitTest --tests '*Migration*'`) targets the **JVM lane**. But:
1. `MigrationTestHelper` needs the schema JSONs discoverable at test runtime. On the instrumented lane it
   reads them from `androidTest` **assets** (conventionally wired via
   `sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")`). On the JVM/Robolectric lane, Room
   2.8.x offers a `MigrationTestHelper(instrumentation, databaseClass, ...)` and a
   file-directory-based constructor; whether the Robolectric-backed form finds `app/schemas` without an
   assets wiring is the open question.
2. The existing module deliberately avoided `MigrationTestHelper` — there may be friction.

**Implementation MUST resolve this empirically before writing the full test**, choosing the FIRST of these
that works, in order of preference (JVM-lane first, to match the issue's verify command and keep it off the
emulator):

- **Option A (preferred) — JVM/Robolectric `MigrationTestHelper`.** Wire schemas as a test asset source
  (`android { sourceSets { getByName("test").assets.srcDir("schemas") }` or the androidTest equivalent if the
  helper resolves from there under Robolectric). Use `helper.createDatabase(TEST_DB, 7)` → seed minimal v7
  rows → `helper.runMigrationsAndValidate(TEST_DB, 12, true, *AppMigrations.ALL)`. `runMigrationsAndValidate`
  IS the schema-shape assertion (throws if the resulting schema ≠ committed 12.json). Runs under
  `@RunWith(RobolectricTestRunner)` + `@Config(sdk=[34])` like the sibling tests.
- **Option B — instrumented lane.** If MigrationTestHelper cannot run under Robolectric, add the test under
  `app/src/androidTest/` (wire `androidTest` assets srcDir to `schemas`), gated by the existing instrumented
  emulator lane (`instrumented.yml`). Update the headline instrumented-test count (9 → 10). Note: this
  changes the issue's stated verify command to `:app:connectedDebugAndroidTest`.
- **Option C (fallback, JVM, no MigrationTestHelper) — manual schema-shape assertion.** Open an in-memory DB
  at v7 (same `SupportSQLiteOpenHelper` idiom the existing tests use), run each `AppMigrations.MIGRATION_*`
  in order, then assert the resulting `PRAGMA table_info` / `PRAGMA index_list` matches `12.json`.
  **CRITICAL scope correction (review finding `381-optionc-narrower-than-issue`):** Option C must validate the
  **ENTIRE terminal v12 schema, not just the 3 recreate-table targets.** The full v7→v12 chain also CREATEs a
  brand-new `billing_receipt` table (in `MIGRATION_8_9`) and ALTERs `daily_step_record` twice
  (`battleStepsEarned` in 7→8, `bossPsEarnedToday` in 9→10). Options A/B (`runMigrationsAndValidate`) validate
  the entire terminal schema automatically; Option C's hand-rolled assertions are the ONE place fidelity to the
  issue ("validate the output schema against app/schemas") can silently erode. So Option C MUST **iterate every
  table+index declared in `12.json`** (parse the committed JSON, assert each entity's `createSql`-derived
  column set/types/nullability + `indices`) — NOT a hand-picked subset. This is more code and is precisely why
  Option C is the LAST resort. This is an explicit acceptance check: Option C validates every v12 table.

**The plan does not pre-commit to A/B/C** — implementation picks the first that works and records the choice
in the test's KDoc + the RUN_LOG. The DELIVERABLE is invariant: one test that fails if a migration `CREATE`
drifts from the committed schema shape across the full v7→v12 chain.

### Files
- New test: `app/src/test/java/com/whitefang/stepsofbabylon/data/local/FullChainMigrationSchemaTest.kt`
  (Option A/C) **or** `app/src/androidTest/java/…/FullChainMigrationSchemaTest.kt` (Option B).
- `app/build.gradle.kts` — possibly a test/androidTest `assets.srcDir("schemas")` wiring (A/B only). **Do not**
  move or re-point `room { schemaDirectory("$projectDir/schemas") }` — additive assets wiring only.

### Risks / mitigations
- **Seeding v7 minimally** — the chain's migrations ALTER/recreate specific tables; the v7 seed must create
  every table a migration touches (mirroring what `DataTransformMigrationsTest.createV9/createV10` already do
  for their slices). Read `7.json` for the exact v7 table set. If MigrationTestHelper.createDatabase(…,7) is
  used, it creates the full v7 schema from `7.json` automatically (no manual seed needed) — another reason A
  is preferred.
- **Not a duplicate** — explicitly distinct from #222 (data transforms) and #237 (contiguity); state that in
  the KDoc.

---

## Finding #382 — refresh stale `lint{}` comment + Compose-prose regression guard

### Part 1 — refresh the comment (mandatory, trivial)
Rewrite the `lint{}` comment at `app/build.gradle.kts:218-226`: the "~110 hardcoded Compose strings" claim is
stale (i18n #34 is complete). New comment states: `HardcodedText` is XML-only; Compose single-line prose is now
guarded by `ComposeHardcodedStringTest` (the new arch test) rather than "held by review". Do NOT copy the
earlier draft's inaccurate enumeration (it mis-listed `BattleRenderer`'s `drawText` as a `Text()` and dropped a
`TierSelector` site) — keep the comment about the *guard*, not a hand-count that will drift.

### Part 2 — the forward guard (`ComposeHardcodedStringTest`)
A new JVM arch test modelled EXACTLY on `PresentationPurityTest` (JUnit Jupiter, no dep, `walkTopDown` over
`src/main/java/.../presentation`). It fails on a **hardcoded prose `Text("literal")`** in `presentation/`.

**Detection rule (must avoid false positives on the 5 legitimate interpolations):**
- Match a Compose `Text("…")` where the string literal contains **≥2 consecutive ASCII letters** AND contains
  **no `$`** (string-template interpolation) AND is **not** fully wrapped by `stringResource(...)`/`getString(...)`.
- **WORD-BOUNDARY ANCHOR (review finding `composehardcoded-substring-matches-drawtext`):** a naive `Text("`
  substring match ALSO matches `canvas.drawText("…")` (and any `*Text(` method). Today the one `drawText`
  literal (`BattleRenderer.kt:89`) has `$` so it's excluded — but a future `canvas.drawText("Game Over")`
  (prose, no `$`) would false-positive as a Compose violation. So anchor the match on a word boundary: regex
  `(^|[^A-Za-z.])Text\s*\("` (or: match `Text(` only when NOT preceded by an identifier char or `.`), so
  `drawText(`/`OutlinedText(`/etc. don't match. Cheap; makes the guard robust.
- The 5 existing Compose `Text("…")` matches (HomeScreen×2, UltimateWeaponBar×1, TierSelector×2) all contain
  `$` → all excluded → **the single-line guard passes clean at HEAD** (verified this session: the
  word-boundary + `$`-exclusion grep returns 0 single-line offenders).
- Scope: single-line `Text("…")` only for v1 (the assessment's explicit ask — "Text(\"…\") prose literals").
  Do NOT try to cover every composable string arg (label/contentDescription/placeholder) — wider net, more
  false-positive surface; documented boundary. `contentDescription` is covered by review + the i18n contract.
- **KNOWN OFF-SCOPE OFFENDERS (must be named in the KDoc — review findings `382-max-prose-blind-spot` /
  `375-382-fully-covered`):** two REAL hardcoded prose `Text("MAX")` literals ship today —
  `LabsScreen.kt:155-156` and `UltimateWeaponScreen.kt:227-228` — both **multi-line** (`Text(\n  "MAX",\n …)`),
  so the single-line scan does NOT catch them. `R.string.upgrade_max` = `"MAX"` already exists. **Decision:
  keep the guard single-line-scoped for v1 (do NOT expand to multi-line) AND explicitly document these two
  known literals in the test KDoc**, so nobody "fixes" the scan to multi-line later and is surprised by a red
  build, and so "green at HEAD" is never mistaken for "no hardcoded prose exists". (Optionally migrate the two
  `Text("MAX")` → `stringResource(R.string.upgrade_max)` as a *separate, out-of-scope-for-this-guard* cleanup —
  NOT required for PR-1; note as a follow-up. If done, it's a 2-line presentation edit, not a guard change.)
- **Multi-line / `buildAnnotatedString` limitation (KDoc, mirroring PresentationPurityTest):** line-level scan
  — a regression *tripwire* for the common single-line case, not a proof. Complements the i18n contract.
- **Allowlist mechanism:** an (initially empty) `allowlist: Set<String>` (filename:line or normalized literal)
  for a future *intentional* non-translatable literal (proper noun), matching the repo's allowlist idiom
  (PresentationPurityTest, StepCreditAllowlistTest).

### Files
- `app/build.gradle.kts` — comment refresh only (no behavior change to the lint block).
- New test: `app/src/test/java/com/whitefang/stepsofbabylon/architecture/ComposeHardcodedStringTest.kt`.

### Risks / mitigations
- **False positive on a legitimate interpolation/format** — mitigated by the `$`-exclusion + the
  ≥2-letter rule; verified 0 offenders at HEAD before committing (the test must go green on the current tree
  with an empty allowlist).
- **Over-broad regex catching `Text(` inside comments/strings** — scan trimmed source lines; accept the same
  textual-scan limitation the sibling tests already accept and document it.

---

## Cross-cutting: docs to sync (PR Task-List Convention) — BEFORE the STATE/RUN_LOG step

Touch only what this PR actually invalidates:
- **`CLAUDE.md`** — Testing section: add the scoped `koverVerifyDebug` gate + the two new guard tests
  (`ComposeHardcodedStringTest`, `FullChainMigrationSchemaTest`) to the "Notable guards" list; bump the
  **headline test count** (1302 JVM → 1302 + #381 + #382 = **1304**, *unless* #381 lands on the instrumented
  lane via Option B, in which case JVM = 1303 and instrumented 9 → 10 — set the exact numbers from the green
  run, don't guess). Tech-stack line: note LeakCanary (debug-only) + Kover now gating (scoped `koverVerifyDebug`).
- **`CHANGELOG.md`** — new `[Unreleased]` entries for #373/#375/#381/#382; update the test-count block.
- **`docs/steering/tech.md`** — **UPDATE (not conditional add — review finding `docs-sync-tech-md-update-not-add`):**
  Kover is ALREADY listed there as "informational / non-gating"; this PR makes it partially gating, so that
  line MUST be updated to reflect the scoped `koverVerifyDebug` ratchet. ADD a LeakCanary row (debug-only). The
  "only if listed" caveat applies only to genuinely-absent entries.
- **`docs/steering/source-files.md`** — add entries for the two new test files.
- **`docs/agent/BACKLOG.md`** — regenerated by `/checkpoint` (do not hand-edit).
- **Do NOT touch** `docs/database-schema.md` (no schema change), `structure.md` (no new module), historical
  artifacts.

Then: update `docs/agent/STATE.md` + append `docs/agent/RUN_LOG.md`.

---

## Task list (implementation order — after the plan passes review)

1. **#382 Part 1** — refresh the `lint{}` comment (trivial, no risk).
2. **#375** — add LeakCanary to the catalog + `debugImplementation`; `assembleDebug` to confirm resolve.
3. **#382 Part 2** — write `ComposeHardcodedStringTest`; run it (must be green with empty allowlist at HEAD).
4. **#381** — resolve MigrationTestHelper feasibility (A→B→C), write `FullChainMigrationSchemaTest`, run it
   green; sanity-check it FAILS on a deliberately drifted `CREATE` (temporary local mutation, then revert).
5. **#373** — add the filtered `variant("debug")` set with the TWO rules (APPLICATION floor 85 + PACKAGE
   floor 54); run `:app:koverVerifyDebug` green; re-run the proven mis-scope check (transiently set a floor to
   99 → confirm the failure names ONLY the four intended packages → revert). Confirm `:app:koverXmlReport`
   still emits the full whole-app report (49 packages — #218 intact). Wire `:app:koverVerifyDebug` into
   `ci.yml` as a SEPARATE step after the report step.
6. **Full green gate locally:** `./run-gradle.sh testDebugUnitTest :app:koverVerifyDebug :app:detekt
   assembleDebug` + `./lint-kotlin.sh` (+ `:app:connectedDebugAndroidTest` only if #381 chose Option B).
7. **Sync current-state docs** (above) — set exact test counts from the green run.
8. **Update `docs/agent/STATE.md` + append `docs/agent/RUN_LOG.md`.**
9. Commit; push; open PR. (Sequential-merge rule: this is PR-1; PR-2/#384 rebases onto updated `main` after.)

## Acceptance criteria
- `:app:koverVerifyDebug` passes at HEAD and would FAIL if the fragile-zone blend drops below 85 OR any single
  scoped package drops below 54 (demonstrated by the transient over-set check — already validated this session).
- The rules are scoped precisely to the four packages (failure message names only them — proven this session).
- `:app:koverXmlReport` (`total`) still reports the whole app (49 packages) — #218 NOT regressed.
- `ComposeHardcodedStringTest` green with an empty allowlist; would fail on a planted single-line
  `Text("Hello world")`; the word-boundary anchor means `canvas.drawText("Hello")` does NOT trip it; the two
  known multi-line `Text("MAX")` literals are documented in the KDoc as off-scope.
- `FullChainMigrationSchemaTest` green; would fail on a drifted `CREATE` anywhere in the terminal v12 schema
  (demonstrated); Option C (if used) validates every v12 table, not a subset.
- `assembleDebug` resolves LeakCanary; release variant unaffected (`assembleRelease` in CI still clean).
- Full local gate green (unit + detekt + ktlint + assembleDebug). Docs synced; test count exact.
- No production runtime code touched; no schema change; no versionCode bump.
