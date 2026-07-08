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
 * When a new locale ships, add its `values-xx/` dir to [locales]. When English adds a string without a
 * matching translation, THIS test goes red — that is the guard.
 */
class LocaleCompletenessTest {
    /** Non-default locales to check against the English default. Add a row per new `values-xx/`. */
    private val locales = listOf("es")

    private val defaultStrings = File("src/main/res/values/strings.xml")
    private val defaultPlurals = File("src/main/res/values/plurals.xml")

    private fun localeStrings(locale: String) = File("src/main/res/values-$locale/strings.xml")

    private fun localePlurals(locale: String) = File("src/main/res/values-$locale/plurals.xml")

    /**
     * Format-arg specifiers: positional `%N$x` (any index) or bare `%x`, with optional flag/width/precision
     * modifiers between the index and the conversion letter (e.g. `%2$02d`). The modifier class deliberately
     * omits the space flag so bare-percent prose like `DoT % MaxHP/sec` is not miscounted as an arg. Escaped
     * `%%` is stripped BEFORE scanning so a literal percent is never miscounted as an arg.
     */
    private val specifier = Regex("""%(\d+\$)?[-+#,0-9.]*[a-zA-Z]""")

    /** Sorted multiset of format specifiers in a resource value (empty for literal-only / formatted=false). */
    internal fun argSignature(value: String): List<String> =
        specifier
            .findAll(value.replace("%%", ""))
            .map { it.value }
            .toList()
            .sorted()

    // ---- guard-the-guard: the extractor itself can't silently drift ----

    @Test
    fun `arg-signature extractor handles positional args, escaped percent, and bare literal percent`() {
        assertEquals(listOf("%1\$s"), argSignature("%1\$s%%")) // stat_percent pattern
        assertEquals(listOf("%1\$d"), argSignature("+%1\$d%% Damage")) // card_effect pattern
        assertEquals(listOf("%1\$d", "%2\$02d"), argSignature("%1\$d:%2\$02d")) // postround_time_value — width modifier
        assertEquals(emptyList<String>(), argSignature("+2%")) // bare literal, no arg
        assertEquals(emptyList<String>(), argSignature("DoT % MaxHP/sec")) // uw_path_dot, bare % + space
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
        for (locale in locales) {
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
            assertTrue(
                fmtDrift.isEmpty(),
            ) { "values-$locale/strings.xml formatted=\"false\" parity drift in: $fmtDrift" }
        }
    }

    // ---- <plurals> parity (per (name, quantity) item) ----

    @Test
    fun `each locale mirrors English plural names, quantities, and per-item arg-signatures`() {
        assertFileExists(defaultPlurals)
        val en = parsePlurals(defaultPlurals)
        for (locale in locales) {
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

    private data class StringEntry(
        val text: String,
        val formattedFalse: Boolean,
    )

    private fun assertFileExists(file: File) =
        assertTrue(file.exists()) { "not found at ${file.absolutePath} (working dir = ${File(".").absolutePath})" }

    private fun parseStrings(file: File): Map<String, StringEntry> {
        val nodes =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(file)
                .getElementsByTagName("string")
        val out = LinkedHashMap<String, StringEntry>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            out[el.getAttribute("name")] = StringEntry(el.textContent, el.getAttribute("formatted") == "false")
        }
        return out
    }

    private fun parsePlurals(file: File): Map<String, Map<String, String>> {
        val plurals =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(file)
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

    private fun keyDiff(
        en: Set<String>,
        loc: Set<String>,
    ): String = "  missing in locale: ${(en - loc).sorted()}\n  extra in locale:   ${(loc - en).sorted()}"
}
