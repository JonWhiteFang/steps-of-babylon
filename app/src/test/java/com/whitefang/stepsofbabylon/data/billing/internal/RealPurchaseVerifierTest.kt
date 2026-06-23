package com.whitefang.stepsofbabylon.data.billing.internal

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/**
 * Pure-JVM coverage for [RealPurchaseVerifier] — the client-side Play Billing RSA-SHA1
 * signature check that gates wallet grants (#124).
 *
 * The tests generate a throwaway RSA keypair, sign a Play-shaped `originalJson` payload with
 * `SHA1withRSA` exactly the way Play Services signs a real purchase, and Base64-encode the
 * public key the same way the Play Console "Licensing" public key is published. No mocks —
 * this exercises the real `java.security` verification path plus the signed-payload→product
 * binding (#124 finding 2) end to end.
 */
class RealPurchaseVerifierTest {
    private val keyPair: KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private val base64PublicKey: String =
        Base64.getEncoder().encodeToString(keyPair.public.encoded)

    /** Builds a Play-shaped signed payload for [productId] + [purchaseToken]. */
    private fun payload(
        productId: String,
        purchaseToken: String,
    ): String =
        """{"orderId":"GPA.1","packageName":"com.whitefang.stepsofbabylon",""" +
            """"productId":"$productId","purchaseTime":1700000000000,"purchaseState":0,""" +
            """"purchaseToken":"$purchaseToken","acknowledged":false}"""

    private fun sign(payload: String): String {
        val signer = Signature.getInstance("SHA1withRSA")
        signer.initSign(keyPair.private)
        signer.update(payload.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    @Test
    fun `valid signature bound to the expected product and token verifies`() {
        val verifier = RealPurchaseVerifier(base64PublicKey)
        val json = payload("gem_pack_large", "tok_real")

        assertTrue(verifier.isValidPurchase(json, sign(json), "gem_pack_large", "tok_real"))
    }

    @Test
    fun `tampered payload fails`() {
        val verifier = RealPurchaseVerifier(base64PublicKey)
        val signed = payload("gem_pack_small", "tok")
        val signature = sign(signed)
        // Attacker rewrites the SKU but reuses the signature.
        val tampered = payload("gem_pack_large", "tok")

        assertFalse(verifier.isValidPurchase(tampered, signature, "gem_pack_large", "tok"))
    }

    @Test
    fun `tampered signature fails`() {
        val verifier = RealPurchaseVerifier(base64PublicKey)
        val json = payload("ad_removal", "tok")
        val bytes = Base64.getDecoder().decode(sign(json))
        bytes[0] = (bytes[0].toInt() xor 0x01).toByte() // flip one bit
        val tamperedSig = Base64.getEncoder().encodeToString(bytes)

        assertFalse(verifier.isValidPurchase(json, tamperedSig, "ad_removal", "tok"))
    }

    @Test
    fun `validly-signed cheap purchase cannot be replayed for an expensive product`() {
        // #124 finding 2: the core replay attack. Attacker has ONE genuinely Google-signed
        // gem_pack_small receipt and tries to use it while the app grants GEM_PACK_LARGE.
        // The signature is valid, but the signed productId is gem_pack_small, so binding fails.
        val verifier = RealPurchaseVerifier(base64PublicKey)
        val json = payload("gem_pack_small", "tok_small")
        val genuineSignature = sign(json)

        assertFalse(
            verifier.isValidPurchase(json, genuineSignature, "gem_pack_large", "tok_small"),
            "signed small-pack receipt must not satisfy a large-pack grant",
        )
    }

    @Test
    fun `signed purchaseToken must match the token used for the grant`() {
        // Attacker reuses one signed payload but swaps the (unsigned) idempotency token to
        // re-grant. The signed token no longer matches the token keying the receipt → reject.
        val verifier = RealPurchaseVerifier(base64PublicKey)
        val json = payload("gem_pack_small", "tok_signed")

        assertFalse(
            verifier.isValidPurchase(json, sign(json), "gem_pack_small", "tok_DIFFERENT"),
        )
    }

    @Test
    fun `malformed base64 signature fails without throwing`() {
        val verifier = RealPurchaseVerifier(base64PublicKey)
        val json = payload("season_pass", "tok")

        assertFalse(verifier.isValidPurchase(json, "!!! not valid base64 !!!", "season_pass", "tok"))
    }

    @Test
    fun `null payload or null signature fails under a configured key`() {
        val verifier = RealPurchaseVerifier(base64PublicKey)
        val json = payload("gem_pack_small", "tok")

        assertFalse(verifier.isValidPurchase(null, sign(json), "gem_pack_small", "tok"))
        assertFalse(verifier.isValidPurchase(json, null, "gem_pack_small", "tok"))
        assertFalse(verifier.isValidPurchase(null, null, "gem_pack_small", "tok"))
    }

    @Test
    fun `blank public key fails open so debug and CI builds keep working`() {
        val verifier = RealPurchaseVerifier("")
        // No key configured → verification (signature AND binding) is a no-op pass; matches
        // pre-#124 behaviour and the repo's "a misconfigured build degrades safely" precedent.
        assertTrue(verifier.isValidPurchase(null, null, "gem_pack_small", "tok"))
        assertTrue(verifier.isValidPurchase("anything", "anything", "any_product", "any_tok"))
    }

    @Test
    fun `non-blank but unparseable public key fails closed`() {
        // A configured-but-garbage key is a developer misconfiguration. The secure default is
        // to reject every purchase (caught immediately in internal testing). Must not throw.
        val verifier = RealPurchaseVerifier("this-is-not-a-valid-base64-x509-key")
        val json = payload("gem_pack_small", "tok")

        assertFalse(verifier.isValidPurchase(json, sign(json), "gem_pack_small", "tok"))
    }
}
