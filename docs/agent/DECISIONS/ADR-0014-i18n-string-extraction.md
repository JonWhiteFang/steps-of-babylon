# ADR-0014 — i18n string-extraction strategy

- **Status:** Accepted
- **Date:** 2026-06-04
- **Plan:** V1X-13 (i18n phase 1)

## Context

`strings.xml` historically held only `app_name`; every UI string was a Kotlin literal — a hard blocker for non-English markets. V1X-13 extracts strings to resources in phases. Compose screens are trivial (`stringResource`), and notification managers already hold a `Context` (slice 1). The hard case is **engine-internal floating-text** ("+12 HP", "RAPID FIRE!", "+45", "+3 Step", "+5 PS") emitted by `GameEngine` (a plain, manually-constructed class) and `BattleViewModel`. `GameEngine` deliberately has no `Context` so `GameEngineTest` can run as pure JVM (no Robolectric); giving it a `Context` to call `getString` would force Robolectric onto a large, fast test.

## Decision

1. **Phased extraction:** phase 1 = notifications + battle/workshop screens + engine-internal floating-text; phases 2/3 (remaining screens, then the first non-English locale) deferred to later versions.
2. **`domain/Strings` seam for engine-internal strings.** A pure-Kotlin interface in `domain/` (no Android imports — passes `DomainPurityTest`) with one method per engine string. Production impl `data/AndroidStrings` wraps `Context.getString`. This lets the engine emit localized text without reading resources directly, and lets pure-JVM tests inject a fake (or rely on the literal fallback) instead of pulling in Robolectric. `domain/` is the chosen home (over `presentation/`) so future pure-domain code — e.g. the V1X-09 `Simulation` — can reuse the seam.
3. **Nullable `var strings: Strings?` with literal fallback**, mirroring the existing `GameEngine.soundManager` / `effectEngine` nullable-collaborator pattern. `GameSurfaceView` constructs `AndroidStrings(context)` directly (exactly as it builds `SoundManager`) and sets `engine.strings`; `BattleViewModel` reads `engine.strings` off the engine reference it already holds. The fallback literal is byte-identical to the pre-extraction English string, so existing `GameEngineTest` / `BattleViewModelTest` assertions stay green **with zero churn** and no fake is required. No Hilt binding is introduced (both call sites construct or read the impl directly).
4. **`HardcodedText` lint-as-error guard** is added in the **final** phase-1 PR, only once every phase-1 surface is migrated — adding it earlier would fail the build on not-yet-migrated screens.

## Consequences

- Engine/VM display strings are now localizable; a translated locale's `AndroidStrings` returns translated text with no engine change.
- The fallback literal is mild duplication, but it is dead in production (`strings` is always wired) and is the cheapest way to keep the engine test-suite Robolectric-free. If the duplication ever drifts, the production path (resources) wins; tests assert the fallback.
- Future pure-domain string needs have a ready seam in `domain/Strings`.
- Translations are deferred: phase 1 ships English-only resources; pseudo-locale testing and the first real locale come in later V1X work.

## Phase 2 — Compose-screen string extraction (#34, shipped 2026-06-24)

Phase 2 extracted the remaining hardcoded English UI strings from the standard Compose screens into
`res/values/strings.xml` via `stringResource`. **English-only, no behavior change, 1282 JVM unchanged.**
Shipped as two stacked PRs (#354 heavy screens: store/settings/cards/onboarding; #355 the rest +
shared `presentation/ui` + two `battle/ui` components + the nav titles + the non-composable helpers).

Implementation notes that became reusable patterns:

- **`Screen.secondaryTitle(route)` returns `@StringRes Int?`** (not `String?`), resolved with
  `stringResource` at the MainActivity call site. A resource id is a plain `Int`, so `Screen` stays
  Android-light and its `by lazy` route-list fragile zone (#161) is untouched; `ScreenSecondaryTitleTest`
  resolves via `ctx.getString`. Same `@StringRes`-and-resolve-at-call-site pattern applied to the
  non-composable helpers `CurrencyType.label()` and `pathLabel()`.
- **Non-composable helpers that interpolate** (`formatTime`/`formatTimeAgo`) keep returning `String`;
  only their pure-literal early-return (`Done!`/`Just now`) is converted, passed in as a call-site-resolved
  `String` param. The duration-unit branches (h/m/s, m/h/d-ago) are deferred to a plurals pass.
- **`UltimateWeaponBar` a11y `contentDescription`** is resolved into per-slot `val`s **inside the
  `forEach`** (before the `Box`); `stringResource` cannot be called inside the non-composable `semantics{}`
  lambda. Both strings resolve unconditionally per iteration; only the final selection is conditional.
- **Completeness method correction:** the plan's per-task `Text("…")` grep undercounts — it can't see a
  literal that starts with `${` (the `"${…} word"` shape). A final interpolation-aware sweep caught and
  extracted several (`"<count> steps"`, `"Tier …"`, `" x1"`, chart legends, `"PS"`). Use the broader pattern
  for any future extraction pass.

**`HardcodedText` correction:** the phase-1 plan said the lint guard would be "added in the final phase-1
PR." In practice the guard is already enabled and is **XML-only** — it does not flag Kotlin `Text("…")`
literals, which is why hardcoded Compose literals persisted into phase 2 and had to be found by grep.

**Deferred to a follow-up (PR3):** ViewModel `_userMessage` strings (Labs/Missions/economy — not
`@Composable`, need a resource-ID-emission pattern rather than `stringResource`), `Screen.kt` bottom-nav
`label`s (fragile zone), the `SCREEN_LOAD_ERROR` const, the duration-unit suffixes (need plurals), and the
`pathValueAtNext`/Canvas/Activity literals. The first real non-English locale remains deferred to later
V1X work.
