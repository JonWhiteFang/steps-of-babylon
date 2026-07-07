# Design — First non-English locale: Spanish (`es`) (#34 payoff)

**Date:** 2026-07-07
**Issue:** #34 (i18n) — the final piece: ship the first real `values-xx` locale.
**Status:** approved (brainstorming) → pending Adversarial Review Gate before plan.

## Problem & context

Across #34 phases 1–3 (ADR-0014, 6 PRs #360–#365) the app was made **100% locale-ready**: every
user-facing string was extracted to `app/src/main/res/values/strings.xml` (566 `<string>`) and
`plurals.xml` (16 `<plurals>`), with the `UiMessage` / `EnumLabels` / `domain/Strings` seam and the
`formatted="false"` prose rule all in place. The extraction is *complete* — but **English is still the
only locale that ships** (`values/` is the only `values*/` directory). This effort turns that readiness
into a real, shipped translation.

**Goal:** ship a complete Spanish (`es`) locale — the smallest diff that proves the readiness machinery
and delivers the #34 user payoff.

## Decisions (locked in brainstorming)

| Decision | Choice | Rationale |
|---|---|---|
| **Locale** | Spanish (`es`) | LTR, Latin script, huge reach, no RTL/CJK/font complications — the safe first locale. |
| **Translation source** | AI-generated, **complete** (all ~582 entries) | Solo project, no in-house translator. A complete set ships now and passes CI; privacy/legal flagged for later human review. |
| **Prose / legal** | Translate **everything** (incl. privacy policy, HC-privacy, help bodies, crash template) | Any English gap trips `MissingTranslation` → fails `lintRelease`. A complete set is the only clean, CI-green path. |
| **Language access** | **Device-language only** — no in-app picker, no `locales_config.xml` | Android auto-matches `values-es` on Spanish-set devices. Zero new UI/code — smallest diff. Per-app picker is a separate future effort. |

## Scope

### In scope
1. `app/src/main/res/values-es/strings.xml` — all **566** `<string>` entries translated to Spanish.
2. `app/src/main/res/values-es/plurals.xml` — all **16** `<plurals>` entries translated (Spanish CLDR
   uses `one`/`other`, the same two forms as English — no structural change).
3. A new pure-JVM regression guard `architecture/LocaleCompletenessTest` (see below).
4. Doc sync: STATE.md fragile-zone i18n line, CHANGELOG, `source-files.md`, master-plan #34 status,
   ADR-0014 amendment.
5. A follow-up GitHub issue: **human/native review of `values-es`** (privacy/legal + gameplay-term
   consistency) before promotion beyond internal.

### Out of scope (YAGNI)
- Per-app language picker; `locales_config.xml` (Android 13+ per-app language).
- A second locale; RTL handling.
- Translating the **documented English residuals** (they are not in `strings.xml` — see below).
- Any `versionCode` bump (happens at the next `v*` release cut, like all `[Unreleased]` work).
- On-device visual sign-off is a **manual follow-up**, not a CI gate.

## Translation fidelity rules (load-bearing)

These are exactly the places a locale silently breaks the build or the layout. The plan MUST enforce
each; the completeness test (below) machine-checks the mechanical ones.

1. **Format args preserved verbatim.** Every `%1$d` / `%1$s` / `%2$s` (and any bare `%d` / `%s`)
   present in an English string MUST appear in its Spanish counterpart with the **same arg numbers**.
   Positional args (`%1$s`) let Spanish reorder words safely; bare non-positional `%s`/`%d` keep their
   original left-to-right order.
2. **`formatted="false"` carried over.** All **25** bare-`%` prose strings that declare
   `formatted="false"` in English MUST declare it in Spanish too — omitting it fails `lintRelease` with
   Android lint `StringFormatInvalid` "multiple substitutions." (Not covered by
   `testDebugUnitTest`/detekt/ktlint — only `lintRelease` catches it.)
3. **Plurals map cleanly.** Spanish CLDR plural categories are `one` + `other` — identical to the
   English structure. Keep both items per plural; do not add `zero`/`few`/`many` unless lint demands it
   (then the added form = the `other` text).
4. **XML escaping.** Escape `'` as `\'`, `&` as `&amp;`, `<` as `&lt;`. Accented/inverted characters
   (á é í ó ú ñ ¿ ¡ ü) are literal UTF-8 — never escape those.
5. **Multi-line bodies preserved.** The privacy-policy body, Health Connect privacy text, the 9
   `help_*_body` sections, and the crash-report template keep their `\n` line breaks, bullet glyphs
   (•, →, —), emoji, and paragraph structure. Translate the prose, not the structure.
6. **Emoji/glyph-bearing strings** (e.g. `👟 +%1$d Steps`) keep the glyph in the same position.
7. **Documented English residuals stay English by design** — and are NOT touched because they are not
   in `strings.xml`:
   - `SupplyDropTrigger.message` (authored push content — `domain/model/`),
   - `BillingProduct.priceDisplay` (static USD fallback; live Play price already localizes),
   - seed cosmetic name/description **fallback fields** in `CosmeticRepositoryImpl` (resolved-by-id at
     render; any cosmetic *string resource* that lives in `strings.xml` IS translated).
   These neither translate nor trip `MissingTranslation` (they are Kotlin fields, not resources).

## Completeness guard — `architecture/LocaleCompletenessTest`

A new pure-JVM test in `app/src/test/java/.../architecture/`, matching the repo's `*Test` guard style
(`ComposeHardcodedStringTest`, `DomainPurityTest`). It parses the four XML files directly (no emulator,
no Robolectric) and asserts, for `strings.xml` and `plurals.xml` independently:

- **Identical key sets** between `values/` and `values-es/` — fails listing any key missing from
  Spanish (would be `MissingTranslation`) or extra in Spanish (would be `ExtraTranslation`).
- **Identical format-arg signature per key** — the multiset of format specifiers (`%1$d`, `%1$s`,
  `%2$s`, bare `%d`/`%s`) in the Spanish value equals the English value's. Catches a dropped/renumbered
  arg that would crash at `String.format` / `getQuantityString` time.
- **`formatted="false"` parity** — a string that is `formatted="false"` in English is `formatted="false"`
  in Spanish (and vice-versa).
- **Plurals: identical `quantity` item sets per plural** between the two locales.

This makes the CI `MissingTranslation`/`ExtraTranslation` protection explicit and — crucially — pins
**arg-signature drift** the moment English adds/changes a string without a matching Spanish edit. It is
the "add-a-locale contract" enforced in code, not prose.

Rationale for a dedicated test even though `lintRelease` already runs `MissingTranslation`: lint catches
missing keys but is weaker on per-key arg-signature equality and on `formatted="false"` *parity*; the
test also fails fast on the JVM lane with a precise per-key diff, and future locales inherit the guard
for free.

## Verification

- `./run-gradle.sh :app:lintDebug :app:lintRelease` — **must be green.** This is the real gate:
  `MissingTranslation`, `ExtraTranslation`, `StringFormatInvalid`. (Run explicitly — CI runs it, but
  it's not part of `testDebugUnitTest`/detekt/ktlint.)
- `./run-gradle.sh testDebugUnitTest` — 1314 existing + the new `LocaleCompletenessTest`.
- `./run-gradle.sh :app:detekt` + `./lint-kotlin.sh` — unchanged (no Kotlin production code touched; the
  new test is Kotlin, so it is format/detekt-checked).
- **Manual follow-up (not CI):** on-device smoke on a Spanish-locale emulator to eyeball truncation on
  the longest strings (Spanish runs ~15–25% longer than English) — battle HUD, Store, Settings,
  onboarding. Recorded as a note, not a gate.

## Docs & tracking

- STATE.md: flip the i18n fragile-zone line from "only the first `values-xx` remains" to "Spanish (`es`)
  shipped; adding a locale = mirror both XML files + pass `LocaleCompletenessTest`."
- CHANGELOG `[Unreleased]`: new Spanish locale entry (566 strings + 16 plurals) + the completeness test.
- `docs/steering/source-files.md`: add the two `values-es/` files + the new test.
- `docs/plans/master-plan.md`: #34 status → first locale shipped.
- ADR-0014 amendment: first locale = Spanish, machine-translated, privacy/legal flagged for eventual
  human/legal review; records the "add-a-locale = mirror both XML files + pass the completeness test"
  recipe.
- New issue: human/native Spanish review (privacy/legal + gameplay term consistency) before promotion
  beyond internal.

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| Machine translation quality (tone, gameplay terms, legal precision) | Complete set unblocks CI now; follow-up issue gates a human review before promotion beyond internal. Locale is opt-in by device language — English users are unaffected. |
| Dropped/renumbered format arg → runtime crash | `LocaleCompletenessTest` arg-signature check + `lintRelease` `StringFormatInvalid`. |
| Missing/extra key → `MissingTranslation`/`ExtraTranslation` CI failure | `LocaleCompletenessTest` fails fast on the JVM lane with a precise diff before lint even runs. |
| Missing `formatted="false"` on a bare-`%` Spanish string | `LocaleCompletenessTest` parity check + `lintRelease`. |
| Layout truncation from longer Spanish text | Manual on-device smoke follow-up; the readiness contract already uses flexible Compose layouts. |
| English adds a string later without a Spanish counterpart | `LocaleCompletenessTest` turns that into a red build — the permanent guard. |

## Success criteria

- A Spanish-set device shows the app fully in Spanish (UI, gameplay, notifications, plurals, help,
  privacy) with correct pluralization and no `String.format` crashes.
- English (and every other locale) is byte-for-byte unaffected — `values/` untouched.
- `lintDebug`, `lintRelease`, `testDebugUnitTest` (incl. the new guard), detekt, ktlint all green.
- No production Kotlin, no schema, no dependency, no `versionCode` change.
