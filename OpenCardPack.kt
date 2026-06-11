// Fix for #19 (High conf) TOCTOU / atomic-guard return value ignored
fun openCardPack() {
    // ...
    if (atomicGuard.openCardPack()) { // Check return value of atomic guard
        // Open card pack
    }
    // ...
}