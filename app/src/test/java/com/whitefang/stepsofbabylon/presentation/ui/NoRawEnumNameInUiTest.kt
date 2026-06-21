package com.whitefang.stepsofbabylon.presentation.ui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * #225: regression guard against raw SCREAMING_CASE enum names leaking into UI text. The fix
 * replaced six `enum.name.replace('_', ' ')` sites (which surface `HANGING GARDENS` / `IRON SKIN`
 * — placeholder-looking, inconsistent with the screens that already use [toDisplayName]) with the
 * shared [String.toDisplayName] helper (`Hanging Gardens`).
 *
 * This walks the whole presentation source tree and fails the build if any `.name.replace(` site
 * reappears, so the inconsistency can't drift back in. The single source of truth for enum-name
 * formatting in the UI is [toDisplayName]; `.name.replace('_', ' ')` is its low-quality predecessor.
 *
 * Intentionally dependency-free + pure JVM (mirrors [com.whitefang.stepsofbabylon.architecture.DomainPurityTest]
 * and [com.whitefang.stepsofbabylon.data.DataDeletionPrefsCoverageTest]): it only walks files and
 * reads text — no Android, no Robolectric.
 */
class NoRawEnumNameInUiTest {

    @Test
    fun `no presentation source surfaces a raw enum name via name dot replace`() {
        // Unit tests run with the :app module dir as the working directory (see DomainPurityTest).
        val presentationRoot = File("src/main/java/com/whitefang/stepsofbabylon/presentation")
        assertTrue(presentationRoot.isDirectory) {
            "presentation source root not found at ${presentationRoot.absolutePath} " +
                "(working dir = ${File(".").absolutePath})"
        }

        // `enum.name.replace("_", " ")` / `.name.replace('_', ' ')` — the low-quality formatter the
        // #225 fix removed in favour of toDisplayName(). Whitespace-tolerant; matches both literal forms.
        val rawNameReplace = Regex("""\.name\.replace\(""")

        var fileCount = 0
        val offenders = mutableListOf<String>()
        presentationRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                fileCount++
                file.readText().lineSequence().forEachIndexed { idx, line ->
                    if (rawNameReplace.containsMatchIn(line)) {
                        offenders += "${file.name}:${idx + 1}: ${line.trim()}"
                    }
                }
            }

        // Sanity: the walk must actually find sources, else a path move would silently pass.
        assertTrue(fileCount >= 10) {
            "Expected to discover presentation sources; walked only $fileCount .kt files"
        }

        assertTrue(offenders.isEmpty()) {
            "Raw enum-name formatting found in presentation UI (#225 — use String.toDisplayName() " +
                "instead of .name.replace('_', ' ')):\n" + offenders.sorted().joinToString("\n")
        }
    }

    /**
     * #260 (IC-9): widen the #225 guard to the OTHER low-quality enum-name de-casing transforms the
     * wave removed — `enum.name.take(n)` (the raw DayOfWeek/abbreviation form) and `enum.name.lowercase(…)`
     * — so they can't drift back into the UI. These are routed through localized `@StringRes` labels
     * / the `Strings` seam now.
     *
     * Deliberately NOT banned (would false-positive — see the wave spec/plan IC-9 note): a *bare*
     * `Text(x.name)` is syntactically indistinguishable from surfacing a legitimate content `.name`
     * field (e.g. `Text(cosmetic.name)` renders the free-text cosmetic display name, not an enum), and
     * a bare `.replace("_", " ")` is used on out-of-scope String activity keys (StatsScreen). The
     * fixed bare-enum-name sites (PackTier/UpgradeCategory/CosmeticCategory) are regression-covered by
     * [EnumLabelResTest] + the screens' rendered-text tests, not by this source scan.
     */
    @Test
    fun `no presentation source de-cases an enum name via name dot take or lowercase`() {
        val presentationRoot = File("src/main/java/com/whitefang/stepsofbabylon/presentation")
        assertTrue(presentationRoot.isDirectory) { "presentation source root not found at ${presentationRoot.absolutePath}" }

        val rawNameTransform = Regex("""\.name\.take\(|\.name\.lowercase\(""")
        var fileCount = 0
        val offenders = mutableListOf<String>()
        presentationRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                fileCount++
                file.readText().lineSequence().forEachIndexed { idx, line ->
                    if (rawNameTransform.containsMatchIn(line)) offenders += "${file.name}:${idx + 1}: ${line.trim()}"
                }
            }

        assertTrue(fileCount >= 10) { "Expected to discover presentation sources; walked only $fileCount .kt files" }
        assertTrue(offenders.isEmpty()) {
            "Raw enum-name de-casing found in presentation UI (#260 — use a @StringRes label / the " +
                "Strings seam, not .name.take()/.name.lowercase()):\n" + offenders.sorted().joinToString("\n")
        }
    }
}
