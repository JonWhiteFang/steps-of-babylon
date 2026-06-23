package com.whitefang.stepsofbabylon.data

import android.app.Activity
import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.whitefang.stepsofbabylon.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DataDeletionManagerTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var manager: DataDeletionManager
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // SynchronousExecutor so cancelAllWork().result resolves on the calling thread — makes the
        // #248 "await cancel before close" path deterministic in-test (the default executor is async).
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        manager = DataDeletionManager(context, db)
        activity = mock()
    }

    @After
    fun tearDown() {
        if (db.isOpen) db.close()
    }

    @Test
    fun `deleteAllData clears all SharedPreferences`() {
        DataDeletionManager.PREFS_NAMES.forEach { name ->
            context
                .getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .putString("test_key", "test_value")
                .commit()
        }

        manager.deleteAllData(activity)

        DataDeletionManager.PREFS_NAMES.forEach { name ->
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            assertTrue("$name should be empty after deletion", prefs.all.isEmpty())
        }
    }

    @Test
    fun `deleteAllData closes database`() {
        manager.deleteAllData(activity)
        assertFalse("Database should be closed", db.isOpen)
    }

    @Test
    fun `deleteAllData calls activity recreate`() {
        manager.deleteAllData(activity)
        verify(activity).recreate()
    }

    @Test
    fun `deleteAllData is idempotent — calling twice does not crash`() {
        manager.deleteAllData(activity)
        manager.deleteAllData(activity)
        // No exception = pass
    }

    @Test
    fun `deleteAllData cancels in-flight work before closing the database`() {
        val wm = WorkManager.getInstance(context)
        // Enqueue work with a long initial delay so it stays ENQUEUED (never starts running under the
        // SynchronousExecutor), modelling pending background work at wipe time.
        val request =
            OneTimeWorkRequestBuilder<NoOpWorker>()
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
        wm.enqueue(request).result.get()
        assertEquals(WorkInfo.State.ENQUEUED, wm.getWorkInfoById(request.id).get()?.state)

        manager.deleteAllData(activity)

        // The awaited cancellation must have run before close(): the work is cancelled and the DB is closed
        // without an "already-closed object" throw (the method returning normally proves no crash).
        val info = wm.getWorkInfoById(request.id).get()
        assertEquals(WorkInfo.State.CANCELLED, info?.state)
        assertFalse("Database should be closed", db.isOpen)
    }

    @Test
    fun `deleteAllData clears the crash breadcrumb prefs`() {
        // Seed a breadcrumb directly via the same file name CrashBreadcrumbStore uses.
        context
            .getSharedPreferences("crash_breadcrumb_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("crash_class", "x")
            .commit()

        manager.deleteAllData(activity)

        assertTrue(
            "crash_breadcrumb_prefs must be cleared by deleteAllData",
            context.getSharedPreferences("crash_breadcrumb_prefs", Context.MODE_PRIVATE).all.isEmpty(),
        )
    }

    /** Minimal no-op worker used only to enqueue pending work for the cancel-before-close test. */
    class NoOpWorker(
        context: Context,
        params: WorkerParameters,
    ) : Worker(context, params) {
        override fun doWork(): Result = Result.success()
    }
}
