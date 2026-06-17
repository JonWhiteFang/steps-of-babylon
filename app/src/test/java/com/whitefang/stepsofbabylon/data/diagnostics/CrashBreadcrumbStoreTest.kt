package com.whitefang.stepsofbabylon.data.diagnostics

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CrashBreadcrumbStoreTest {

    private fun newStore(): CrashBreadcrumbStore =
        CrashBreadcrumbStore(RuntimeEnvironment.getApplication() as Context)

    @Test
    fun `peek is null when nothing recorded`() {
        assertNull(newStore().peek())
    }

    @Test
    fun `record then peek round-trips the fields`() {
        val store = newStore()
        val ex = IllegalStateException("boom")
        store.record("GameLoop", ex, timestampMillis = 1234L)

        val b = store.peek()!!
        assertEquals(1234L, b.timestampMillis)
        assertEquals("GameLoop", b.threadName)
        assertEquals("java.lang.IllegalStateException", b.exceptionClass)
        assertEquals("boom", b.message)
        assertTrue("stack preview should mention the exception", b.stackPreview.contains("IllegalStateException"))
    }

    @Test
    fun `clear removes the breadcrumb`() {
        val store = newStore()
        store.record("main", RuntimeException("x"), 1L)
        store.clear()
        assertNull(store.peek())
    }

    @Test
    fun `newest record overwrites the previous one`() {
        val store = newStore()
        store.record("t1", RuntimeException("first"), 1L)
        store.record("t2", RuntimeException("second"), 2L)

        val b = store.peek()!!
        assertEquals(2L, b.timestampMillis)
        assertEquals("t2", b.threadName)
        assertEquals("second", b.message)
    }

    @Test
    fun `stack preview is truncated to MAX_STACK_CHARS`() {
        val store = newStore()
        val deep = RuntimeException("x".repeat(10_000))
        store.record("main", deep, 1L)
        assertTrue(store.peek()!!.stackPreview.length <= CrashBreadcrumbStore.MAX_STACK_CHARS)
    }

    @Test
    fun `record never throws on a null message`() {
        val store = newStore()
        store.record("main", NullPointerException(), 1L)
        val b = store.peek()!!
        assertEquals("java.lang.NullPointerException", b.exceptionClass)
        assertNull(b.message)
    }
}
