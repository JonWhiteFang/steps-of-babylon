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
 * The import scan is complemented by an **inline fully-qualified reference** check (#220): a domain
 * file could reach the data layer without an `import` by writing `com.whitefang.stepsofbabylon.data.local.X`
 * inline in code — which the import scan would miss. The third test strips comments (so the existing
 * `…data.*` KDoc `[…]` doc-links are correctly ignored) and fails on any inline `…data.*` reference in
 * executable code. Together the two checks close the cycle the #227/#228/#229 cluster broke (GitHub #220),
 * so it stays build-enforced ahead of any future `domain` Gradle-module extraction (#27). A full
 * AST/Konsist check remains out of scope; the comment-strip is a pragmatic line-level approximation.
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

    /**
     * #220: an inline fully-qualified `com.whitefang.stepsofbabylon.data…` reference in domain CODE
     * (no `import` line) would reach the data layer while evading the import scan above. Strip comments
     * first so the legitimate `…data.*` KDoc `[…]` doc-links (BillingManager / PlayerRepository /
     * BillingProduct) are ignored, then fail on any data-layer FQN left in executable code.
     */
    @Test
    fun `domain layer has no inline fully-qualified data-layer references`() {
        val dataFqn = Regex("""\bcom\.whitefang\.stepsofbabylon\.data\b""")
        val offenders = domainSourceFiles().flatMap { file ->
            stripComments(file.readText()).lineSequence()
                .mapIndexedNotNull { idx, line ->
                    // Ignore the `import` lines — those are covered (and asserted) by the import scan.
                    if (!line.trim().startsWith("import ") && dataFqn.containsMatchIn(line)) {
                        "${file.name}:${idx + 1}: ${line.trim()}"
                    } else null
                }
        }.toList()

        assertTrue(offenders.isEmpty()) {
            "domain/ must have zero inline fully-qualified data-layer references (#220) — found:\n" +
                offenders.joinToString("\n")
        }
    }

    private fun domainSourceFiles(): List<File> {
        val domainRoot = File("src/main/java/com/whitefang/stepsofbabylon/domain")
        assertTrue(domainRoot.isDirectory) {
            "domain source root not found at ${domainRoot.absolutePath} (working dir = ${File(".").absolutePath})"
        }
        return domainRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * Removes Kotlin `/* … */` (incl. KDoc `/** … */`) block comments and `//` line comments so the
     * inline-FQN scan sees executable code only. Deliberately simple (no string-literal awareness): a
     * data-layer FQN never legitimately appears inside a domain string literal, so the approximation is
     * safe for this guard.
     */
    private fun stripComments(source: String): String {
        // Replace each block comment with the SAME number of newlines it spanned, so line numbers in
        // the offender report stay aligned with the original file.
        val noBlock = source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)) { match ->
            "\n".repeat(match.value.count { it == '\n' })
        }
        return noBlock.lines().joinToString("\n") { it.substringBefore("//") }
    }
}
