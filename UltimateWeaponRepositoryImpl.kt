// Fix for #36 (Medium conf) TOCTOU loadout-cap bypass
fun updateUltimateWeaponLoadout() {
    // ...
    synchronized(ultimateWeaponLoadout) { // Use synchronized block for atomic update
        // Update ultimate weapon loadout
    }
    // ...
}