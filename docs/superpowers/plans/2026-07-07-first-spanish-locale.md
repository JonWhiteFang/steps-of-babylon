# First non-English locale: Spanish (`es`) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a complete Spanish (`es`) translation of Steps of Babylon — the first non-English locale — turning the app's existing 100%-locale-readiness (#34, ADR-0014) into a real user-facing feature.

**Architecture:** Two new Android resource files (`values-es/strings.xml` mirroring all 566 `<string>`, `values-es/plurals.xml` mirroring all 16 `<plurals>`) plus one new pure-JVM guard test (`architecture/LocaleCompletenessTest`) that pins key-set / format-arg-signature / `formatted="false"` parity between the two locales. No production Kotlin, no schema, no dependency, no `versionCode` change. Android auto-selects `values-es` on Spanish-set devices; every other locale is byte-for-byte unaffected.

**Tech Stack:** Android string resources (aapt2), JUnit Jupiter (JVM lane), `javax.xml` DocumentBuilder (JDK built-in — no new Gradle dep), Gradle via `./run-gradle.sh`.

**Spec:** `docs/superpowers/specs/2026-07-07-first-spanish-locale-design.md` (passed the Adversarial Review Gate: 22 raised / 16 survived / 5 refuted).

**Branch:** `i18n/first-spanish-locale` (already created; the spec + its review amendments are already committed here).

**Review lane note:** this diff touches ONLY resource XML + a new test — none of `presentation/battle/engine/**`, `effects/**`, a Room DAO, `PlayerRepositoryImpl`, the spend/claim use cases, or any shared-collection/currency mutation. The mandatory `concurrency-reviewer` lane (ADR-0038) therefore does **NOT** apply. The guard-sensitive-edits advisory hook will not fire on these paths.

---

## Translation glossary (USE FOR EVERY STRING — consistency is load-bearing)

The single most important input for translation quality and for the later human review. Every task that
writes Spanish text MUST use these exact renderings so the same game term never appears two ways.

| English term | Spanish (canonical) | Notes |
|---|---|---|
| Steps (currency) | **Pasos** | The walking currency. Capitalized as a proper currency noun. |
| Cash (in-round) | **Dinero** | In-round currency. |
| Gems | **Gemas** | |
| Power Stones | **Piedras de Poder** | |
| Card Copies / Copies | **Copias** | |
| Workshop | **Taller** | |
| Labs / Research | **Laboratorios** / **Investigación** | |
| Cards | **Cartas** | |
| Ultimate Weapon(s) (UW) | **Arma Definitiva / Armas Definitivas** | |
| Wave | **Oleada** | |
| Tier | **Nivel** | difficulty level 1–10 (distinct from card "level" = **nivel de carta**; context disambiguates) |
| Biome | **Bioma** | |
| Enemy / Boss | **Enemigo / Jefe** | |
| Upgrade (verb/noun) | **Mejora / Mejorar** | |
| Health / HP | **Salud / PS** (Puntos de Salud) | keep `HP`→`PS` only where the English uses "HP"; if it uses "Health", use "Salud" |
| Damage | **Daño** | |
| Attack Speed | **Velocidad de Ataque** | |
| Critical Chance | **Probabilidad Crítica** | |
| Range | **Alcance** | |
| Lifesteal | **Robo de Vida** | |
| Rapid Fire | **Fuego Rápido** | |
| Supply Drop | **Suministro** | |
| Milestone | **Hito** | |
| Daily Mission | **Misión Diaria** | |
| Settings | **Ajustes** | |
| Store | **Tienda** | |
| Home | **Inicio** | |
| Battle | **Batalla** | |
| Missions | **Misiones** | |
| Stats | **Estadísticas** | |
| Help | **Ayuda** | |
| Claim (verb) | **Reclamar** | |
| Equip / Equipped | **Equipar / Equipado** | |

**Biome proper names** (narrative — translate meaningfully, keep consistent):
Hanging Gardens → **Jardines Colgantes** · Burning Sands → **Arenas Ardientes** · Frozen Ziggurats →
**Zigurats Congelados** · Underworld of Kur → **Inframundo de Kur** · Celestial Gate → **Puerta Celestial**.

**Brand:** `app_name` = **"Steps of Babylon"** — NOT translated (proper-noun launcher label; see Task 3, rule R8).

---

## Fidelity rules (from the reviewed spec — every Spanish value MUST obey)

- **R1 — Format args verbatim.** Every `%N$x` (positional, any index up to 4) and bare `%x` present in the
  English value appears in the Spanish value, same multiset. `%%` is an escaped literal percent — preserve
  it, never treat it as an arg. Positional `%1$s`/`%2$s` may be reordered in the sentence; bare `%d`/`%s`
  keep original order.
- **R2 — `formatted="false"` carried over.** The 25 English strings with `formatted="false"` keep that
  attribute in Spanish. Omitting it fails `lintRelease` (`StringFormatInvalid` "multiple substitutions").
- **R3 — Bare literal `%`.** `uw_path_dot` = `"DoT % MaxHP/sec"` (`strings.xml:365`, no `formatted="false"`):
  keep the `% ` (percent + space) so it can't drift into a conversion. Spanish e.g. `"DoT % PS máx/seg"`.
- **R4 — Plural arg-signatures are per `(name, quantity)` item** and may differ within one plural.
  `boss_in_waves`: `one` has 0 args, `other` has `%1$d` — preserve per item (Spanish `one` carries no arg).
- **R5 — XML escaping.** `'`→`\'`, `&`→`&amp;`, `<`→`&lt;`. Accented/inverted chars (á é í ó ú ñ ¿ ¡ ü) are
  literal UTF-8 — never escape. `assembleDebug` (aapt2) is the backstop for a missed escape.
- **R6 — Multi-line bodies** (`hc_privacy_policy_body`, the 9 `help_*_body`, `crash_report_email_body`):
  keep `\n`, bullets (•, →, —), emoji, URLs, the email address, and paragraph structure. Translate prose only.
- **R7 — Emoji/glyph strings** keep the glyph in the same position (e.g. `👟 +%1$d Pasos`).
- **R8 — `app_name` stays `Steps of Babylon`** (identical value; do not translate the brand).
- **R9 — Residuals NOT touched** (not translatable resources): `SupplyDropTrigger.message`,
  `BillingProduct.priceDisplay`, `CosmeticRepositoryImpl` seed fields, `R.raw.oss_notices` body.

---

## File structure

- **Create** `app/src/test/java/com/whitefang/stepsofbabylon/architecture/LocaleCompletenessTest.kt` —
  the pure-JVM parity guard (Task 1).
- **Create** `app/src/main/res/values-es/plurals.xml` — 16 translated plurals (Task 2).
- **Create** `app/src/main/res/values-es/strings.xml` — 566 translated strings (Task 3).
- **Modify (docs, Task 5):** `CLAUDE.md` (headline test count), `CHANGELOG.md`,
  `docs/steering/source-files.md`, `docs/plans/master-plan.md`, `docs/agent/DECISIONS/ADR-0014-i18n-string-extraction.md`.
- **Modify (spine, Task 6):** `docs/agent/STATE.md`, `docs/agent/RUN_LOG.md`.

---

## Task 1: Locale-completeness guard test (TDD — write the failing guard first)

**Files:**
- Create: `app/src/test/java/com/whitefang/stepsofbabylon/architecture/LocaleCompletenessTest.kt`

The guard-the-guard unit tests (the extractor fixtures) pass immediately; the two parity tests fail RED
because `values-es/` does not exist yet — that is the intended failing state that Tasks 2–3 turn green.

- [ ] **Step 1: Write the test file (complete code)**

```kotlin
package com.whitefang.stepsofbabylon.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * #34 (i18n): the "add-a-locale contract" enforced in code. Pins that every non-default locale mirrors
 * the English default exactly on the three axes that silently break a translation at build- or run-time:
 *  1. identical key set (a missing key = Android `MissingTranslation`; an extra = `ExtraTranslation`);
 *  2. identical format-arg signature per key (a dropped/renumbered `%N$x` crashes `String.format` /
 *     `getQuantityString`); and
 *  3. `formatted="false"` parity (a bare-`%` prose string missing the attr fails `StringFormatInvalid`).
 *
 * Pure-JVM (mirrors [ComposeHardcodedStringTest]/[DomainPurityTest]): reads the resource XML directly with
 * the JDK DocumentBuilder — no emulator, no Robolectric, no new dependency. JVM working dir = the :app
 * module root, so paths are module-relative under `src/main/res/`.
 *
 * When a new locale ships, add its `values-xx/` dir to [LOCALES]. When English adds a string without a
 * matching translation, THIS test goes red — that is the guard.
 */
class LocaleCompletenessTest {
    /** Non-default locales to check against the English default. Add a row per new `values-xx/`. */
    private val LOCALES = listOf("es")

    private val defaultStrings = File("src/main/res/values/strings.xml")
    private val defaultPlurals = File("src/main/res/values/plurals.xml")
    private fun localeStrings(locale: String) = File("src/main/res/values-$locale/strings.xml")
    private fun localePlurals(locale: String) = File("src/main/res/values-$locale/plurals.xml")

    /**
     * Format-arg specifiers: positional `%N$x` (any index) or bare `%x`. Escaped `%%` is stripped BEFORE
     * scanning so a literal percent is never miscounted as an arg.
     */
    private val specifier = Regex("""%(\d+\$)?[a-zA-Z]""")

    /** Sorted multiset of format specifiers in a resource value (empty for literal-only / formatted=false). */
    internal fun argSignature(value: String): List<String> =
        specifier.findAll(value.replace("%%", "")).map { it.value }.sorted()

    // ---- guard-the-guard: the extractor itself can't silently drift ----

    @Test
    fun `arg-signature extractor handles positional args, escaped percent, and bare literal percent`() {
        assertEquals(listOf("%1\$s"), argSignature("%1\$s%%"))                 // stat_percent pattern
        assertEquals(listOf("%1\$d"), argSignature("+%1\$d%% Damage"))         // card_effect pattern
        assertEquals(emptyList<String>(), argSignature("+2%"))                 // bare literal, no arg
        assertEquals(emptyList<String>(), argSignature("DoT % MaxHP/sec"))     // uw_path_dot, bare % + space
        assertEquals(
            listOf("%1\$s", "%2\$s", "%3\$s", "%4\$s"),
            argSignature("Exception: %1\$s\nMessage: %2\$s\nStack:\n%3\$s\n\n%4\$s"), // crash_report_email_body
        )
    }

    // ---- <string> parity ----

    @Test
    fun `each locale mirrors English string keys, arg-signatures, and formatted-false`() {
        assertFileExists(defaultStrings)
        val en = parseStrings(defaultStrings)
        for (locale in LOCALES) {
            val file = localeStrings(locale)
            assertFileExists(file)
            val loc = parseStrings(file)
            assertEquals(en.keys, loc.keys) {
                "values-$locale/strings.xml key drift (MissingTranslation/ExtraTranslation):\n" +
                    keyDiff(en.keys, loc.keys)
            }
            val argDrift = en.keys.filter { argSignature(en.getValue(it).text) != argSignature(loc.getValue(it).text) }
            assertTrue(argDrift.isEmpty()) { "values-$locale/strings.xml format-arg drift in: $argDrift" }
            val fmtDrift = en.keys.filter { en.getValue(it).formattedFalse != loc.getValue(it).formattedFalse }
            assertTrue(fmtDrift.isEmpty()) { "values-$locale/strings.xml formatted=\"false\" parity drift in: $fmtDrift" }
        }
    }

    // ---- <plurals> parity (per (name, quantity) item) ----

    @Test
    fun `each locale mirrors English plural names, quantities, and per-item arg-signatures`() {
        assertFileExists(defaultPlurals)
        val en = parsePlurals(defaultPlurals)
        for (locale in LOCALES) {
            val file = localePlurals(locale)
            assertFileExists(file)
            val loc = parsePlurals(file)
            assertEquals(en.keys, loc.keys) { "values-$locale/plurals.xml name drift:\n" + keyDiff(en.keys, loc.keys) }
            for (name in en.keys) {
                val enItems = en.getValue(name)
                val locItems = loc.getValue(name)
                assertEquals(enItems.keys, locItems.keys) {
                    "values-$locale/plurals.xml quantity-item drift in plural '$name'"
                }
                for (q in enItems.keys) {
                    assertEquals(argSignature(enItems.getValue(q)), argSignature(locItems.getValue(q))) {
                        "values-$locale/plurals.xml arg-signature drift in plural '$name' quantity '$q'"
                    }
                }
            }
        }
    }

    // ---- helpers ----

    private data class StringEntry(val text: String, val formattedFalse: Boolean)

    private fun assertFileExists(file: File) =
        assertTrue(file.exists()) { "not found at ${file.absolutePath} (working dir = ${File(".").absolutePath})" }

    private fun parseStrings(file: File): Map<String, StringEntry> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            .getElementsByTagName("string")
        val out = LinkedHashMap<String, StringEntry>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            out[el.getAttribute("name")] = StringEntry(el.textContent, el.getAttribute("formatted") == "false")
        }
        return out
    }

    private fun parsePlurals(file: File): Map<String, Map<String, String>> {
        val plurals = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            .getElementsByTagName("plurals")
        val out = LinkedHashMap<String, Map<String, String>>()
        for (i in 0 until plurals.length) {
            val p = plurals.item(i) as Element
            val items = LinkedHashMap<String, String>()
            val itemNodes = p.getElementsByTagName("item")
            for (j in 0 until itemNodes.length) {
                val it = itemNodes.item(j) as Element
                items[it.getAttribute("quantity")] = it.textContent
            }
            out[p.getAttribute("name")] = items
        }
        return out
    }

    private fun keyDiff(en: Set<String>, loc: Set<String>): String =
        "  missing in locale: ${(en - loc).sorted()}\n  extra in locale:   ${(loc - en).sorted()}"
}
```

- [ ] **Step 2: Run the test — expect the extractor fixture GREEN, the two parity tests RED**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests 'com.whitefang.stepsofbabylon.architecture.LocaleCompletenessTest'`
Expected: the `arg-signature extractor …` test PASSES; the two parity tests FAIL with
`not found at …/src/main/res/values-es/strings.xml (working dir = …/app)` — confirming the module-relative
path resolves and the guard fails loud on a missing locale (not a silent empty pass).

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/whitefang/stepsofbabylon/architecture/LocaleCompletenessTest.kt
git commit -m "test(#34): LocaleCompletenessTest — locale key/arg/formatted parity guard"
```

---

## Task 2: `values-es/plurals.xml` (all 16 plurals)

**Files:**
- Create: `app/src/main/res/values-es/plurals.xml`

Full translated content below (small enough to specify verbatim). Note `boss_in_waves` preserves the
intra-plural arg asymmetry (R4: `one` has no `%1$d`), `page_x_of_n` keeps both `%1$d`/`%2$d`,
`steps_earned_banner` keeps the 👟 (R7), and `time_ago_*` use Spanish "hace …" order (positional-safe).

- [ ] **Step 1: Write the file (complete content)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Spanish (es) — first non-English locale (#34). Mirrors values/plurals.xml key-for-key.
         Spanish CLDR plural categories are one/other, same as English. -->

    <plurals name="fx_step_reward">
        <item quantity="one">+%1$d Paso</item>
        <item quantity="other">+%1$d Pasos</item>
    </plurals>

    <plurals name="steps_earned_banner">
        <item quantity="one">👟 +%1$d Paso</item>
        <item quantity="other">👟 +%1$d Pasos</item>
    </plurals>

    <plurals name="wave_enemies">
        <item quantity="one">%1$d enemigo</item>
        <item quantity="other">%1$d enemigos</item>
    </plurals>

    <plurals name="boss_in_waves">
        <item quantity="one">Jefe la próxima oleada</item>
        <item quantity="other">Jefe en %1$d oleadas</item>
    </plurals>

    <plurals name="reward_gems">
        <item quantity="one">+%1$d Gema</item>
        <item quantity="other">+%1$d Gemas</item>
    </plurals>
    <plurals name="reward_power_stones">
        <item quantity="one">+%1$d Piedra de Poder</item>
        <item quantity="other">+%1$d Piedras de Poder</item>
    </plurals>
    <plurals name="reward_steps">
        <item quantity="one">+%1$d Paso</item>
        <item quantity="other">+%1$d Pasos</item>
    </plurals>

    <plurals name="card_copies">
        <item quantity="one">+%1$d Copia</item>
        <item quantity="other">+%1$d Copias</item>
    </plurals>

    <plurals name="days_remaining">
        <item quantity="one">Activo — %1$d día restante</item>
        <item quantity="other">Activo — %1$d días restantes</item>
    </plurals>

    <plurals name="page_x_of_n">
        <item quantity="one">Página %1$d de %2$d</item>
        <item quantity="other">Página %1$d de %2$d</item>
    </plurals>

    <plurals name="widget_steps">
        <item quantity="one">%1$s paso</item>
        <item quantity="other">%1$s pasos</item>
    </plurals>

    <plurals name="reminder_steps_away">
        <item quantity="one">¡Estás a %1$d paso de mejorar %2$s!</item>
        <item quantity="other">¡Estás a %1$d pasos de mejorar %2$s!</item>
    </plurals>

    <plurals name="notif_today_steps">
        <item quantity="one">Hoy: %1$d paso</item>
        <item quantity="other">Hoy: %1$d pasos</item>
    </plurals>

    <plurals name="time_ago_minutes">
        <item quantity="one">hace %1$dm</item>
        <item quantity="other">hace %1$dm</item>
    </plurals>
    <plurals name="time_ago_hours">
        <item quantity="one">hace %1$dh</item>
        <item quantity="other">hace %1$dh</item>
    </plurals>
    <plurals name="time_ago_days">
        <item quantity="one">hace %1$dd</item>
        <item quantity="other">hace %1$dd</item>
    </plurals>
</resources>
```

- [ ] **Step 2: Verify the plurals half of the guard now passes**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests 'com.whitefang.stepsofbabylon.architecture.LocaleCompletenessTest'`
Expected: the plurals parity test PASSES (16 names, quantities, per-item arg-sigs all match); the strings
parity test still FAILS (`values-es/strings.xml` missing) — that's Task 3.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values-es/plurals.xml
git commit -m "feat(#34): Spanish (es) plurals.xml"
```

---

## Task 3: `values-es/strings.xml` (all 566 strings)

**Files:**
- Create: `app/src/main/res/values-es/strings.xml`

This is the bulk deliverable. Rather than reproduce 566 lines here, the recipe is deterministic: mirror
`app/src/main/res/values/strings.xml` **key-for-key** (same order, same feature-area comments translated
or kept), translating each value per the **glossary** and the **fidelity rules R1–R9** above. The
completeness test + `lintRelease` + `assembleDebug` (Task 4) make "correct" objective — there is no
guesswork about done-ness.

**Procedure (follow exactly):**

- [ ] **Step 1: Copy the English file as the scaffold**

```bash
cp app/src/main/res/values/strings.xml app/src/main/res/values-es/strings.xml
```

This guarantees identical keys, `formatted="false"` attributes, and order — so R2/R8 and the key-parity
check are satisfied structurally; you then translate the *text content* of each entry in place.

- [ ] **Step 2: Translate every value per the glossary + R1–R9**

Walk the file top to bottom and replace each element's English text with Spanish, obeying:
- **R8:** leave `<string name="app_name">Steps of Babylon</string>` EXACTLY as-is (brand).
- **R1:** keep every `%N$d`/`%N$s` and bare `%d`/`%s` in each value (you may reorder positional args to
  suit Spanish word order). Keep `%%` literal (e.g. `stat_percent` `%1$s%%` → `%1$s%%`,
  `card_effect_glass_cannon` `+%1$d%% Damage, -%2$d%% Health` → `+%1$d%% Daño, -%2$d%% Salud`).
- **R2:** the 25 `formatted="false"` entries KEEP the attribute (Step 1's copy already did this — don't
  strip it). These are the `upgrade_desc_*` / research / UW percentage-prose strings around lines 512–561.
- **R3:** `uw_path_dot` `"DoT % MaxHP/sec"` → e.g. `"DoT % PS máx/seg"` (keep the `% ` intact).
- **R5:** escape any Spanish apostrophe as `\'` (e.g. contractions are rare in formal Spanish, but a
  possessive/elision like `device's` → `del dispositivo` avoids the apostrophe entirely — prefer that).
  Escape `&` → `&amp;`. Inverted marks `¿ ¡` and accents are literal.
- **R6:** for `hc_privacy_policy_body` (line 439), the 9 `help_*_body` (445–461), and
  `crash_report_email_body` (126): translate the prose but keep every `\n`, every `•`/`→`/`—`, the URL
  `https://jonwhitefang.github.io/steps-of-babylon/`, the email `jonwhitefang@gmail.com`, and the
  `%1$s`..`%4$s` args (crash body). Section headers inside the privacy body ("Health Connect Integration",
  "Data Storage", "Advertising") are translated prose.
- **R7:** any 👟/emoji stays in position.
- Use the **biome proper names** and the glossary term for every currency/screen/mechanic noun.

> Reviewer/human-review note: this is a machine translation. The privacy/legal body
> (`hc_privacy_policy_body`) and gameplay-term consistency are exactly what the Task 6 follow-up issue
> flags for eventual native review. Translate faithfully; do not paraphrase the privacy meaning.

- [ ] **Step 3: Confirm no accidental key/structure change**

```bash
# Same number of <string> elements as English (566), and app_name still English:
grep -c '<string name=' app/src/main/res/values-es/strings.xml   # expect 566
grep -c 'formatted="false"' app/src/main/res/values-es/strings.xml # expect 25
grep 'name="app_name"' app/src/main/res/values-es/strings.xml      # expect: >Steps of Babylon<
```
Expected: `566`, `25`, and the `app_name` line still reads `Steps of Babylon`.

- [ ] **Step 4: Run the completeness guard — expect ALL GREEN now**

Run: `./run-gradle.sh :app:testDebugUnitTest --tests 'com.whitefang.stepsofbabylon.architecture.LocaleCompletenessTest'`
Expected: all three tests PASS. If the strings parity test reports `format-arg drift` or
`formatted="false" parity drift`, the named keys have a dropped `%`-arg or a stripped attribute — fix
those values and re-run. If it reports key drift, an element name was accidentally edited — restore it.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values-es/strings.xml
git commit -m "feat(#34): Spanish (es) strings.xml — first non-English locale"
```

---

## Task 4: Full build verification (lint + resource compile + suite)

**Files:** none (verification only).

- [ ] **Step 1: Resource-compile backstop (aapt2 catches any unescaped `'`/`&`/`<`)**

Run: `./run-gradle.sh :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. A failure here naming `values-es/strings.xml` with an aapt2 error (e.g.
"apostrophe not preceded by \\") points at the exact line — escape it per R5 and re-run.

- [ ] **Step 2: Lint gate (MissingTranslation / ExtraTranslation / StringFormatInvalid — FATAL under AGP 9.2.1)**

Run: `./run-gradle.sh :app:lintDebug :app:lintRelease`
Expected: `BUILD SUCCESSFUL`, no `MissingTranslation`/`ExtraTranslation`/`StringFormatInvalid`. If
`lintRelease` reports `StringFormatInvalid` "multiple substitutions" on a `values-es` string, that string
has a bare `%` and needs `formatted="false"` (it should have inherited it from the English copy — check R2/R3).

- [ ] **Step 3: Full unit suite (no regressions; new guard included)**

Run: `./run-gradle.sh :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Existing tests unchanged; `LocaleCompletenessTest`'s 3 methods pass.
(`PluralsResourceTest` stays green — Robolectric resolves the default locale, unaffected by `values-es`.)

- [ ] **Step 4: Kotlin static analysis / format (the new test is Kotlin)**

Run: `./run-gradle.sh :app:detekt && ./lint-kotlin.sh`
Expected: both clean. If `lint-kotlin.sh` reports a format issue in `LocaleCompletenessTest.kt`, run
`./lint-kotlin.sh --format` to auto-fix, then re-run `detekt`.

- [ ] **Step 5: Record the new JVM test-count total**

```bash
# Count @Test methods added by this PR (expect 3) to update the headline count in Task 5:
grep -c '@Test' app/src/test/java/com/whitefang/stepsofbabylon/architecture/LocaleCompletenessTest.kt
```
Expected: `3`. New headline JVM total = 1314 + 3 = **1317** (use the real number if the suite total differs).

- [ ] **Step 6: Commit any verification fixes** (only if Steps 1–4 required edits)

```bash
git add -A
git commit -m "fix(#34): resource-escape / arg-signature fixes from Spanish-locale verification"
```

---

## Task 5: Sync current-state docs (BEFORE the STATE/RUN_LOG update — PR Task-List Convention)

**Files:**
- Modify: `CLAUDE.md` (headline test count)
- Modify: `CHANGELOG.md`
- Modify: `docs/steering/source-files.md`
- Modify: `docs/plans/master-plan.md`
- Modify: `docs/agent/DECISIONS/ADR-0014-i18n-string-extraction.md`

- [ ] **Step 1: `CLAUDE.md` — bump the headline test count**

Find the Testing line `**Headline count: 1314 JVM tests + 9 instrumented tests.**` and change `1314` to the
number from Task 4 Step 5 (expected **1317**). Touch nothing else in CLAUDE.md (it documents stable
architecture; a new locale doesn't change any invariant).

- [ ] **Step 2: `CHANGELOG.md` — add an `[Unreleased]` entry**

Under `## [Unreleased]`, add:
```markdown
### Added
- **First non-English locale: Spanish (`es`)** (#34) — complete `values-es/` translation of all 566
  strings + 16 plurals; the app's 100% locale-readiness (ADR-0014) now ships a real locale. Device-language
  only (no in-app picker). `app_name` and the documented English residuals
  (`SupplyDropTrigger.message`, `BillingProduct.priceDisplay`, seed cosmetic fallbacks, `R.raw.oss_notices`
  body) stay English by design. New `architecture/LocaleCompletenessTest` (JVM) pins locale key /
  format-arg-signature / `formatted="false"` parity. No production Kotlin, schema, dependency, or
  `versionCode` change. Machine-translated — flagged for native review (see follow-up issue) before
  promotion beyond internal.
```
If a current-state block in CHANGELOG carries a test count, update it to 1317.

- [ ] **Step 3: `docs/steering/source-files.md` — add the three new files**

Add entries (match the file's existing format) for:
- `app/src/main/res/values-es/strings.xml` — "Spanish translation of all UI strings (#34)."
- `app/src/main/res/values-es/plurals.xml` — "Spanish translation of all quantity strings (#34)."
- `app/src/test/java/com/whitefang/stepsofbabylon/architecture/LocaleCompletenessTest.kt` — "Pure-JVM guard: locale key/arg-signature/formatted parity vs the English default (#34)."

- [ ] **Step 4: `docs/plans/master-plan.md` — update #34 status**

In the status tracker, move #34 from "locale-ready, first locale pending" to "first locale (Spanish) shipped;
remaining locales are per-language translation efforts." (Match the tracker's exact wording style.)

- [ ] **Step 5: `ADR-0014` — append a first-locale amendment**

Add an "Amendment — 2026-07-07: first locale (Spanish)" note recording: Spanish is the first shipped
`values-xx`; machine-translated with privacy/legal + gameplay terms flagged for native review; the
"add-a-locale = mirror both XML files + pass `LocaleCompletenessTest`" recipe; and that `R.raw.oss_notices`
joins the documented English residuals (title localizes, body stays English).

- [ ] **Step 6: Commit the doc sweep**

```bash
git add CLAUDE.md CHANGELOG.md docs/steering/source-files.md docs/plans/master-plan.md docs/agent/DECISIONS/ADR-0014-i18n-string-extraction.md
git commit -m "docs(#34): sync current-state docs for Spanish locale"
```

---

## Task 6: Spine update + follow-up issue + PR (End-of-Run memory writes)

**Files:**
- Modify: `docs/agent/STATE.md`
- Modify: `docs/agent/RUN_LOG.md`

- [ ] **Step 1: File the human-review follow-up issue**

```bash
gh issue create --title "[i18n] Native review of the Spanish (es) locale before promotion" \
  --label "i18n,ux" \
  --body "The first non-English locale (Spanish, #34) shipped machine-translated. Before promoting the app beyond the internal track, a native/professional Spanish speaker should review: (1) the privacy/legal body \`hc_privacy_policy_body\` for legal precision; (2) gameplay-term consistency against the glossary in docs/superpowers/plans/2026-07-07-first-spanish-locale.md; (3) on-device truncation on long strings (battle HUD, Store, Settings, onboarding); (4) the intended English \`R.raw.oss_notices\` body under a Spanish Help title (confirm it's acceptable). No code blocker — the LocaleCompletenessTest already guards mechanical parity."
```
Note the returned issue number for the STATE/RUN_LOG entries.

- [ ] **Step 2: `docs/agent/STATE.md` — flip the i18n line + note what shipped**

In the i18n fragile-zone line (currently "the app is 100% locale-ready … Only the first real non-English
`values-xx` locale remains"), change it to state Spanish (`es`) is now the first shipped locale, and that
adding a further locale = mirror `values-xx/strings.xml` + `plurals.xml` and pass `LocaleCompletenessTest`.
Update the "Open tracks remaining" list (drop "first non-English locale" as the headline i18n item; the
remaining i18n work is additional languages + the native-review follow-up). Keep STATE to one page.

- [ ] **Step 3: `docs/agent/RUN_LOG.md` — prepend a session entry**

Add a dated entry summarizing: shipped Spanish (`es`) as the first locale (566 strings + 16 plurals),
added `LocaleCompletenessTest`, doc sync, filed the native-review follow-up (#NNN); no production
Kotlin/schema/dep/versionCode change; test count 1314 → 1317; passed the Adversarial Review Gate
(22/16/5) at spec stage.

- [ ] **Step 4: Commit the spine update**

```bash
git add docs/agent/STATE.md docs/agent/RUN_LOG.md
git commit -m "docs(#34): STATE + RUN_LOG — Spanish locale shipped"
```

- [ ] **Step 5: Push and open the PR**

```bash
git push -u origin i18n/first-spanish-locale
gh pr create --title "feat(#34): first non-English locale — Spanish (es)" \
  --body "Ships the first \`values-es/\` locale (566 strings + 16 plurals) + \`LocaleCompletenessTest\` parity guard. Device-language only. No production Kotlin/schema/dep/versionCode change. Machine-translated; native-review follow-up filed. Spec + Adversarial Review Gate: docs/superpowers/specs/2026-07-07-first-spanish-locale-design.md. Closes #34 (extraction + first locale)."
```
Expected: PR opens; the PR gate (testDebugUnitTest + lintDebug + lintRelease + assembleDebug + detekt + ktlint) runs. Merge on green per the sequential-merge rule.

---

## Self-review checklist (completed by plan author)

- **Spec coverage:** ✔ Spanish locale (Task 3), plurals (Task 2), `LocaleCompletenessTest` with the exact
  pinned identity/grammar/per-item plural rule (Task 1), assembleDebug escaping backstop + lint + suite
  (Task 4), all doc syncs incl. ADR-0014 amendment + oss_notices residual + app_name rule (Tasks 3/5),
  follow-up issue (Task 6). Device-language-only / no-picker honored (no locales_config work). Out-of-scope
  items (picker, 2nd locale, versionCode) are absent by construction.
- **Placeholder scan:** the only non-verbatim artifact is `values-es/strings.xml` (566 entries) — the spec
  and skill both treat bulk translation as generated content; it is made objective by the glossary + R1–R9
  + the completeness test + lint + assembleDebug gates (Task 4), so "done" is machine-checkable, not vibes.
- **Type/name consistency:** the test's `argSignature`/`parseStrings`/`parsePlurals`/`StringEntry.formattedFalse`/
  `keyDiff`/`assertFileExists` names are used consistently across Task 1 and referenced identically in
  Tasks 2–4. File paths (`values-es/strings.xml`, `values-es/plurals.xml`, the test FQN) are identical everywhere.
- **PR Task-List Convention:** Task 5 (current-state docs) precedes Task 6 (STATE + RUN_LOG), as required.
