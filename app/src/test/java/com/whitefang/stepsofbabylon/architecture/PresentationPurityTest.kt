package com.whitefang.stepsofbabylon.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Machine-enforces the presentation→data boundary (#219): a ViewModel (or any `presentation/` class)
 * must NOT inject or import a Room DAO, the `AppDatabase`, or a Room `@Entity` — those reads go through
 * repository **ports** so a schema/column change can't ripple into the UI. Complements
 * [DomainPurityTest] (which guards the domain side) and is the presentation analogue of #228.
 *
 * Dependency-free (no Konsist/Detekt): walks the `presentation/` source tree and scans `import` lines.
 * Targets ONLY `data.local.*Dao`, `data.local.AppDatabase`, `data.local.*Entity` — legitimate non-local
 * data-layer types presentation may use (e.g. `data.time.SystemTimeProvider`, `data.BiomePreferences`,
 * `data.onboarding.*`) are deliberately out of scope.
 *
 * Known limitation (shared with [DomainPurityTest]): only `import` lines are scanned, so an inline
 * fully-qualified reference would evade it. None exists today.
 *
 * **Allowlist:** `BattleViewModel.kt` may import `AppDatabase` — the end-of-round `withTransaction`
 * seam is a cross-repository atomicity boundary, a deliberate documented exception (ADR-0035). It may
 * NOT import any DAO or `@Entity`.
 */
class PresentationPurityTest {

    private val forbiddenPrefixes = listOf(
        "com.whitefang.stepsofbabylon.data.local.",
    )

    /** filename → the single data.local import it is allowed to keep. */
    private val allowlist = mapOf(
        "BattleViewModel.kt" to "com.whitefang.stepsofbabylon.data.local.AppDatabase",
    )

    @Test
    fun `presentation layer has no DAO, AppDatabase, or entity imports`() {
        val root = File("src/main/java/com/whitefang/stepsofbabylon/presentation")
        assertTrue(root.isDirectory) {
            "presentation source root not found at ${root.absolutePath} (working dir = ${File(".").absolutePath})"
        }

        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .map { it.trim() }
                    .filter { line ->
                        line.startsWith("import ") &&
                            forbiddenPrefixes.any { line.removePrefix("import ").startsWith(it) }
                    }
                    .map { line -> file.name to line.removePrefix("import ").trim() }
            }
            .filter { (fileName, import) -> allowlist[fileName] != import }
            .map { (fileName, import) -> "$fileName: import $import" }
            .toList()

        assertTrue(offenders.isEmpty()) {
            "presentation/ must not import Room DAOs / AppDatabase / entities (route through repository " +
                "ports — #219) — found:\n" + offenders.joinToString("\n")
        }
    }
}
