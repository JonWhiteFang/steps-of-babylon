package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.domain.model.PlayerWallet
import com.whitefang.stepsofbabylon.domain.model.UpgradeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class QuickInvestTest {
    private val sut = QuickInvest()

    @Test
    fun `single affordable upgrade is returned`() {
        val upgrades = mapOf(UpgradeType.DAMAGE to 0)
        val wallet = PlayerWallet(stepBalance = 50)
        assertEquals(UpgradeType.DAMAGE, sut(upgrades, wallet))
    }

    @Test
    fun `returns cheapest among multiple affordable`() {
        // CASH_BONUS baseCost=40 is cheapest at level 0
        val upgrades = mapOf(UpgradeType.DAMAGE to 0, UpgradeType.CASH_BONUS to 0)
        val wallet = PlayerWallet(stepBalance = 10_000)
        assertEquals(UpgradeType.CASH_BONUS, sut(upgrades, wallet))
    }

    @Test
    fun `none affordable returns null`() {
        val upgrades = mapOf(UpgradeType.DAMAGE to 0)
        val wallet = PlayerWallet(stepBalance = 0)
        assertNull(sut(upgrades, wallet))
    }

    @Test
    fun `all at max level returns null`() {
        // ORBS maxLevel=6
        val upgrades = mapOf(UpgradeType.ORBS to 6)
        val wallet = PlayerWallet(stepBalance = 999_999)
        assertNull(sut(upgrades, wallet))
    }

    @Test
    fun `empty upgrades returns null`() {
        assertNull(sut(emptyMap(), PlayerWallet(stepBalance = 999_999)))
    }
}
