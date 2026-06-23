package com.whitefang.stepsofbabylon.data

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * #247: [DataDeletionManager.PREFS_NAMES] is a hand-maintained list of every SharedPreferences file
 * the "Delete All Data" wipe must clear. It drifted — `onboarding_prefs` (#24) and `haptics_prefs`
 * (#162) were added to the app after the list and silently escaped the wipe, leaving a confusing
 * post-wipe state (the first-run tutorial never re-shows) and an incomplete "full local data wipe"
 * privacy claim.
 *
 * This guard walks the whole main source tree, discovers EVERY `getSharedPreferences(name, …)` call
 * site — resolving both string-literal args (`getSharedPreferences("x", …)`) and `const val X = "x"`
 * constant args — and asserts each resolved prefs-file name appears in [DataDeletionManager.PREFS_NAMES].
 * A newly-added prefs file therefore fails the build until it is wired into the wipe list, so the
 * list can no longer drift out from under the deletion contract.
 *
 * Intentionally dependency-free + pure JVM (mirrors [com.whitefang.stepsofbabylon.architecture.DomainPurityTest]):
 * it only walks files and reads a `listOf` constant — no Android, no Robolectric.
 */
class DataDeletionPrefsCoverageTest {
    @Test
    fun `every getSharedPreferences call site is covered by PREFS_NAMES`() {
        // Unit tests run with the :app module dir as the working directory (see DomainPurityTest).
        val mainRoot = File("src/main/java/com/whitefang/stepsofbabylon")
        assertTrue(mainRoot.isDirectory) {
            "main source root not found at ${mainRoot.absolutePath} (working dir = ${File(".").absolutePath})"
        }

        // getSharedPreferences("literal", …)
        val literalCall = Regex("""getSharedPreferences\(\s*"([^"]+)"""")
        // getSharedPreferences(IDENT, …) where IDENT resolves to a `const val IDENT = "value"`
        val identCall = Regex("""getSharedPreferences\(\s*([A-Za-z_][A-Za-z0-9_]*)""")
        val constDecl = Regex("""const\s+val\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*"([^"]+)"""")

        val referenced = mutableSetOf<String>()
        // #247 review fix: any getSharedPreferences(IDENT) whose IDENT we CANNOT resolve to a prefs
        // name (a const defined in another file, a non-const val, a qualified Foo.PREFS reference, a
        // method param, …) is recorded here and FAILS the test — rather than being silently dropped,
        // which would let an unwiped prefs file slip past the guard (the exact #247 failure mode).
        val unresolved = mutableListOf<String>()
        mainRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            // DataDeletionManager itself calls getSharedPreferences(name) with a loop variable, not a
            // prefs-file name — skip it so `name` isn't mistaken for an (unresolved) constant.
            .filter { it.name != "DataDeletionManager.kt" }
            .forEach { file ->
                val text = file.readText()
                val consts =
                    constDecl
                        .findAll(text)
                        .associate { it.groupValues[1] to it.groupValues[2] }
                literalCall.findAll(text).forEach { referenced += it.groupValues[1] }
                identCall.findAll(text).forEach { m ->
                    val ident = m.groupValues[1]
                    val resolved = consts[ident]
                    if (resolved != null) {
                        referenced += resolved
                    } else {
                        unresolved += "${file.name}: getSharedPreferences($ident, …)"
                    }
                }
            }

        // A getSharedPreferences arg the scan can't resolve must FAIL (not silently pass): either
        // make it a string literal / same-file const so the guard can see the prefs name, or the
        // wipe-coverage promise is unverifiable for that call site.
        assertTrue(unresolved.isEmpty()) {
            "DataDeletionPrefsCoverageTest could not resolve these getSharedPreferences args to a " +
                "prefs-file name, so it can't verify they're wiped (#247). Use a string literal or a " +
                "same-file `const val`, or extend this test's resolver:\n" +
                unresolved.sorted().joinToString("\n")
        }

        // Sanity: the walk must actually find call sites, else a path move would silently pass.
        assertTrue(referenced.size >= 12) {
            "Expected to discover the app's SharedPreferences files; found only $referenced"
        }

        val uncovered = referenced - DataDeletionManager.PREFS_NAMES.toSet()
        assertTrue(uncovered.isEmpty()) {
            "These SharedPreferences files are not wiped by DataDeletionManager.PREFS_NAMES " +
                "(#247 — add them so 'Delete All Data' is a complete local wipe):\n" +
                uncovered.sorted().joinToString("\n")
        }
    }
}
