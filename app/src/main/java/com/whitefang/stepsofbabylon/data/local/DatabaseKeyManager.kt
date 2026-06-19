package com.whitefang.stepsofbabylon.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the Room database encryption passphrase.
 * Generates a random passphrase on first run, encrypts it with an
 * Android Keystore key, and stores the encrypted blob in SharedPreferences.
 * On decryption failure, the response is scoped to the cause (#238): the DB is wiped **only** when the
 * Keystore alias is provably absent (the true device-restore signal — the on-disk DB is encrypted with a
 * now-unrecoverable passphrase). A decrypt failure with the alias still present is treated as a *transient*
 * Keystore fault (OEM daemon restart, low memory, post-OS-update) and is rethrown so the next launch retries
 * — non-regenerable player progress (Steps) is never destroyed on a fault we can't prove is unrecoverable.
 */
object DatabaseKeyManager {

    private const val TAG = "DatabaseKeyManager"
    private const val KEYSTORE_ALIAS = "steps_of_babylon_db_key"
    private const val PREFS_NAME = "db_key_prefs"
    private const val PREF_ENCRYPTED = "encrypted_passphrase"
    private const val PREF_IV = "passphrase_iv"
    // Must match DatabaseModule.provideDatabase() name — when the passphrase is
    // lost (e.g. backup-restore to a new device), the encrypted DB file on disk
    // cannot be opened with a freshly-generated passphrase, so we delete it
    // here alongside the stale key blob and let Room rebuild from scratch.
    private const val DB_FILENAME = "steps_of_babylon.db"

    /**
     * The two ways a decrypt failure can be handled (#238). [Wipe] is the irreversible
     * device-restore path; [Rethrow] preserves progress on a transient fault.
     */
    internal sealed interface DecryptFailureAction {
        object Wipe : DecryptFailureAction
        object Rethrow : DecryptFailureAction
    }

    /**
     * #238: the pure wipe-vs-rethrow decision, extracted so it's JVM-unit-testable without a working
     * AndroidKeyStore (Robolectric can't shadow it). Wipe **only** when the alias is provably absent
     * (true device-restore — the on-disk DB is unrecoverable); otherwise preserve progress and retry.
     */
    internal fun decideOnDecryptFailure(aliasExists: Boolean): DecryptFailureAction =
        if (aliasExists) DecryptFailureAction.Rethrow else DecryptFailureAction.Wipe

    /**
     * Whether the Keystore alias backing the passphrase is present. Overridable for tests (the real impl
     * touches AndroidKeyStore, which Robolectric can't provide). If the keystore can't even be opened to
     * check, we report `true` ("present/uncertain") so an *uncertain* failure never triggers a destructive
     * wipe — preferring data preservation for non-regenerable currency.
     */
    internal var keystoreAliasExists: () -> Boolean = ::realKeystoreAliasExists

    private fun realKeystoreAliasExists(): Boolean =
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(KEYSTORE_ALIAS)
        } catch (e: Exception) {
            Log.w(TAG, "Keystore unavailable while checking alias presence — treating as present (no wipe)", e)
            true
        }

    fun getPassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(PREF_ENCRYPTED, null)
        if (existing != null) {
            try {
                return decrypt(
                    android.util.Base64.decode(existing, android.util.Base64.NO_WRAP),
                    android.util.Base64.decode(prefs.getString(PREF_IV, "")!!, android.util.Base64.NO_WRAP),
                )
            } catch (e: Exception) {
                when (decideOnDecryptFailure(keystoreAliasExists())) {
                    DecryptFailureAction.Wipe -> {
                        // Alias provably absent (device restore to new hardware) — the on-disk DB is
                        // encrypted with the now-lost passphrase and cannot be recovered. Delete it so Room
                        // rebuilds cleanly instead of crash-looping on open, then fall through to regenerate.
                        Log.w(TAG, "Keystore alias absent — wiping stale key blob and unreadable DB file", e)
                        prefs.edit().clear().apply()
                        wipeDatabaseFile(context)
                    }
                    DecryptFailureAction.Rethrow -> {
                        // Alias present but decrypt threw → transient/recoverable Keystore fault. Do NOT
                        // destroy progress; rethrow so DB open fails this launch and the next launch retries.
                        Log.e(TAG, "Transient passphrase decryption failure (alias present) — NOT wiping", e)
                        throw e
                    }
                }
            }
        }
        val passphrase = generateRandomPassphrase()
        val (encrypted, iv) = encrypt(passphrase)
        prefs.edit()
            .putString(PREF_ENCRYPTED, android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
            .putString(PREF_IV, android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    /**
     * Deletes the SQLCipher database file and its companion -shm/-wal files.
     * Called when the passphrase blob is no longer decryptable — the on-disk DB
     * is unreadable without the original passphrase, so wiping it prevents a
     * crash-on-launch loop at the cost of local progress. Progress is already
     * unrecoverable at this point.
     *
     * Visible for testing: Robolectric cannot provide a working AndroidKeyStore
     * shadow, so the full decrypt-fail → wipe path is verified via direct test
     * on this method. Keystore integration is verified by the single call site
     * in [getPassphrase] plus on-device smoke.
     */
    internal fun wipeDatabaseFile(context: Context) {
        val dbFile = context.getDatabasePath(DB_FILENAME)
        listOf(dbFile, java.io.File(dbFile.path + "-shm"), java.io.File(dbFile.path + "-wal"))
            .filter { it.exists() }
            .forEach { file ->
                if (!file.delete()) {
                    Log.w(TAG, "Failed to delete stale DB file: ${file.path}")
                }
            }
    }

    private fun generateRandomPassphrase(): ByteArray {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.getEntry(KEYSTORE_ALIAS, null)?.let {
            return (it as KeyStore.SecretKeyEntry).secretKey
        }
        val spec = KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(spec)
            generateKey()
        }
    }

    private fun encrypt(data: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
        return cipher.doFinal(data) to cipher.iv
    }

    private fun decrypt(data: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(data)
    }
}
