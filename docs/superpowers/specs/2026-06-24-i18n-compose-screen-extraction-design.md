# Design — #34 i18n: Compose-screen string extraction (phase 2)

- **Date:** 2026-06-24
- **Issue:** #34 ([i18n] All UI strings are hardcoded; strings.xml has only one entry)
- **ADR:** extends ADR-0014 (i18n string-extraction strategy) — phase 2 of the phased plan
- **Status:** Reviewed — passed the Adversarial Review Gate 2026-06-24 (see "Review history")

## Context

`strings.xml` is no longer "one entry" — ADR-0014's **phase 1** already extracted 130+ strings
(notifications, battle HUD/floating-text, workshop, enum display-names) and established the
`domain/Strings` engine seam for engine-internal Canvas text. What remains for #34 is **phase 2**:
the standard Compose screens still hold hardcoded literals.

Measured at HEAD (`a5b49fd`), roughly (the `~` counts are deliberately approximate — the
implementer greps each file and migrates the actual hardcoded **English** literals, NOT data-driven
templates like `Text("${slot.cooldownRemaining.toInt()}")` which need no extraction):

- **~85–90 hardcoded `Text("…")`** literals + **~7 `contentDescription = "…"`** literals
- Across the standard Compose screens + a few battle/ui Compose components + the navigation layer
- **17 files already use `stringResource`** (phase-1 surfaces) — these are the reference patterns

## Goal

Extract the hardcoded **Compose** UI strings into `res/values/strings.xml` and reference them via
`stringResource(R.string.…)` (and `pluralStringResource` / format-args where appropriate), following
the conventions already shipped in phase 1. No behavior change; English-only resources (translation
is a later ADR-0014 phase).

## Scope

### In scope

The hardcoded Compose `Text("…")` content + `contentDescription = "…"` literals on the standard
screens **and all their Compose sub-surfaces** — explicitly including:

- **Standard screens:** store, settings, cards, onboarding, home, economy
  (`CurrencyDashboardScreen`), stats, labs, missions, weapons, supplies.
- **Dialog surfaces on those screens** — `AlertDialog` title / text / `confirmButton` / `dismissButton`
  `Text(...)` labels are in scope (e.g. `SettingsScreen`'s delete-confirmation dialogs ~lines 158–186;
  `CardsScreen`'s pack-result dialog). These are standard Compose `Text` and are NOT a separate surface.
- **`presentation/ui/` shared components** that carry literals: `ErrorState.kt`, `SobTopAppBar.kt`,
  and any of `Rarity.kt` / `CurrencyDisplay.kt` that hold a hardcoded English literal (grep to confirm
  per file — most are already migrated or data-driven). This is several files, not "the lone leftover".
- **Battle/ui Compose components** — `UltimateWeaponBar.kt` (a pure-Compose `@Composable`, no Canvas)
  has a hardcoded `contentDescription` (~lines 50–55) that IS in scope; extract it as a format-arg
  resource. `InRoundUpgradeMenu.kt` has a hardcoded `Text("✕")` close-affordance (already partly
  migrated elsewhere) — migrate it too. Their data-driven `Text("${…}")` calls need no extraction.
- **Navigation layer — `presentation/navigation/Screen.kt`:** the 8 user-visible secondary-screen
  titles surfaced by `Screen.secondaryTitle()` ("Weapons", "Cards", "Supplies"→"Unclaimed Supplies",
  "Premium Currencies", "Missions", "Settings", "Store", "Help") feed the `SobTopAppBar` `Text`. They
  are hardcoded UI strings and must be extracted. **Constraint:** `secondaryTitle` is a pure helper
  pinned by `ScreenSecondaryTitleTest`, and `Screen`'s route lists are `by lazy` init-order-sensitive
  (#161 fragile zone). Resolve the strings at the **call site** (`MainActivity` is `@Composable` and
  can call `stringResource`), or pass a `@StringRes Int` — do NOT call `stringResource`/`Context`
  inside `Screen` (it must stay Android-light and the lazy lists untouched). Update
  `ScreenSecondaryTitleTest` accordingly.
- The `contentDescription` literals across the above (accessibility-string correctness).

### Out of scope (deferred to a follow-up PR — PR3)

- **Battle-renderer Canvas text** — `BattleRenderer.kt` (`presentation/battle/engine/`) and
  `WaveAnnouncement.kt` (`presentation/battle/effects/`) draw hardcoded literals via
  `Canvas.drawText` (e.g. `"$pct%"`, `"⚠ BOSS INCOMING"`, `"Next Wave: …s"`), NOT Compose `Text`.
  Some of their text already arrives via the `domain/Strings` seam (`bossCountdownLabel`,
  `nextWaveComposition`); the remaining direct-`drawText` literals would need new `domain/Strings`
  methods + Effect-constructor wiring — an engine-seam refactor in the fragile battle zone, beyond
  this phase. Deferred. **(Note: `UltimateWeaponBar.kt` is NOT here — it is pure Compose, see In scope.)**
- **`HealthConnectPermissionActivity.kt`** — a **Compose** Activity (`setContent { }` +
  `material3.Text`, NOT `Context.getString`) with ~2 hardcoded literals ("Privacy Policy" + the
  policy body). It is technically migratable now exactly like `MainActivity`, but is deferred to PR3
  for scope isolation (a standalone privacy Activity, not part of the screen set). The deferral is a
  scoping choice, not a framework constraint.
- **Actual translations** — phase 2 ships English-only resources, exactly as phase 1 did.

## Conventions (established by phase 1 — no new decisions)

- **Static string** → `stringResource(R.string.x)`.
- **String with a value** → format-arg form `stringResource(R.string.x, arg)` with positional
  `%1$s` / `%1$d` in the resource.
- **Count-dependent text** → `plurals.xml` + `pluralStringResource` (only where a count drives
  singular/plural — most screen labels are static and do NOT need plurals).
- **Format-arg vs Kotlin interpolation — the decision rule:** extract to a resource (static string or
  format-arg template) any **human-readable English copy**, including templates where the literal
  text is fixed and only data is interpolated (e.g. `"Balance: %1$d Steps"`, `"Wave %1$d · %2$s"`).
  Do **not** create a resource for a `Text` whose entire content is a bare data read with no English
  words (e.g. `Text("${slot.cooldownRemaining.toInt()}")`, `Text("$%1$d")`-style pure-number/glyph
  readouts) — those carry no translatable text. When in doubt: if a translator would need to touch
  it, it's a resource.
- **Naming:** `screen_element` snake_case, mirroring existing keys
  (e.g. `settings_sound_header`, `settings_delete_confirm_title`, `store_…`, `cards_…`).
- **Escaping:** apostrophes as `\'`; a string with a meaningful leading/trailing space wrapped in
  `"…"` (as existing `inround_free` / `reward_join` do).
- **`contentDescription`** literals also become resources (a11y override correctness).

## Lint guard (already enabled — NOT a deferred deliverable)

**Correction from review:** the `HardcodedText` lint-as-error guard is **already enabled** —
`app/build.gradle.kts:225` has `error += "HardcodedText"` (added in phase 1). There is no guard to
"flip later." Critically, the build file's own comment documents the load-bearing limitation:
**`HardcodedText` is an XML-only check — it does NOT flag Compose `Text("literal")` or
`Canvas.drawText`.** So:

- The guard does **not** catch any of the literals this phase migrates (they're all Kotlin/Compose),
  and it would **not** red-build on the deferred Canvas/Activity literals either (same reason). The
  earlier "defer the global flip" framing was wrong on both counts and is removed.
- **Phase-2 Compose surfaces are therefore protected only by code review + the existing UI-test
  harness**, exactly as phase 1 was. There is no build-time guard against a *new* hardcoded Compose
  `Text` slipping in — a known, accepted gap until a dedicated Compose lint rule lands (ADR-0014).
- **End-of-phase-2 state:** all in-scope Compose surfaces above are migrated. What remains hardcoded
  and deferred to **PR3** is exactly: the `Canvas.drawText` literals (`BattleRenderer`,
  `WaveAnnouncement`) and `HealthConnectPermissionActivity`. **#34 stays open until PR3** closes those.

## PR structure (multi-PR by cluster)

Each PR is its own branch + merge commit (repo policy: merge-commits only), independently green.
Literal counts are approximate — the implementer migrates the actual hardcoded literals found per file.

- **PR1 — heavy screens:** store, settings (incl. its `AlertDialog` strings), cards (incl. its
  pack-result dialog), onboarding.
- **PR2 — remaining screens + shared/nav surfaces:** home, economy (`CurrencyDashboardScreen`),
  stats, labs, missions, weapons, supplies; the `presentation/ui/` shared components
  (`ErrorState`, `SobTopAppBar`, + any literal-bearing `Rarity`/`CurrencyDisplay`); the battle/ui
  Compose components (`UltimateWeaponBar` contentDescription, `InRoundUpgradeMenu` "✕"); and
  `navigation/Screen.kt`'s 8 `secondaryTitle` titles (resolved at the `MainActivity` call site +
  `ScreenSecondaryTitleTest` updated).
- **PR3 (later follow-up, NOT this effort):** the deferred `Canvas.drawText` literals
  (`BattleRenderer`, `WaveAnnouncement`) via new `domain/Strings` methods + the
  `HealthConnectPermissionActivity` Compose literals → **closes #34**. No lint-guard change (already
  enabled; XML-only).

## Testing

- **No behavior change**, so no new logic tests. The risk is purely "wrong/missing string" or "wrong
  format arg", which the build + a render check catch.
- Each migrated screen that **already has a Robolectric Compose UI test** (e.g. `CardsScreenTest`,
  `OnboardingScreenTest`) must stay green. Where such a test asserts visible text, **prefer** updating
  the assertion to read the resource explicitly —
  `context.getString(R.string.x, args)` / `getResources().getQuantityString(...)` — and assert that
  value, so the test is pinned to the *resource*, not a stale duplicate literal. (Proven working under
  the Robolectric NATIVE lane — see `ClaimRewardFormatTest`, which calls `stringResource` inside a
  `setContent` block, and `PluralsResourceTest`.) Asserting the bare rendered literal is acceptable
  only with a `// stringResource(R.string.x)` comment naming the source — note that a bare-literal
  assertion stays green even if the screen→resource wiring drifts, so it does NOT verify the resource.
- Screens **without** a Compose UI test — notably **`SettingsScreen`, `CurrencyDashboardScreen`,
  `StatsScreen`** (and `LabsScreen`/`MissionsScreen`, whose VMs' `while(true)` tickers make cheap VM
  construction awkward) — rely on `:app:testDebugUnitTest` + `assembleDebug` + a careful visual diff
  of the literal→resource mapping in review (every extracted string's English value must be
  byte-identical to what it replaced). Flag these as heightened-rigor in the PR description.
- Full JVM suite (`./run-gradle.sh :app:testDebugUnitTest`) green at each PR; `detekt` + `ktlint`
  clean (CI-gated).

## Risks & mitigations

- **Copy drift** (a resource value that differs from the original literal) → review every
  literal→resource pair; the value must be byte-identical (incl. punctuation, emoji, spacing).
- **Format-arg mistakes** (`%1$d` vs `%1$s`, wrong order) → match the Kotlin call-site arg types;
  covered by the screen rendering without a `Resources$NotFoundException`/format crash under the
  UI-test lane where one exists.
- **Key collisions / sprawl** → namespace every key by screen; reuse existing shared keys
  (e.g. `action_resume`, `action_continue`) rather than minting duplicates.
- **Accidentally migrating a non-UI literal** (log strings, test tags, content keys) → only `Text`
  composable content + `contentDescription` are in scope; leave log/analytics/test-tag strings alone.

## Open questions

None — architecture is fixed by ADR-0014; scope, cluster split, and the lint-guard reality (already
enabled, XML-only) are settled.

## Review history

Passed the Adversarial Review Gate (ultracode, multi-agent) on 2026-06-24: 30 findings raised, 25
surviving after refutation (collapsing to ~10 distinct issues — the same items were independently
found across dimensions). Surviving findings applied above. The two **critical** findings — that
`UltimateWeaponBar.kt` is pure Compose (not deferred Canvas text) and that the `HardcodedText`
lint-guard rationale was inverted (it's already enabled and XML-only) — drove the largest amendments.
Refuted (5): a "counts match exactly" claim (counts are approximate, now stated as such); an
over-extraction worry already covered by the "leave log/test-tag strings alone" risk note; two
finder claims with miscounted evidence; and a duplicate of the Canvas/Activity mischaracterization.
