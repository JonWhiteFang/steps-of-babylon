package com.whitefang.stepsofbabylon.data

import android.app.Activity
import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
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
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context, Configuration.Builder().build()
        )
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
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
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit().putString("test_key", "test_value").commit()
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
    fun `deleteAllData clears the crash breadcrumb prefs`() {
        // Seed a breadcrumb directly via the same file name CrashBreadcrumbStore uses.
        context.getSharedPreferences("crash_breadcrumb_prefs", Context.MODE_PRIVATE)
            .edit().putString("crash_class", "x").commit()

        manager.deleteAllData(activity)

        assertTrue(
            "crash_breadcrumb_prefs must be cleared by deleteAllData",
            context.getSharedPreferences("crash_breadcrumb_prefs", Context.MODE_PRIVATE).all.isEmpty(),
        )
    }
}
