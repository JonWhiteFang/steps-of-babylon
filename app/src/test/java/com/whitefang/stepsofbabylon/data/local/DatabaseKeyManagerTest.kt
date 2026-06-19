package com.whitefang.stepsofbabylon.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.whitefang.stepsofbabylon.data.local.DatabaseKeyManager.DecryptFailureAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Verifies DatabaseKeyManager's recovery path when the Android Keystore key that
 * encrypted the passphrase blob is no longer available (e.g. device restore, key
 * rotation). The old passphrase is unrecoverable and the on-disk SQLCipher DB
 * file cannot be opened with a freshly-generated passphrase — without recovery,
 * the app would crash on launch in a loop.
 *
 * Scope of this test: the file-wipe half of the recovery path. Robolectric does
 * not ship a working AndroidKeyStore shadow (KeyStore.getInstance("AndroidKeyStore")
 * throws NoSuchAlgorithmException), so the keystore-half is exercised by the
 * single call site in [DatabaseKeyManager.getPassphrase] plus on-device smoke,
 * not here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DatabaseKeyManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbFile: File = context.getDatabasePath("steps_of_babylon.db")
    private val shmFile: File = File(dbFile.path + "-shm")
    private val walFile: File = File(dbFile.path + "-wal")

    @Before
    fun setup() {
        dbFile.parentFile?.mkdirs()
        listOf(dbFile, shmFile, walFile).forEach { if (it.exists()) it.delete() }
    }

    @After
    fun tearDown() {
        listOf(dbFile, shmFile, walFile).forEach { if (it.exists()) it.delete() }
    }

    @Test
    fun `wipeDatabaseFile deletes the main DB file`() {
        dbFile.writeBytes(byteArrayOf(0x01, 0x02, 0x03))
        assertTrue("precondition", dbFile.exists())

        DatabaseKeyManager.wipeDatabaseFile(context)

        assertFalse("DB file should be deleted after wipe", dbFile.exists())
    }

    @Test
    fun `wipeDatabaseFile deletes companion -shm and -wal files`() {
        dbFile.writeBytes(byteArrayOf(0x01))
        shmFile.writeBytes(byteArrayOf(0x02))
        walFile.writeBytes(byteArrayOf(0x03))

        DatabaseKeyManager.wipeDatabaseFile(context)

        assertFalse("-shm should be deleted", shmFile.exists())
        assertFalse("-wal should be deleted", walFile.exists())
    }

    @Test
    fun `wipeDatabaseFile is a no-op when no DB file exists`() {
        assertFalse("precondition", dbFile.exists())

        // Must not throw for a fresh install (no prior DB to clean up).
        DatabaseKeyManager.wipeDatabaseFile(context)

        assertFalse(dbFile.exists())
    }

    // #238: the wipe-vs-rethrow decision is a pure seam so it's testable without an AndroidKeyStore.

    @Test
    fun `decideOnDecryptFailure wipes only when the alias is provably absent`() {
        assertEquals(DecryptFailureAction.Wipe, DatabaseKeyManager.decideOnDecryptFailure(aliasExists = false))
    }

    @Test
    fun `decideOnDecryptFailure rethrows when the alias is present — transient fault must not wipe`() {
        assertEquals(DecryptFailureAction.Rethrow, DatabaseKeyManager.decideOnDecryptFailure(aliasExists = true))
    }
}
