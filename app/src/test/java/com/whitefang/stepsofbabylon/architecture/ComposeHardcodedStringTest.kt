package com.whitefang.stepsofbabylon.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * #382 (`cqt-1`): forward guard against a NEW hardcoded, user-facing prose `Text("literal")` regressing
 * into `presentation/`. i18n extraction (#34) is complete, so every user-facing string should resolve via
 * `stringResource(...)`; this test fails the build if a fresh single-line `Text("Some prose")` lands.
 *
 * The `lint{}` `HardcodedText` rule (`app/build.gradle.kts`) is **XML-only** and does NOT see Compose
 * literals — this is the Compose analogue. Dependency-free (mirrors [PresentationPurityTest] /
 * [StepCreditAllowlistTest]): walks `presentation/`, comment-strips, scans lines with a pure predicate.
 *
 * ## What counts as an offender
 * A `Text("…")` **Compose call** whose string literal:
 *  1. is anchored on a **word boundary** — `Text(` NOT preceded by an identifier char or `.`, so
 *     `canvas.drawText("…")`, `OutlinedText(...)`, etc. do NOT match (only the Compose `Text` composable); and
 *  2. contains **≥2 consecutive ASCII letters** (so pure-symbol/number literals like `"%"`, `"1"` are ignored); and
 *  3. contains **no `$`** (a `$`-interpolation is dynamic content — cash/badge counts — not translatable prose); and
 *  4. is not on the [allowlist] (an intentional non-translatable literal, e.g. a proper noun).
 *
 * ## Known scope boundaries (deliberate — do NOT "fix" without re-scoping the test)
 *  - **Single-line only.** A multi-line `Text(\n  "literal",\n  …)` evades the line-level scan. TWO such
 *    prose literals ship today and are intentionally OUT of this guard's scope for v1:
 *    `presentation/labs/LabsScreen.kt` and `presentation/weapons/UltimateWeaponScreen.kt` each render a
 *    multi-line `Text("MAX", …)` (a `R.string.upgrade_max` = "MAX" resource already exists). They are
 *    un-guarded here BY DESIGN — so a green run means "no NEW single-line prose", NOT "the tree is prose-free".
 *    If you expand this scan to multi-line, you MUST first migrate those two to `stringResource(...)` or
 *    add them to [allowlist], or the build goes red. (Migrating them is a fine follow-up; it is not part of #382.)
 *  - `contentDescription`/label/placeholder args are out of scope (covered by review + the i18n contract).
 *  - `buildAnnotatedString { append("…") }` prose is out of scope.
 *
 * This is a regression **tripwire** for the common case, not a proof — it complements, does not replace, the
 * i18n locale-readiness contract (ADR-0014).
 */
class ComposeHardcodedStringTest {
    private val presentation = File("src/main/java/com/whitefang/stepsofbabylon/presentation")

    /**
     * Intentional, non-translatable single-line `Text("…")` literals that are allowed to stay hardcoded.
     * Format: `"<FileName.kt>:<exact trimmed source line>"`. Empty today — the tree has zero single-line
     * prose offenders. Add an entry (with a code comment justifying it) only for a genuine proper-noun /
     * non-translatable case.
     */
    private val allowlist: Set<String> = emptySet()

    /**
     * Matches a Compose `Text("…")` call and captures the literal. The leading `(?:^|[^A-Za-z0-9_.])`
     * is the word-boundary anchor: it rejects `drawText(`/`myText(`/`foo.Text(` so only the top-level
     * `Text` composable trips. `[^"\\]*` keeps it to a simple (non-escaped) single-string-literal argument.
     */
    private val textCall = Regex("""(?:^|[^A-Za-z0-9_.])Text\s*\(\s*"([^"\\]*)"""")

    /** Two+ consecutive ASCII letters ⇒ this is prose, not a pure symbol/number literal. */
    private val hasProse = Regex("""[A-Za-z]{2,}""")

    /**
     * Pure predicate (isolated from the file walk so it's directly unit-testable): does this single
     * source line contain a hardcoded-prose `Text("…")`? A line may contain more than one `Text(` call.
     */
    internal fun isOffendingLine(trimmedLine: String): Boolean =
        textCall.findAll(trimmedLine).any { match ->
            val literal = match.groupValues[1]
            !literal.contains('$') && hasProse.containsMatchIn(literal)
        }

    /**
     * Strips `/* … */` (incl. KDoc) and `//` comments so the scan sees executable code only — same
     * pragmatic approach as [StepCreditAllowlistTest.stripComments].
     */
    private fun stripComments(source: String): String {
        val noBlock =
            source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)) { m ->
                "\n".repeat(m.value.count { it == '\n' })
            }
        return noBlock.lines().joinToString("\n") { it.substringBefore("//") }
    }

    @Test
    fun `presentation layer has no hardcoded single-line Text prose`() {
        assertTrue(presentation.isDirectory) {
            "presentation root not found at ${presentation.absolutePath} (working dir = ${File(".").absolutePath})"
        }

        val offenders =
            presentation
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    stripComments(file.readText())
                        .lines()
                        .map { it.trim() }
                        .filter { isOffendingLine(it) }
                        .map { "${file.name}: $it" }
                }.filterNot { entry -> allowlist.any { entry.startsWith(it) || entry == it } }
                .toList()

        assertTrue(offenders.isEmpty()) {
            "presentation/ must not add a hardcoded single-line Text(\"prose\") — route user-facing text through " +
                "stringResource(...) (i18n #34 / ADR-0014). Offenders:\n" + offenders.joinToString("\n")
        }
    }

    // ---- predicate unit tests (negative + positive fixtures — guard the guard) ----

    @Test
    fun `predicate flags a hardcoded prose Text`() {
        assertTrue(isOffendingLine("""Text("Game Over")"""))
        assertTrue(isOffendingLine("""Text("Battle error", style = foo)"""))
    }

    @Test
    fun `predicate ignores interpolations, symbols, drawText, and stringResource`() {
        assertFalse(isOffendingLine("""Text("${'$'}badgeCount")"""))
        assertFalse(isOffendingLine("""Text("${'$'}{tier.cashMultiplier}x")"""))
        assertFalse(isOffendingLine("""Text("%")"""))
        assertFalse(isOffendingLine("""canvas.drawText("Game Over", x, y)""")) // word-boundary anchor
        assertFalse(isOffendingLine("""Text(stringResource(R.string.foo))""")) // resource, not a literal
    }

    @Test
    fun `allowlist is intentionally empty at HEAD`() {
        assertEquals(emptySet<String>(), allowlist)
    }
}
