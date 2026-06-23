package com.whitefang.stepsofbabylon.domain.model

data class UltimateWeaponLoadout(
    val weapons: List<UltimateWeaponType> = emptyList(),
) {
    init {
        require(weapons.size <= MAX_SIZE) { "Loadout cannot exceed $MAX_SIZE ultimate weapons" }
        require(weapons.distinct().size == weapons.size) { "Loadout cannot contain duplicates" }
    }

    fun add(weapon: UltimateWeaponType): UltimateWeaponLoadout = copy(weapons = weapons + weapon)

    fun remove(weapon: UltimateWeaponType): UltimateWeaponLoadout = copy(weapons = weapons - weapon)

    companion object {
        const val MAX_SIZE = 3
    }
}
