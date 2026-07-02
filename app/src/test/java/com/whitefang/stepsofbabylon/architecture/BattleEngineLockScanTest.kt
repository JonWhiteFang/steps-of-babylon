package com.whitefang.stepsofbabylon.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Machine-enforces the battle-engine invariant that the engine's collaborators **hold no monitor of
 * their own** (`CLAUDE.md` Battle Renderer note): they run inside `GameEngine`'s single held
 * `entitiesLock`, so a collaborator that declares its own `synchronized`/`ReentrantLock`/monitor
 * field reintroduces the lock-order / nested-lock hazard the #118/#191 fixes closed. Closes the
 * fallback path of GitHub #372 (`ai-2`); the detekt custom-rule alternative is deferred (ADR-0038).
 *
 * Scoped to the explicit collaborator allowlist and EXCLUDES:
 *  - `GameEngine.kt` — the SOLE sanctioned `entitiesLock` owner (it legitimately declares + uses it).
 *  - `BattleHosts.kt` — its `synchronized(entitiesLock)` reference lives in a KDoc (also handled by
 *    comment-stripping, but excluded by name for clarity).
 * Comment-stripped (reuses the `DomainPurityTest`/`StepCreditAllowlistTest` approach) so a KDoc
 * reference to a lock never false-fires.
 */
class BattleEngineLockScanTest {
    private val engineDir =
        File("src/main/java/com/whitefang/stepsofbabylon/presentation/battle/engine")

    private val collaborators =
        setOf("UWController.kt", "CombatResolver.kt", "BuffTickers.kt", "BattleRenderer.kt")

    private val monitorPatterns =
        listOf("synchronized(", "ReentrantLock", "= Any()", "= Object()")

    private fun stripComments(source: String): String {
        val noBlock =
            source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)) { m ->
                "\n".repeat(m.value.count { it == '\n' })
            }
        return noBlock.lines().joinToString("\n") { it.substringBefore("//") }
    }

    @Test
    fun `engine collaborators declare no monitor of their own`() {
        assertTrue(engineDir.isDirectory) {
            "engine dir not found at ${engineDir.absolutePath} (working dir = ${File(".").absolutePath})"
        }
        val files = engineDir.walkTopDown().filter { it.isFile && it.name in collaborators }.toList()
        assertTrue(files.map { it.name }.toSet() == collaborators) {
            "Collaborator file set drifted. Expected=$collaborators Found=${files.map { it.name }}. " +
                "If a collaborator was renamed/added, update this allowlist (and re-confirm it holds " +
                "no monitor)."
        }
        val offenders =
            files.flatMap { file ->
                stripComments(file.readText())
                    .lineSequence()
                    .mapIndexedNotNull { idx, line ->
                        if (monitorPatterns.any {
                                line.contains(
                                    it,
                                )
                            }
                        ) {
                            "${file.name}:${idx + 1}: ${line.trim()}"
                        } else {
                            null
                        }
                    }
            }
        assertTrue(offenders.isEmpty()) {
            "A battle-engine collaborator declares its own monitor — collaborators must run inside " +
                "GameEngine's held entitiesLock, NOT take a lock of their own (CLAUDE.md Battle " +
                "Renderer note; #118/#191). Offenders:\n" + offenders.joinToString("\n")
        }
    }
}
