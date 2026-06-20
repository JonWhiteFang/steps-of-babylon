package com.whitefang.stepsofbabylon.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Machine-enforces the core architecture invariant (`docs/agent/CONSTRAINTS.md`,
 * `START_HERE.md`): the `domain/` layer is pure Kotlin with **zero Android imports AND zero
 * data-layer imports** — i.e. the Clean-Architecture dependency rule (`presentation → domain ← data`)
 * holds at the dependency-DIRECTION level, not just the Android-framework level.
 * Closes the "no machine-enforced domain-purity rule" gap flagged for GitHub #27, and #228 (the
 * test previously scanned only Android prefixes, so the domain→data violation #227 fixed sailed
 * through — documented assurance that the architecture was intact when it was not).
 *
 * Intentionally dependency-free (no Konsist/Detekt): it walks the domain source tree and
 * scans `import` lines, so it adds no new Gradle dependency and runs inside the existing
 * `testDebugUnitTest` suite. A violation (e.g. someone adds `import androidx.room.Entity` or
 * `import com.whitefang.stepsofbabylon.data.local.SomeDao` to a domain file) fails the build with
 * the offending file + import listed.
 *
 * Known limitation (#228): the scan matches only `import` lines, so an inline fully-qualified
 * reference (`com.whitefang.stepsofbabylon.data.local.X` with no import) would evade it. No domain
 * file does this today; the existing `…data.*` strings in domain are all KDoc `[…]` doc-links, which
 * are not `import` lines and so are correctly ignored. A full AST/Konsist check is out of scope.
 *
 * Forbidden prefixes cover the framework (`android.`, `androidx.`), the two third-party SDK
 * namespaces that must never leak into domain (`com.android.` — Play Billing;
 * `com.google.android.` — Mobile Ads / GMS), and the project's own **data layer**
 * (`com.whitefang.stepsofbabylon.data`).
 */
class DomainPurityTest {

    private val forbiddenPrefixes = listOf(
        "android.", "androidx.", "com.android.", "com.google.android.",
        "com.whitefang.stepsofbabylon.data",
    )

    /** Domain must also stay DI-framework-agnostic — no Dagger/Hilt or javax.inject in domain code. */
    private val forbiddenDiPrefixes = listOf("dagger.", "javax.inject.")

    private fun domainImports(): List<Pair<String, String>> {
        // Unit tests run with the :app module dir as the working directory.
        val domainRoot = File("src/main/java/com/whitefang/stepsofbabylon/domain")
        // Fail loud rather than silently pass on an empty walk if the path ever moves.
        assertTrue(domainRoot.isDirectory) {
            "domain source root not found at ${domainRoot.absolutePath} (working dir = ${File(".").absolutePath})"
        }
        return domainRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .map { it.trim() }
                    .filter { it.startsWith("import ") }
                    .map { file.name to it.removePrefix("import ").trim() }
            }
            .toList()
    }

    @Test
    fun `domain layer has no Android and no data-layer imports`() {
        val offenders = domainImports()
            .filter { (_, import) -> forbiddenPrefixes.any { import.startsWith(it) } }
            .map { (file, import) -> "$file: import $import" }

        assertTrue(offenders.isEmpty()) {
            "domain/ must have zero Android AND zero data-layer imports — found:\n" + offenders.joinToString("\n")
        }
    }

    @Test
    fun `domain layer is DI-framework-agnostic`() {
        val offenders = domainImports()
            .filter { (_, import) -> forbiddenDiPrefixes.any { import.startsWith(it) } }
            .map { (file, import) -> "$file: import $import" }

        assertTrue(offenders.isEmpty()) {
            "domain/ must not import a DI framework (Dagger/Hilt/javax.inject) — found:\n" +
                offenders.joinToString("\n")
        }
    }
}
