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

1. **Format args preserved verbatim.** Every format specifier present in an English value MUST appear
   in its Spanish counterpart with the **same arg numbers** and the **same multiset**. The specifier
   grammar (used both here and by the completeness test) is: **positional** `%N$x` for any index `N`
   (not just 1–2) and any conversion letter `x`, plus **bare non-positional** `%x`. The real corpus
   goes up to index **4** (`crash_report_email_body` = `%1$s`…`%4$s`; `duration_hms` = `%1$d %2$d %3$d`)
   — the rule is index-agnostic, not a 1–2 set. Positional args (`%1$s`) let Spanish reorder words
   safely; bare non-positional `%s`/`%d` keep their original left-to-right order. **`%%` is an escaped
   literal percent, NOT an arg** — it must be preserved as-is and never counted as a specifier.
2. **`formatted="false"` carried over.** All **25** bare-`%` prose strings that declare
   `formatted="false"` in English MUST declare it in Spanish too — omitting it fails `lintRelease` with
   Android lint `StringFormatInvalid` "multiple substitutions." (Not covered by
   `testDebugUnitTest`/detekt/ktlint — only `lintRelease` catches it.)
3. **Bare literal `%` must be safe.** A bare `%` that is neither `%N$x`, `%x`, nor `%%` (e.g.
   `uw_path_dot` = `"DoT % MaxHP/sec"` at `strings.xml:365`, which carries **no** `formatted="false"`)
   must, in Spanish, keep a following space/non-letter so the token can't drift toward a conversion and
   trip `StringFormatInvalid`. The safest handling is to escape it as `%%` in **both** locales — but do
   **not** silently change the English source in this PR beyond what's needed; if left bare, the Spanish
   translation MUST keep the `% ` (percent-space) pattern. The arg-signature test extracts zero
   specifiers from `% ` and so gives no guard here — this is a documented blind spot backstopped by
   `lintRelease`.
4. **Plurals map cleanly.** Spanish CLDR plural categories are `one` + `other` — identical to the
   English structure. Keep both items per plural; do not add `zero`/`few`/`many` unless lint demands it
   (then the added form = the `other` text). **Arg signatures are per-`(name, quantity)` item, and may
   legitimately differ between a plural's own items** — `boss_in_waves` (`plurals.xml:25–28`) has
   `one` = `"Boss next wave"` (**0 args**) and `other` = `"Boss in %1$d waves"` (**1 arg**). Spanish
   must preserve that intra-plural asymmetry per item (Spanish `one` carries no `%1$d`; Spanish `other`
   carries `%1$d`) — never force `%1$d` into the `one` form just because `other` has it.
5. **XML escaping.** Escape `'` as `\'`, `&` as `&amp;`, `<` as `&lt;`. Accented/inverted characters
   (á é í ó ú ñ ¿ ¡ ü) are literal UTF-8 — never escape those. Spanish uses apostrophes far more than
   English (contractions, elision), so this rule bites harder here. **`assembleDebug` (already in the CI
   gate) is the escaping backstop** — a raw `'`/`&` in `values-es` is an aapt2 resource-compile hard
   error caught there, **not** by `LocaleCompletenessTest` (which parses XML leniently and never sees
   aapt's stricter apostrophe rule).
6. **Multi-line bodies preserved.** The Health Connect privacy body (`hc_privacy_policy_body` at
   `strings.xml:439` — the **only** in-app privacy text; the full privacy policy is hosted at a URL,
   `site/index.md`, and is **not** an Android string resource), the 9 `help_*_body` sections, and the
   crash-report template (`crash_report_email_body`) keep their `\n` line breaks, bullet glyphs
   (•, →, —), emoji, and paragraph structure. Translate the prose, not the structure.
7. **Emoji/glyph-bearing strings** (e.g. `👟 +%1$d Steps`) keep the glyph in the same position.
8. **`app_name` stays "Steps of Babylon"** (proper-noun brand — the launcher label). The `values-es`
   value is the **identical** string `Steps of Babylon`; do **not** translate it to e.g. "Escalones de
   Babilonia" (a user-visible branding regression the completeness test can't catch, since the key IS
   present — only the value would differ). Kept as an identical-value entry rather than
   `translatable="false"` so the key-parity assertion still holds and the brand ships in Spanish
   unchanged.
9. **Documented English residuals stay English by design** — NOT touched, because they are not
   translatable `strings.xml` resources:
   - `SupplyDropTrigger.message` (authored push content — `domain/model/`),
   - `BillingProduct.priceDisplay` (static USD fallback; live Play price already localizes),
   - seed cosmetic name/description **fallback fields** in `CosmeticRepositoryImpl` (resolved-by-id at
     render; any cosmetic *string resource* that lives in `strings.xml` IS translated),
   - **`R.raw.oss_notices`** (the Help → "Open-source notices" body, read by `HelpScreen.kt:81–89`) —
     a `res/raw` asset, **not** a string resource. Its Apache-2.0 §4(d) attribution + upstream license
     names are conventionally English and machine-translating them would be wrong; **only the section
     title** (`help_oss_title`) localizes. Consequence: a Spanish device shows a Spanish Help heading
     over an English notices body — an **intended, acceptable** language mix (flag it in the on-device
     smoke + the human-review follow-up so it's a conscious call, not a surprise).
   The first three are Kotlin fields; the fourth is a raw asset. None trip `MissingTranslation`.

## Completeness guard — `architecture/LocaleCompletenessTest`

A new **pure-JVM** test, matching the repo's `architecture/*Test` guard style
(`ComposeHardcodedStringTest`, `DomainPurityTest`).

**Fixed identity (pin these — the single biggest way this guard silently no-ops is a wrong base path
or wrong lane):**
- **Class:** `app/src/test/java/com/whitefang/stepsofbabylon/architecture/LocaleCompletenessTest.kt`,
  package `com.whitefang.stepsofbabylon.architecture`.
- **Framework:** JUnit Jupiter (`org.junit.jupiter.api.Test`) — matches the JVM arch-test lane
  (`useJUnitPlatform()`), NOT JUnit4/instrumented.
- **File reading:** JVM working dir is the **`:app` module root**, so the test roots at module-relative
  paths `File("src/main/res/values/strings.xml")` / `File("src/main/res/values-es/strings.xml")` (and
  the two `plurals.xml`) — the same idiom as `ComposeHardcodedStringTest.kt:41` /
  `DomainPurityTest.kt:50`, except under `res/` not `java/`. It MUST `assertTrue(file.exists()) { "…
  (working dir = ${File(".").absolutePath})" }` on each of the four files (mirroring
  `DomainPurityTest:52`) so a moved/missing path fails **loud** instead of passing on an empty parse.
- **Parsing:** `javax.xml.parsers.DocumentBuilderFactory` (JDK built-in — **no new Gradle dependency**);
  import nothing from `android.*` / `androidx.*` / `org.robolectric.*`. (All 566 `<string>` entries are
  single-line today, so DOM parsing is robust and simple.)

It parses the four XML files and asserts, for `strings.xml` and `plurals.xml` independently:

- **Identical key sets** between `values/` and `values-es/` — fails listing any key missing from
  Spanish (would be `MissingTranslation`) or extra in Spanish (would be `ExtraTranslation`).
- **Identical format-arg signature** — the multiset of format specifiers in the Spanish value equals
  the English value's, catching a dropped/renumbered arg that would crash at `String.format` /
  `getQuantityString`. **Extraction grammar (exact):** first strip escaped `%%` (replace with empty),
  *then* match `Regex("%(\\d+\\$)?[a-zA-Z]")` and collect into a multiset. So `%1$s%%` → `{%1$s}`,
  `+2%` → `{}` (bare literal, no specifier), `%1$d%%` → `{%1$d}`. `formatted="false"` strings yield an
  empty set. For `<string>` the comparison is **per key**; for plurals it is **per `(plural-name,
  quantity)` item** (see below). Include a guard-the-guard fixture (as
  `ComposeHardcodedStringTest.kt:110–123` does) asserting `%1$s%%`→`{%1$s}` and `+2%`→`{}` so the
  extractor itself can't silently drift.
- **`formatted="false"` parity** — a string that is `formatted="false"` in English is `formatted="false"`
  in Spanish (and vice-versa).
- **Plurals: identical `quantity` item sets per plural**, AND the arg-signature check compares
  **per matching `(plural-name, quantity)` pair** — English `boss_in_waves/one` `{}` vs Spanish
  `boss_in_waves/one` `{}`; English `boss_in_waves/other` `{%1$d}` vs Spanish `.../other` `{%1$d}`.
  **Not** a per-plural aggregate (which would mask a `%1$d` moved into the wrong item) and **not**
  per-item-within-one-locale (which would wrongly demand `%1$d` in every item). `boss_in_waves` is the
  sole intra-plural-asymmetric case and is the worked example the test should encode.

This makes the CI `MissingTranslation`/`ExtraTranslation` protection explicit and — crucially — pins
**arg-signature drift** the moment English adds/changes a string without a matching Spanish edit. It is
the "add-a-locale contract" enforced in code, not prose. It is the **authoritative** guard: it covers
missing/extra keys, arg-signature, and `formatted="false"` parity on the fast JVM lane independent of
any lint default severity.

Rationale for a dedicated test even though `lintRelease` already runs `MissingTranslation`: lint catches
missing keys but is weaker on per-key arg-signature equality and on `formatted="false"` *parity*; the
test also fails fast on the JVM lane with a precise per-key diff, and future locales inherit the guard
for free. (Verified in review: under AGP 9.2.1 the resolved `lint-checks-32.2.1` jar defines
`MissingTranslation` / `ExtraTranslation` / `StringFormatInvalid` as **FATAL** by default, so
`lintRelease` does gate them today — but the repo doesn't *pin* those severities, so the completeness
test owns the guarantee rather than relying on an unpinned AGP default.)

## Verification

- `./run-gradle.sh :app:lintDebug :app:lintRelease` — **must be green.** This is the lint gate:
  `MissingTranslation`, `ExtraTranslation`, `StringFormatInvalid` (all FATAL by default under AGP
  9.2.1 — see the completeness-guard note). Run explicitly — CI runs it, but it's not part of
  `testDebugUnitTest`/detekt/ktlint. During the plan, verify severity once by intentionally breaking a
  Spanish key/arg and confirming `lintRelease` goes red (treat `LocaleCompletenessTest` as the
  authoritative guard regardless).
- `./run-gradle.sh assembleDebug` — the **XML-escaping backstop**: a raw unescaped `'`/`&`/`<` in
  `values-es` is an aapt2 resource-compile hard error caught here (and in CI), **not** by
  `LocaleCompletenessTest` (lenient parse). Confirms the whole `values-es` set compiles.
- `./run-gradle.sh testDebugUnitTest` — 1314 existing + the new `LocaleCompletenessTest`.
- `./run-gradle.sh :app:detekt` + `./lint-kotlin.sh` — unchanged (no Kotlin production code touched; the
  new test is Kotlin, so it is format/detekt-checked).
- **Manual follow-up (not CI):** on-device smoke on a Spanish-locale emulator to eyeball (a) truncation
  on the longest strings (Spanish runs ~15–25% longer than English) — battle HUD, Store, Settings,
  onboarding — and (b) the intended English-body-under-Spanish-title mix on Help → "Open-source
  notices" (`R.raw.oss_notices`, fidelity rule #9). Recorded as a note, not a gate.

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
| Bare literal `%` (`uw_path_dot`) drifts toward a conversion in Spanish word order | Fidelity rule #3 keeps `% ` / escapes as `%%`; `lintRelease` `StringFormatInvalid` backstop (the arg-signature test does NOT cover it). |
| Raw unescaped `'`/`&`/`<` in `values-es` | `assembleDebug` aapt2 resource-compile hard error (in the CI gate) — not the completeness test. |
| `app_name` accidentally translated → shipped launcher-label branding regression | Fidelity rule #8: `values-es` `app_name` = identical `Steps of Babylon`. The completeness test can't catch a value-only change (key is present) — this is a code-review + translator-instruction guard. |
| Language mix: English `R.raw.oss_notices` body under a Spanish Help title | Intended (fidelity rule #9) — Apache-2.0 attribution/license names stay English; flagged in on-device smoke + human-review issue as a conscious call. |
| Layout truncation from longer Spanish text | Manual on-device smoke follow-up; the readiness contract already uses flexible Compose layouts. |
| English adds a string later without a Spanish counterpart | `LocaleCompletenessTest` turns that into a red build — the permanent guard. |

## Success criteria

- A Spanish-set device shows the app in Spanish (UI, gameplay, notifications, plurals, help, privacy)
  with correct pluralization and no `String.format` crashes — save the four intended English residuals
  (fidelity rule #9: `SupplyDropTrigger.message`, `BillingProduct.priceDisplay` fallback, seed cosmetic
  fallback fields, and the `R.raw.oss_notices` Help body under a Spanish title).
- English (and every other locale) is byte-for-byte unaffected — `values/` untouched.
- `assembleDebug`, `lintDebug`, `lintRelease`, `testDebugUnitTest` (incl. the new guard), detekt, ktlint
  all green.
- No production Kotlin, no schema, no dependency, no `versionCode` change.
