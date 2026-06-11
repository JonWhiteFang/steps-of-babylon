// Fix for #19 (High conf) TOCTOU / atomic-guard return value ignored
fun unlockLabSlot() {
    // ...
    if (atomicGuard.unlockLabSlot()) { // Check return value of atomic guard
        // Unlock lab slot
    }
    // ...
}