// Fix for #19 (High conf) TOCTOU / atomic-guard return value ignored
fun upgradeUltimateWeapon() {
    // ...
    if (atomicGuard.upgradeUltimateWeapon()) { // Check return value of atomic guard
        // Upgrade ultimate weapon
    }
    // ...
}