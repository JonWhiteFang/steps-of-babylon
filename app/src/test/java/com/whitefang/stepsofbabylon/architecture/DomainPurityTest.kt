package com.whitefang.stepsofbabylon.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Machine-enforces the core architecture invariant (`docs/agent/CONSTRAINTS.md`,
 * `START_HERE.md`): the `domain/` layer is pure Kotlin with **zero Android imports**.
 * Closes the "no machine-enforced domain-purity rule" gap flagged for GitHub #27.
 *
 * Intentionally dependency-free (no Konsist/Detekt): it walks the domain source tree and
 * scans `import` lines, so it adds no new Gradle dependency and runs inside the existing
 * `testDebugUnitTest` suite. A violation (e.g. someone adds `import androidx.room.Entity`
 * to a domain file) fails the build with the offending file + import listed.
 *
 * Forbidden prefixes cover the framework (`android.`, `androidx.`) and the two third-party
 * SDK namespaces that must never leak into domain either (`com.android.` — Play Billing;
 * `com.google.android.` — Mobile Ads / GMS).
 */
class DomainPurityTest {

    private val forbiddenPrefixes = listOf("android.", "androidx.", "com.android.", "com.google.android.")

    @Test
    fun `domain layer has zero Android imports`() {
        // Unit tests run with the :app module dir as the working directory.
        val domainRoot = File("src/main/java/com/whitefang/stepsofbabylon/domain")
        // Fail loud rather than silently pass on an empty walk if the path ever moves.
        assertTrue(domainRoot.isDirectory) {
            "domain source root not found at ${domainRoot.absolutePath} (working dir = ${File(".").absolutePath})"
        }

        val offenders = domainRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .map { it.trim() }
                    .filter { line ->
                        line.startsWith("import ") &&
                            forbiddenPrefixes.any { line.removePrefix("import ").startsWith(it) }
                    }
                    .map { "${file.name}: $it" }
            }
            .toList()

        assertTrue(offenders.isEmpty()) {
            "domain/ must have zero Android imports — found:\n" + offenders.joinToString("\n")
        }
    }
}
