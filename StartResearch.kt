// Fix for #19 (High conf) TOCTOU / atomic-guard return value ignored
fun startResearch() {
    // ...
    if (atomicGuard.startResearch()) { // Check return value of atomic guard
        // Start research
    }
    // ...
}