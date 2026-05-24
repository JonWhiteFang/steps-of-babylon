package com.whitefang.stepsofbabylon.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-UW persistent state. R4-06 replaces the pre-R4-06 single `level: Int` column
 * with three independent path-level columns plus an explicit [isUnlocked] flag. See
 * [com.whitefang.stepsofbabylon.domain.model.UWPath] for the path semantics and
 * [AppMigrations.MIGRATION_9_10] for the migration that redistributes legacy `level`
 * values across the new columns.
 *
 * Defaults match the post-unlock starting state: all three path levels = 0, no path
 * upgrades purchased yet. [isUnlocked] defaults to `false` because legitimate row
 * insertions (via [com.whitefang.stepsofbabylon.data.repository.UltimateWeaponRepositoryImpl.unlockWeapon])
 * always set it explicitly; the `false` default is the safe state for any other
 * insertion path.
 */
@Entity(tableName = "ultimate_weapon_state")
data class UltimateWeaponStateEntity(
    @PrimaryKey val weaponType: String,
    val damageLevel: Int = 0,
    val secondaryLevel: Int = 0,
    val cooldownLevel: Int = 0,
    val isUnlocked: Boolean = false,
    val isEquipped: Boolean = false,
)
