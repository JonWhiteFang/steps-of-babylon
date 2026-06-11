// Fix for #19 (High conf) TOCTOU / atomic-guard return value ignored
fun rushResearch() {
    // ...
    if (atomicGuard.rushResearch()) { // Check return value of atomic guard
        // Rush research
    }
    // ...
}