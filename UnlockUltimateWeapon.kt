// Fix for #19 (High conf) TOCTOU / atomic-guard return value ignored
fun unlockUltimateWeapon() {
    // ...
    if (atomicGuard.unlockUltimateWeapon()) { // Check return value of atomic guard
        // Unlock ultimate weapon
    }
    // ...
}