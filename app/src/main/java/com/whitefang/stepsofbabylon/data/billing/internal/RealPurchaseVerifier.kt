package com.whitefang.stepsofbabylon.data.billing.internal

import android.util.Log
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.util.Base64

/**
 * Concrete [PurchaseVerifier] doing standard Google Play `SHA1withRSA` signature verification
 * against the developer's Base64-encoded Play "Licensing" public key — a local port of the
 * canonical Play Billing sample's `Security.verifyPurchase` — plus a signed-payload→product
 * binding check (#124, ADR-0005). Needs no backend.
 *
 * The public key is parsed once at construction (it is fixed for the app's lifetime). A blank
 * key disables verification (fail-open) so debug / CI builds with no Play license key behave
 * exactly as they did before #124; a configured-but-unparseable key fails closed (every
 * purchase rejected) so a developer misconfiguration surfaces immediately in internal testing
 * rather than silently granting. (A *release* build with a blank key is rejected at build time
 * — see `app/build.gradle.kts` — so fail-open can never reach the Play Store.) See
 * [PurchaseVerifier.isValidPurchase] for the full contract.
 */
internal class RealPurchaseVerifier(
    base64PublicKey: String,
) : PurchaseVerifier {

    /** `true` when no key is configured — verification is a no-op pass (see class KDoc). */
    private val failOpen: Boolean = base64PublicKey.isBlank()

    /**
     * Parsed RSA public key, or `null` if a non-blank key failed to parse (→ fail closed).
     * Parsing once avoids re-decoding on every purchase.
     */
    private val publicKey: PublicKey? = if (failOpen) {
        null
    } else {
        runCatching {
            val decoded = Base64.getDecoder().decode(base64PublicKey)
            KeyFactory.getInstance("RSA")
                .generatePublic(java.security.spec.X509EncodedKeySpec(decoded))
        }.onFailure {
            Log.e(TAG, "Play license public key did not parse; rejecting ALL purchases.", it)
        }.getOrNull()
    }

    override fun isValidPurchase(
        originalJson: String?,
        signature: String?,
        expectedProductId: String,
        expectedPurchaseToken: String,
    ): Boolean {
        // No key configured → verification disabled (debug/CI). Pre-#124 behaviour.
        if (failOpen) return true
        // Key was configured but didn't parse → reject everything (fail closed).
        val key = publicKey ?: return false
        // A real purchase always carries both fields; their absence means stripped/forged data.
        if (originalJson == null || signature == null) return false

        // 1. The signature must be Google's, over exactly these payload bytes.
        if (!signatureMatches(key, originalJson, signature)) return false

        // 2. The now-trusted payload's productId + purchaseToken must match the grant. This binds
        //    the verified signature to THIS product/token, so a genuinely-signed cheap receipt
        //    can't be replayed by a hooked client to grant an expensive product (#124 finding 2).
        //    Safe to extract by field after step 1: the signature guarantees the JSON is authentic
        //    Google output, so the field values are trustworthy and unambiguous.
        if (extractJsonString(originalJson, "productId") != expectedProductId) return false
        if (extractJsonString(originalJson, "purchaseToken") != expectedPurchaseToken) return false

        return true
    }

    private fun signatureMatches(key: PublicKey, originalJson: String, signature: String): Boolean =
        runCatching {
            val sigBytes = Base64.getDecoder().decode(signature)
            val verifier = Signature.getInstance("SHA1withRSA")
            verifier.initVerify(key)
            verifier.update(originalJson.toByteArray(Charsets.UTF_8))
            verifier.verify(sigBytes)
        }.getOrElse {
            // Malformed Base64 signature, wrong-shape bytes, etc. — treat as invalid, never throw.
            Log.w(TAG, "Signature verification threw; rejecting purchase.", it)
            false
        }

    /**
     * Returns the string value of [field] in [json], or `null` if absent. Play's `originalJson`
     * is a flat object whose `productId` / `purchaseToken` are plain JSON string values
     * (`[a-z0-9._]` SKUs, base64url-ish tokens — no embedded quotes/backslashes), so a targeted
     * field match is unambiguous and avoids depending on `org.json` (an `android.jar` stub on the
     * pure-JVM unit-test classpath). Only ever called on a signature-verified (trusted) payload.
     */
    private fun extractJsonString(json: String, field: String): String? =
        Regex(""""${Regex.escape(field)}"\s*:\s*"([^"\\]*)"""").find(json)?.groupValues?.get(1)

    private companion object {
        const val TAG = "PurchaseVerifier"
    }
}
