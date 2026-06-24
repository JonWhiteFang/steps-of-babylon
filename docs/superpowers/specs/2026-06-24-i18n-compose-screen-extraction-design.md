# Design — #34 i18n: Compose-screen string extraction (phase 2)

- **Date:** 2026-06-24
- **Issue:** #34 ([i18n] All UI strings are hardcoded; strings.xml has only one entry)
- **ADR:** extends ADR-0014 (i18n string-extraction strategy) — phase 2 of the phased plan
- **Status:** Proposed (pending Adversarial Review Gate)

## Context

`strings.xml` is no longer "one entry" — ADR-0014's **phase 1** already extracted 130+ strings
(notifications, battle HUD/floating-text, workshop, enum display-names) and established the
`domain/Strings` engine seam for engine-internal Canvas text. What remains for #34 is **phase 2**:
the standard Compose screens still hold hardcoded literals.

Measured at HEAD (`a5b49fd`):

- **~93 hardcoded `Text("…")`** literals + **~6 `contentDescription = "…"`** literals
- Across **~14 files**, but the bulk is plain Compose `Text` on standard screens
- **17 files already use `stringResource`** (phase-1 surfaces) — these are the reference patterns

## Goal

Extract the hardcoded **Compose** UI strings into `res/values/strings.xml` and reference them via
`stringResource(R.string.…)` (and `pluralStringResource` / format-args where appropriate), following
the conventions already shipped in phase 1. No behavior change; English-only resources (translation
is a later ADR-0014 phase).

## Scope

### In scope

The plain Compose `Text("…")` + `contentDescription = "…"` literals on the standard screens:

- **store** (~16), **settings** (~12), **cards** (~11), **onboarding** (~9), **home** (~8),
  **economy** (~8), **stats** (~6), **labs** (~6), **missions** (~5), **weapons** (~2),
  **supplies** (~2), and the lone `presentation/ui/` leftover.
- The ~6 `contentDescription` literals on these screens (accessibility-string correctness).

### Out of scope (deferred to a follow-up PR — see "Lint guard" below)

- **Battle-renderer Canvas text** — `BattleRenderer.kt`, `WaveAnnouncement.kt`, `UltimateWeaponBar.kt`
  draw via `Canvas.drawText`, not Compose. These route through the `domain/Strings` engine seam
  (ADR-0014), not `stringResource`, and touch the fragile battle-engine zone. Deferred.
- **`HealthConnectPermissionActivity.kt`** — uses `Context.getString`, not Compose. Deferred (simple,
  but grouped with the final follow-up).
- **Global `HardcodedText = error` lint guard** — see below.
- **Actual translations** — phase 2 ships English-only resources, exactly as phase 1 did.

## Conventions (established by phase 1 — no new decisions)

- **Static string** → `stringResource(R.string.x)`.
- **String with a value** → format-arg form `stringResource(R.string.x, arg)` with positional
  `%1$s` / `%1$d` in the resource.
- **Count-dependent text** → `plurals.xml` + `pluralStringResource` (only where a count drives
  singular/plural — most screen labels are static and do NOT need plurals).
- **Naming:** `screen_element` snake_case, mirroring existing keys
  (e.g. `settings_sound_header`, `settings_delete_confirm_title`, `store_…`, `cards_…`).
- **Escaping:** apostrophes as `\'`; a string with a meaningful leading/trailing space wrapped in
  `"…"` (as existing `inround_free` / `reward_join` do).
- **`contentDescription`** literals also become resources (a11y override correctness).

## Lint guard (deferred)

ADR-0014 planned to flip `HardcodedText` to lint-as-error in the **final** phase-1 PR, once every
surface is migrated. Because we are deferring the Canvas/Activity surfaces, a *global*
`HardcodedText = error` would red-build on the remaining Activity literals (and `Canvas.drawText`
isn't even caught by `HardcodedText`). Therefore:

- **Phase 2 does NOT flip the lint guard.** Migrated screens are protected by code review + the
  existing UI-test harness.
- The **global `HardcodedText = error` guard is enabled only in the final follow-up PR** that also
  closes out the deferred Canvas/Activity surfaces. **#34 stays open until that follow-up.**

## PR structure (multi-PR by cluster)

Each PR is its own branch + merge commit (repo policy: merge-commits only), independently green:

- **PR1 — heavy screens:** store, settings, cards, onboarding (~48 literals).
- **PR2 — medium/light screens:** home, economy, stats, labs, missions, weapons, supplies, +
  the `presentation/ui/` leftover (~38 literals).
- **PR3 (later follow-up, not this effort):** deferred Canvas-text + HC Activity surfaces + flip the
  global `HardcodedText` lint guard → closes #34.

## Testing

- **No behavior change**, so no new logic tests. The risk is purely "wrong/missing string" or "wrong
  format arg", which the build + a render check catch.
- Each migrated screen that **already has a Robolectric Compose UI test** (e.g. `CardsScreenTest`,
  `OnboardingScreenTest`) must stay green; where such a test asserts visible text, update the
  assertion to read the resource (or assert the same literal the resource resolves to) so the test
  still pins the user-visible copy.
- Screens **without** a UI test rely on `:app:testDebugUnitTest` + `assembleDebug` + a careful
  visual diff of the literal→resource mapping in review (every extracted string's English value must
  be byte-identical to what it replaced).
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

None — architecture is fixed by ADR-0014; scope, cluster split, and lint-guard timing are decided.
