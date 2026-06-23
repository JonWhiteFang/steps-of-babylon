package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.PlayerWallet
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CanAffordUpgradeTest {
    private val sut = CanAffordUpgrade()

    @Test
    fun `sufficient balance returns true`() {
        val wallet = PlayerWallet(stepBalance = 10_000)
        assertTrue(sut(wallet, UpgradeType.DAMAGE, 0))
    }

    @Test
    fun `insufficient balance returns false`() {
        val wallet = PlayerWallet(stepBalance = 1)
        assertFalse(sut(wallet, UpgradeType.DAMAGE, 0))
    }

    @Test
    fun `exact balance returns true`() {
        val wallet = PlayerWallet(stepBalance = 50) // DAMAGE baseCost = 50
        assertTrue(sut(wallet, UpgradeType.DAMAGE, 0))
    }

    @Test
    fun `zero steps cannot afford any upgrade`() {
        val wallet = PlayerWallet(stepBalance = 0)
        UpgradeType.entries.forEach { type ->
            assertFalse(sut(wallet, type, 0), "$type should not be affordable with 0 steps")
        }
    }
}
