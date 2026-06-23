package com.whitefang.stepsofbabylon.domain.usecase

import com.whitefang.stepsofbabylon.fakes.FakeCardRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ManageCardLoadoutTest {
    private lateinit var cardRepo: FakeCardRepository
    private lateinit var useCase: ManageCardLoadout

    @BeforeEach
    fun setup() {
        cardRepo = FakeCardRepository()
        useCase = ManageCardLoadout(cardRepo)
    }

    @Test
    fun `equip success when under capacity`() =
        runTest {
            val result = useCase.equip(1, equippedCount = 2)
            assertTrue(result is ManageCardLoadout.Result.Success)
        }

    @Test
    fun `equip fails when loadout full`() =
        runTest {
            val result = useCase.equip(1, equippedCount = 3)
            assertTrue(result is ManageCardLoadout.Result.LoadoutFull)
        }

    @Test
    fun `unequip always succeeds`() =
        runTest {
            val result = useCase.unequip(1)
            assertTrue(result is ManageCardLoadout.Result.Success)
        }
}
