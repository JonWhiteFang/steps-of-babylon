package com.whitefang.stepsofbabylon.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Machine-enforces the #1 hard design rule (`CLAUDE.md` Key Domain Concepts; ADR-0003):
 * **"Steps are never generated in-game"** — the only sanctioned exception is the bounded
 * battle-step reward (`AwardBattleSteps`, `DAILY_BATTLE_STEP_CAP`), which terminates at
 * `DailyStepDao.creditBattleStepsAtomic` (on the allowlist below). Closes GitHub #371 (`ai-3`).
 *
 * The invariant boundary is the set of `PlayerProfileDao` methods that can raise
 * `currentStepBalance`. There are THREE writers (verified against `PlayerProfileDao.kt`):
 *  - `adjustStepBalance(delta)`   — relative credit/debit (`SET … MAX(0, … + :delta)`)
 *  - `updateStepBalance(balance)` — absolute setter (`SET currentStepBalance = :balance`); ZERO
 *                                   production callers today — a latent landmine.
 *  - `upsert(PlayerProfileEntity)` — full-row write incl. the balance.
 * `adjustStepBalanceIfSufficient(cost)` is the guarded SPEND primitive (a debit) — and note the
 * substring `.adjustStepBalance(` is NOT contained in `.adjustStepBalanceIfSufficient(` (the `I`
 * breaks the token), so no special-casing is needed to avoid a false collision.
 *
 * Dependency-free (mirrors `DomainPurityTest`): walks `src/main`, comment-strips, scans lines. Any
 * NEW credit site fails the build with the offending file. The pure line-matching predicates are
 * isolated from the file walk so they are unit-testable directly (see the negative-fixture test).
 */
class StepCreditAllowlistTest {
    private val srcMain = File("src/main/java/com/whitefang/stepsofbabylon")

    private fun kotlinFiles(): List<File> {
        assertTrue(srcMain.isDirectory) {
            "src/main root not found at ${srcMain.absolutePath} (working dir = ${File(".").absolutePath})"
        }
        return srcMain.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * Removes `/* … */` (incl. KDoc) block comments and `//` line comments so scans see executable
     * code only. Same pragmatic approach as `DomainPurityTest.stripComments` (no string-literal
     * awareness — a credit call never legitimately hides inside a string literal).
     */
    private fun stripComments(source: String): String {
        val noBlock =
            source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)) { m ->
                "\n".repeat(m.value.count { it == '\n' })
            }
        return noBlock.lines().joinToString("\n") { it.substringBefore("//") }
    }

    /** Files (by name) that contain a qualified `.addSteps(` CALL. */
    internal fun addStepsCallerFiles(files: List<File>): Set<String> =
        files
            .filter { stripComments(it.readText()).contains(".addSteps(") }
            .map { it.name }
            .toSet()

    /**
     * Files containing a qualified positive-credit `.adjustStepBalance(` CALL — i.e. NOT a negated
     * debit `.adjustStepBalance(-…)`. The leading `.` excludes the interface DECLARATION in
     * `PlayerProfileDao.kt` (`suspend fun adjustStepBalance(delta: Long)`), and the `-` check
     * excludes `PlayerRepositoryImpl.spendSteps` (`dao.adjustStepBalance(-amount)`).
     */
    internal fun positiveAdjustStepBalanceFiles(files: List<File>): Set<String> =
        files
            .filter { file ->
                stripComments(file.readText())
                    .lineSequence()
                    .any { line ->
                        val idx = line.indexOf(".adjustStepBalance(")
                        if (idx < 0) {
                            false
                        } else {
                            val argStart = idx + ".adjustStepBalance(".length
                            line.getOrNull(argStart) != '-'
                        }
                    }
            }.map { it.name }
            .toSet()

    /** Files with a qualified `.updateStepBalance(` CALL (the absolute setter — zero callers today). */
    internal fun updateStepBalanceCallerFiles(files: List<File>): Set<String> =
        files
            .filter { stripComments(it.readText()).contains(".updateStepBalance(") }
            .map { it.name }
            .toSet()

    /**
     * Files that CONSTRUCT a `PlayerProfileEntity(` (which `upsert` writes as a full row). Excludes
     * `PlayerProfileEntity.kt` itself — its `data class PlayerProfileEntity(` DECLARATION contains
     * the same substring but is not a construction site.
     */
    internal fun playerProfileEntityConstructionFiles(files: List<File>): Set<String> =
        files
            .filter {
                it.name != "PlayerProfileEntity.kt" && stripComments(it.readText()).contains("PlayerProfileEntity(")
            }.map { it.name }
            .toSet()

    @Test
    fun `addSteps is called only from the sanctioned use cases`() {
        val expected = setOf("ClaimSupplyDrop.kt", "StepCrossValidator.kt", "DailyStepManager.kt")
        val actual = addStepsCallerFiles(kotlinFiles())
        assertEquals(expected, actual) {
            "Unexpected .addSteps( caller set. Steps may only be credited from the sanctioned " +
                "sites (ADR-0003). Expected=$expected Actual=$actual. A new credit site is a " +
                "'Steps generated in-game' invariant break — see CLAUDE.md Key Domain Concepts."
        }
    }

    @Test
    fun `positive adjustStepBalance calls are confined to the sanctioned wallet-credit sites`() {
        val expected = setOf("PlayerRepositoryImpl.kt", "DailyStepDao.kt")
        val actual = positiveAdjustStepBalanceFiles(kotlinFiles())
        assertEquals(expected, actual) {
            "Unexpected positive .adjustStepBalance( site. Only PlayerRepositoryImpl.addSteps and " +
                "DailyStepDao.creditBattleStepsAtomic may credit Steps (ADR-0003). " +
                "Expected=$expected Actual=$actual."
        }
    }

    @Test
    fun `absolute step-balance setter has no production caller`() {
        val actual = updateStepBalanceCallerFiles(kotlinFiles())
        assertTrue(actual.isEmpty()) {
            "PlayerProfileDao.updateStepBalance(balance) is an ABSOLUTE step-balance setter with no " +
                "sanctioned caller — a new caller (e.g. updateStepBalance(current + reward)) would " +
                "generate Steps in-game (ADR-0003). Offending files: $actual."
        }
    }

    @Test
    fun `PlayerProfileEntity construction is confined to the profile-creation seam`() {
        val expected = setOf("PlayerRepositoryImpl.kt")
        val actual = playerProfileEntityConstructionFiles(kotlinFiles())
        assertEquals(expected, actual) {
            "PlayerProfileEntity is constructed outside the profile-creation seam. An upsert of an " +
                "entity with a raised currentStepBalance would generate Steps (ADR-0003). " +
                "Expected=$expected Actual=$actual."
        }
    }

    @Test
    fun `only sanctioned PlayerProfileDao queries write currentStepBalance`() {
        val daoFile =
            kotlinFiles().firstOrNull { it.name == "PlayerProfileDao.kt" }
                ?: error("PlayerProfileDao.kt not found under $srcMain")
        val stripped = stripComments(daoFile.readText())
        val writeFragmentCount =
            Regex("""currentStepBalance\s*=""").findAll(stripped).count()
        assertEquals(3, writeFragmentCount) {
            "PlayerProfileDao has an unexpected number of `currentStepBalance =` write sites " +
                "($writeFragmentCount, expected 3: adjustStepBalance, adjustStepBalanceIfSufficient, " +
                "updateStepBalance). A NEW query writing the balance must be reviewed against the " +
                "'Steps never generated in-game' invariant (ADR-0003) before this count is updated."
        }
    }

    @Test
    fun `matchers flag a rogue credit site (negative fixture)`(
        @TempDir tmp: File,
    ) {
        val rogueAddSteps =
            File(tmp, "RogueReward.kt").apply {
                writeText("class RogueReward { suspend fun go() { repo.addSteps(500L) } }")
            }
        val roguePositiveAdjust =
            File(tmp, "RogueDao.kt").apply {
                writeText("class RogueDao { suspend fun go() { dao.adjustStepBalance(500L) } }")
            }
        val rogueUpdate =
            File(tmp, "RogueSetter.kt").apply {
                writeText("class RogueSetter { suspend fun go() { dao.updateStepBalance(999L) } }")
            }
        val files = listOf(rogueAddSteps, roguePositiveAdjust, rogueUpdate)

        assertTrue("RogueReward.kt" in addStepsCallerFiles(files)) {
            "matcher failed to flag a rogue .addSteps( site — the guard would be a no-op"
        }
        assertTrue("RogueDao.kt" in positiveAdjustStepBalanceFiles(files)) {
            "matcher failed to flag a rogue positive .adjustStepBalance( site"
        }
        assertTrue("RogueSetter.kt" in updateStepBalanceCallerFiles(files)) {
            "matcher failed to flag a rogue .updateStepBalance( site"
        }
        val debit =
            File(tmp, "Spend.kt").apply {
                writeText("class Spend { suspend fun go() { dao.adjustStepBalance(-500L) } }")
            }
        assertTrue("Spend.kt" !in positiveAdjustStepBalanceFiles(listOf(debit))) {
            "negated .adjustStepBalance(-…) debit was wrongly flagged as a positive credit"
        }
    }
}
