package com.whitefang.stepsofbabylon.data.healthconnect

import com.whitefang.stepsofbabylon.data.anticheat.AntiCheatPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Duration
import java.time.Instant

class ActivityMinuteValidatorTest {
    private val antiCheatPrefs: AntiCheatPreferences = mock()
    private val validator = ActivityMinuteValidator(antiCheatPrefs)

    private fun session(
        type: Int,
        minutes: Int,
    ): ExerciseSessionInfo {
        val start = Instant.ofEpochSecond(1_000_000)
        return ExerciseSessionInfo(
            exerciseType = type,
            startTime = start,
            endTime = start.plus(Duration.ofMinutes(minutes.toLong())),
            durationMinutes = minutes,
        )
    }

    @Test
    fun `normal sessions pass through`() {
        val sessions = listOf(session(1, 30), session(2, 45))
        val result = validator.validate(sessions)
        assertEquals(2, result.size)
        assertEquals(30, result[0].durationMinutes)
        assertEquals(45, result[1].durationMinutes)
    }

    @Test
    fun `micro-sessions discarded`() {
        val sessions = listOf(session(1, 1), session(2, 30))
        val result = validator.validate(sessions)
        assertEquals(1, result.size)
        assertEquals(30, result[0].durationMinutes)
    }

    @Test
    fun `extreme duration truncated to 240 min`() {
        val sessions = listOf(session(1, 360))
        val result = validator.validate(sessions)
        assertEquals(1, result.size)
        assertEquals(240, result[0].durationMinutes)
    }

    @Test
    fun `more than 5 activity types rejects extras`() {
        val sessions = (1..7).map { session(it, 30) }
        val result = validator.validate(sessions)
        assertEquals(5, result.size)
        assertTrue(result.all { it.exerciseType in 1..5 })
    }

    @Test
    fun `mixed valid and invalid sessions filtered correctly`() {
        val sessions =
            listOf(
                session(1, 1), // micro — rejected
                session(2, 30), // valid
                session(3, 300), // truncated to 240
                session(4, 15), // valid
            )
        val result = validator.validate(sessions)
        assertEquals(3, result.size)
        assertEquals(30, result[0].durationMinutes)
        assertEquals(240, result[1].durationMinutes)
        assertEquals(15, result[2].durationMinutes)
    }
}
