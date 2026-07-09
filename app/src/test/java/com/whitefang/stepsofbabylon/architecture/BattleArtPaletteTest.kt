package com.whitefang.stepsofbabylon.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * #426 (#391 free-lane): forward guard that keeps the battle **art** palette sourced from the single
 * source of truth `presentation/battle/biome/BattlePalette.kt` (introduced by #421/C1, adopted by
 * #422/C2 + #423/C3). It fails the build if a NEW raw `0x…` ARGB literal lands in an art-colour
 * consumer file instead of being named in `BattlePalette`.
 *
 * Dependency-free (mirrors [StepCreditAllowlistTest] / [ComposeHardcodedStringTest]): walks the scoped
 * files, comment-strips, and scans lines with a pure predicate.
 *
 * ## Scope — the art-colour CONSUMER files only
 * The three files that used to hold anonymous art-colour literals. `BattlePalette.kt` itself is the
 * source of truth and is deliberately NOT scanned (that is where the named literals live).
 *
 * ## What counts as an offender
 * A source line (outside comments) in a [scannedFiles] file that contains a `0x…` hex literal of 6–8
 * hex digits (an ARGB/RGB colour) AND is not on the [functionalColorAllowlist].
 *
 * ## The allowlist = FUNCTIONAL-feedback colours (deliberate — see style-bible §7)
 * HP-bar ratio thresholds, the armor charge stroke, the HP-bar background, the ziggurat origin marker,
 * and the attack-range circle alphas encode **gameplay state, not art direction**. They are UI signal,
 * kept inline at their consumption site, and are excluded from `BattlePalette` BY DESIGN. A green run
 * means "no NEW un-sourced ART colour", NOT "zero hex literals exist". If you add a genuinely new
 * functional-signal colour, add it here WITH a justifying comment; if you add a new ART colour, put it
 * in `BattlePalette` instead.
 */
class BattleArtPaletteTest {
    private val battleRoot = File("src/main/java/com/whitefang/stepsofbabylon/presentation/battle")

    /** The art-colour consumer files. `BattlePalette.kt` (the source of truth) is intentionally excluded. */
    private val scannedFiles =
        listOf(
            File(battleRoot, "biome/BiomeTheme.kt"),
            File(battleRoot, "entities/EnemyEntity.kt"),
            File(battleRoot, "entities/ZigguratEntity.kt"),
        )

    /**
     * Intentional FUNCTIONAL-feedback colour literals allowed to stay inline (style-bible §7).
     * Keyed by `"<FileName.kt>:<hex literal>"` — the functional hex values are all distinct, so a
     * per-file hex key is precise and stays short. Each entry is a UI-signal colour, NOT art.
     */
    private val functionalColorAllowlist: Set<String> =
        setOf(
            // EnemyEntity — HP-bar ratio thresholds (green/yellow/red = health state).
            "EnemyEntity.kt:0xFF4CAF50",
            "EnemyEntity.kt:0xFFFFEB3B",
            "EnemyEntity.kt:0xFFF44336",
            // EnemyEntity — armor charge stroke (translucent cyan; presence-of-armor signal).
            "EnemyEntity.kt:0x5500BCD4",
            // EnemyEntity — HP-bar background frame.
            "EnemyEntity.kt:0xFF2A1A10",
            // ZigguratEntity — origin firing marker (gold dot; targeting signal).
            "ZigguratEntity.kt:0xFFFFD700",
            // ZigguratEntity — attack-range circle fill + stroke alphas (range indicator).
            "ZigguratEntity.kt:0x22FFFFFF",
            "ZigguratEntity.kt:0x44FFFFFF",
        )

    /** Matches an ARGB/RGB hex colour literal (6–8 hex digits). */
    private val hexColor = Regex("""0x[0-9A-Fa-f]{6,8}\b""")

    private fun stripComments(source: String): String {
        val noBlock =
            source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)) { m ->
                "\n".repeat(m.value.count { it == '\n' })
            }
        return noBlock.lines().joinToString("\n") { it.substringBefore("//") }
    }

    @Test
    fun `art-colour consumers hold no un-sourced hex colour literal`() {
        val offenders = mutableListOf<String>()
        scannedFiles.forEach { file ->
            assertTrue(file.isFile) { "scanned file not found: ${file.absolutePath}" }
            stripComments(file.readText()).lines().forEach { rawLine ->
                hexColor.findAll(rawLine).forEach { match ->
                    val key = "${file.name}:${match.value}"
                    if (key !in functionalColorAllowlist) offenders.add("$key   (line: ${rawLine.trim()})")
                }
            }
        }
        assertTrue(offenders.isEmpty()) {
            "Found un-sourced ART colour literal(s) in battle art consumers. Move ART colours into " +
                "BattlePalette.kt; if a literal is genuine FUNCTIONAL-signal, add it to " +
                "functionalColorAllowlist with a justifying comment:\n  " + offenders.joinToString("\n  ")
        }
    }

    /**
     * Proves the guard is not a no-op: a fresh un-allowlisted ART hex literal in a scanned file must be
     * flagged. Runs the same predicate over a synthetic line (does not touch real source).
     */
    @Test
    fun `predicate flags a new un-sourced art colour`() {
        val syntheticLine = "        val skyColorTop = 0xFF123456.toInt()"
        val flagged =
            hexColor.findAll(syntheticLine).any { match ->
                "BiomeTheme.kt:${match.value}" !in functionalColorAllowlist
            }
        assertTrue(flagged) { "guard should flag a new un-sourced art colour literal" }
    }
}
