// Fix for #16 (High conf) Off-by-one / duplicate-trigger on round start
fun startRound() {
    // ...
    roundStartTriggered = true // Add this line to prevent duplicate trigger
    // ...
}

// Fix for #17 (High conf) Lifesteal granted on fully armor-absorbed hits
fun applyLifesteal(hit: Hit) {
    if (hit.damage > 0) { // Check if damage is greater than 0
        // Grant lifesteal
    }
}

// Fix for #18 (High conf) Per-frame list allocations in hot UW loop
private val uwList = mutableListOf<UltimateWeapon>() // Create a reusable list

fun updateUW() {
    // ...
    uwList.clear() // Clear the list instead of creating a new one
    // ...
}