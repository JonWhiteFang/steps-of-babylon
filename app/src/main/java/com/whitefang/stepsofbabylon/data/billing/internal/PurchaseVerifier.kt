package com.whitefang.stepsofbabylon.data.billing.internal

/**
 * Client-side Google Play purchase **signature verification** seam (#124).
 *
 * Every wallet grant in [com.whitefang.stepsofbabylon.data.billing.BillingManagerImpl] runs
 * through [isValidPurchase] before it credits permanent currency / entitlements. The check
 * confirms two things:
 *
 * 1. The purchase's `originalJson` payload was signed by Google with the developer's Play
 *    "Licensing" RSA private key — i.e. the [SdkPurchase] really came from Play Services and was
 *    not fabricated by a rooted device, a hooked Play Billing client, or a repackaged build.
 * 2. The *signed* `productId` and `purchaseToken` match the product + token the caller is about
 *    to grant. Without this binding, a genuinely Google-signed receipt for a cheap SKU could be
 *    replayed by a hooked client to grant an expensive product (#124 finding 2): the signature
 *    is valid, but it isn't valid *for that grant*.
 *
 * **Scope (per ADR-0005 / #124).** This is *client-side* RSA verification — it needs no backend
 * and is therefore explicitly NOT the "server-side receipt verification" forbidden by
 * `CONSTRAINTS.md` for v1.0. It is defence-in-depth: it raises the bar against local IAP fraud
 * but, like any client-side check, is itself bypassable on a fully-repackaged APK (the embedded
 * public key can be patched). All grants are local-only (no server economy), so this is a
 * tracked hardening step, not a complete fix for a determined local attacker.
 *
 * Kept behind an interface for the same reason as [BillingClientAdapter]: it lets
 * `BillingManagerImplTest` substitute a deterministic fake without doing real crypto, while the
 * concrete [RealPurchaseVerifier] is unit-tested in isolation against a real RSA keypair.
 */
internal interface PurchaseVerifier {
    /**
     * Returns `true` iff [signature] is a valid Play signature over [originalJson] for the
     * configured public key **and** the signed payload's `productId` / `purchaseToken` equal
     * [expectedProductId] / [expectedPurchaseToken].
     *
     * Contract:
     * - A `null`/blank [originalJson] or [signature] returns `false` **when a key is configured**
     *   (a real purchase always carries both; their absence means the data was stripped or forged).
     * - When **no** public key is configured (blank), this returns `true` (fail-open) so debug and
     *   CI builds — which have no Play license key — keep working exactly as they did before #124.
     *   Release builds source the key from `local.properties`; a release build with a blank key
     *   fails the Gradle build (see `app/build.gradle.kts`), so fail-open never ships.
     * - Never throws: malformed Base64, a bad key, or a crypto error all resolve to a boolean.
     *
     * @param expectedProductId    the SKU the caller is about to grant (`BillingProduct.skuId()`).
     * @param expectedPurchaseToken the token keying the idempotent grant for this purchase.
     */
    fun isValidPurchase(
        originalJson: String?,
        signature: String?,
        expectedProductId: String,
        expectedPurchaseToken: String,
    ): Boolean
}
