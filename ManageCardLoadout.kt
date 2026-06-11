// Fix for #36 (Medium conf) TOCTOU loadout-cap bypass
fun updateLoadout() {
    // ...
    synchronized(cardLoadout) { // Use synchronized block for atomic update
        // Update loadout
    }
    // ...
}