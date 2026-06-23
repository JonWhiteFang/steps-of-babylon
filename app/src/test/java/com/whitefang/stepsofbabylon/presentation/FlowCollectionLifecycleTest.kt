package com.whitefang.stepsofbabylon.presentation

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * #235: every data-backed screen must collect its ViewModel `uiState` with
 * `collectAsStateWithLifecycle()`, NOT plain `collectAsState()`. Plain `collectAsState()` keeps an
 * active collector even while the screen is STOPPED (a dialog / permission Activity / Health Connect
 * screen in front), so `WhileSubscribed(5000)` never times out and the upstream Room Flows / combine
 * pipelines keep recomputing in the background — wasted CPU/battery (Labs has a 1s `while(true)`
 * ticker that would recompute every second while not visible).
 *
 * [BattleScreen] is the one DELIBERATE exception: it's a full-screen destination that owns its game
 * loop and is never backgrounded mid-round, and a lifecycle-aware collector would needlessly tear the
 * HUD state down on transient STOP. It is allowlisted here with that documented reason.
 *
 * Intentionally dependency-free + pure JVM (mirrors [com.whitefang.stepsofbabylon.architecture.DomainPurityTest]):
 * it only walks files and reads text — no Android, no Robolectric.
 */
class FlowCollectionLifecycleTest {
    /** Screens where plain `collectAsState()` on `uiState` is an accepted, documented choice. */
    private val allowed = setOf("BattleScreen.kt")

    @Test
    fun `data-backed screens collect uiState with lifecycle awareness`() {
        // Unit tests run with the :app module dir as the working directory (see DomainPurityTest).
        val presentationRoot = File("src/main/java/com/whitefang/stepsofbabylon/presentation")
        assertTrue(presentationRoot.isDirectory) {
            "presentation source root not found at ${presentationRoot.absolutePath} " +
                "(working dir = ${File(".").absolutePath})"
        }

        // A plain `…collectAsState()` call (no "WithLifecycle"). The negative lookahead keeps the
        // lifecycle-aware variant from matching.
        val plainCollect = Regex("""\.collectAsState\(\)""")

        var fileCount = 0
        val offenders = mutableListOf<String>()
        presentationRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name !in allowed }
            .forEach { file ->
                fileCount++
                file.readText().lineSequence().forEachIndexed { idx, line ->
                    if (plainCollect.containsMatchIn(line)) {
                        offenders += "${file.name}:${idx + 1}: ${line.trim()}"
                    }
                }
            }

        assertTrue(fileCount >= 10) {
            "Expected to discover presentation sources; walked only $fileCount .kt files"
        }

        assertTrue(offenders.isEmpty()) {
            "Plain collectAsState() found (#235 — use collectAsStateWithLifecycle() so backgrounded " +
                "screens stop recomputing; add to the documented allowlist only with a reason):\n" +
                offenders.sorted().joinToString("\n")
        }
    }
}
