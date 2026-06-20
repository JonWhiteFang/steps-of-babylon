package com.whitefang.stepsofbabylon.presentation.battle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * #214: the pure battle live-region decision layer. Asserts the sealed [BattleAnnouncement] returned
 * for each (prev, next) transition — transition detection, health bucketing (no per-tick spam),
 * priority, and the pre-round guard. No Compose; the composable owns localization.
 */
class BattleAnnouncerTest {

    private fun snap(
        wave: Int = 1,
        phase: String = "SPAWNING",
        hp: Double = 100.0,
        maxHp: Double = 100.0,
        roundEnded: Boolean = false,
        battleError: Boolean = false,
    ) = BattleSnapshot(wave, phase, hp, maxHp, roundEnded, battleError)

    @Test
    fun `blank phase is pre-round and announces nothing`() {
        assertNull(battleAnnouncement(null, snap(phase = "")))
    }

    @Test
    fun `non-positive maxHp is pre-round and announces nothing`() {
        assertNull(battleAnnouncement(null, snap(maxHp = 0.0)))
    }

    @Test
    fun `first live snapshot announces the wave`() {
        assertEquals(BattleAnnouncement.Wave(1), battleAnnouncement(null, snap(wave = 1)))
    }

    @Test
    fun `wave change announces the new wave`() {
        assertEquals(BattleAnnouncement.Wave(2), battleAnnouncement(snap(wave = 1), snap(wave = 2)))
    }

    @Test
    fun `phase change announces the raw phase (composable localizes)`() {
        assertEquals(
            BattleAnnouncement.Phase("COOLDOWN"),
            battleAnnouncement(snap(phase = "SPAWNING"), snap(phase = "COOLDOWN")),
        )
    }

    @Test
    fun `health announces only on quarter-bracket crossings, not every tick`() {
        // Within the top bracket (100 → 80, both bucket 4 since 0.8*4=3.2→3 ... check boundaries):
        // 100/100=1.0→bucket4; 80/100=0.8→bucket3. So 100→80 DOES cross 4→3.
        // Use a within-bracket move: 79→76 (both 0.79/0.76 → bucket 3) → no announcement.
        assertNull(
            battleAnnouncement(snap(hp = 79.0), snap(hp = 76.0)),
            "small HP change inside a bracket must not announce",
        )
        // Crossing a bracket boundary announces once.
        val crossed = battleAnnouncement(snap(hp = 76.0), snap(hp = 49.0)) // bucket 3 → 1
        assertTrue(crossed is BattleAnnouncement.Health, "crossing a bracket announces health; was $crossed")
    }

    @Test
    fun `round over takes priority and announces the wave reached`() {
        assertEquals(
            BattleAnnouncement.RoundOver(7),
            battleAnnouncement(snap(wave = 7), snap(wave = 7, roundEnded = true)),
        )
    }

    @Test
    fun `battle error has top priority`() {
        // Even if wave + roundEnded also changed, error wins.
        assertEquals(
            BattleAnnouncement.Error,
            battleAnnouncement(snap(wave = 3), snap(wave = 4, roundEnded = true, battleError = true)),
        )
    }

    @Test
    fun `unchanged state announces nothing (no spam)`() {
        val s = snap(wave = 2, phase = "SPAWNING", hp = 60.0)
        assertNull(battleAnnouncement(s, s.copy()))
    }

    @Test
    fun `a sustained error does not re-announce`() {
        val errored = snap(battleError = true)
        // prev already errored → no re-announce
        assertNull(battleAnnouncement(errored, errored.copy()))
    }

    @Test
    fun `healthBucket guards non-positive maxHp`() {
        assertEquals(-1, healthBucket(50.0, 0.0))
        assertEquals(4, healthBucket(100.0, 100.0))
        assertEquals(0, healthBucket(10.0, 100.0))
    }
}
