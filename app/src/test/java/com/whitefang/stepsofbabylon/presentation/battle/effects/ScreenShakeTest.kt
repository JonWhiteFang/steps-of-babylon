package com.whitefang.stepsofbabylon.presentation.battle.effects

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ScreenShakeTest {
    @Test
    fun `trigger sets intensity and duration`() {
        val shake = ScreenShake()
        shake.trigger(10f, 0.5f)
        assertTrue(shake.isActive)
    }

    @Test
    fun `update decays to zero`() {
        val shake = ScreenShake()
        shake.trigger(10f, 0.3f)
        shake.update(0.4f)
        assertFalse(shake.isActive)
        assertEquals(0f, shake.dx)
        assertEquals(0f, shake.dy)
    }

    @Test
    fun `stronger trigger overrides weaker`() {
        val shake = ScreenShake()
        shake.trigger(5f, 0.3f)
        shake.trigger(10f, 0.5f)
        assertTrue(shake.isActive)
    }

    @Test
    fun `weaker trigger does not override stronger`() {
        val shake = ScreenShake()
        shake.trigger(10f, 0.5f)
        shake.trigger(3f, 0.2f)
        // Still active with original duration
        shake.update(0.3f)
        assertTrue(shake.isActive)
    }

    @Test
    fun `reset clears all state`() {
        val shake = ScreenShake()
        shake.trigger(10f, 0.5f)
        shake.update(0.1f)
        shake.reset()
        assertFalse(shake.isActive)
        assertEquals(0f, shake.dx)
        assertEquals(0f, shake.dy)
    }

    @Test
    fun `produces non-zero offset during shake`() {
        val shake = ScreenShake()
        shake.trigger(10f, 0.5f)
        shake.update(0.05f)
        // At least one axis should have non-zero offset
        assertTrue(shake.dx != 0f || shake.dy != 0f)
    }
}
