package com.whitefang.stepsofbabylon.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UltimateWeaponLoadoutTest {
    @Test
    fun `empty loadout is valid`() {
        UltimateWeaponLoadout()
    }

    @Test
    fun `add up to 3 weapons succeeds`() {
        val loadout =
            UltimateWeaponLoadout()
                .add(UltimateWeaponType.entries[0])
                .add(UltimateWeaponType.entries[1])
                .add(UltimateWeaponType.entries[2])
        assertEquals(3, loadout.weapons.size)
    }

    @Test
    fun `adding 4th weapon throws`() {
        val full = UltimateWeaponLoadout(UltimateWeaponType.entries.take(3))
        assertThrows<IllegalArgumentException> { full.add(UltimateWeaponType.entries[3]) }
    }

    @Test
    fun `duplicate weapon throws`() {
        val w = UltimateWeaponType.entries[0]
        assertThrows<IllegalArgumentException> { UltimateWeaponLoadout(listOf(w, w)) }
    }

    @Test
    fun `remove works`() {
        val w = UltimateWeaponType.entries[0]
        assertEquals(0, UltimateWeaponLoadout(listOf(w)).remove(w).weapons.size)
    }
}
