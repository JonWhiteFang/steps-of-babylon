// Fix for #19 (High conf) TOCTOU / atomic-guard return value ignored
fun spendCurrency() {
    // ...
    if (atomicGuard.spendCurrency()) { // Check return value of atomic guard
        // Spend currency
    }
    // ...
}