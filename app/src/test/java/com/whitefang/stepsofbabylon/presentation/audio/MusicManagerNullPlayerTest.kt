package com.whitefang.stepsofbabylon.presentation.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * #246: [MusicManager] must degrade to silent (not crash) when `MediaPlayer.create()` returns null,
 * and #242 (ADR-0033): each track's player is built **at most once**, **off the main thread**, then
 * cached and switched via pause/start instead of release/recreate on every navigation.
 *
 * All tests inject a **synchronous** decode executor so the off-thread dispatch resolves inline, and
 * `shadowOf(Looper.getMainLooper()).idle()` runs the marshal-back-to-main post deterministically.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MusicManagerNullPlayerTest {
    private fun context(): Context = ApplicationProvider.getApplicationContext()

    /** Runs submitted runnables immediately on the calling thread. */
    private val syncExecutor = Executor { it.run() }

    /** Queues runnables; [drain] runs them on demand — models an in-flight decode. */
    private class DeferredExecutor : Executor {
        private val queue = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            queue.add(command)
        }

        fun drain() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    /** Settle the off-thread decode (sync) + the marshal-to-main post. */
    private fun settle() = shadowOf(Looper.getMainLooper()).idle()

    // ---- #246 null-degrade (migrated to the executor + looper-idle model) ----

    @Test
    fun `playWalking does not crash when MediaPlayer create returns null`() {
        val manager = MusicManager(context(), playerFactory = { _, _ -> null }, injectedExecutor = syncExecutor)

        manager.playWalking()
        settle()

        assertNull(
            "a failed player creation must leave no active player (degrade to silent, not crash)",
            manager.activePlayerOrNullForTest(),
        )
        manager.pause()
        manager.resume()
        manager.setVolume(0.8f)
    }

    @Test
    fun `playBattle does not crash when MediaPlayer create returns null`() {
        val manager = MusicManager(context(), playerFactory = { _, _ -> null }, injectedExecutor = syncExecutor)

        manager.playBattle()
        settle()

        assertNull(manager.activePlayerOrNullForTest())
    }

    @Test
    fun `a successfully-created player is retained and started`() {
        val player = mock<MediaPlayer>()
        val manager = MusicManager(context(), playerFactory = { _, _ -> player }, injectedExecutor = syncExecutor)

        manager.playWalking()
        settle()

        verify(player).isLooping = true
        verify(player).setVolume(0.5f, 0.5f)
        verify(player).start()
    }

    // ---- #242 caching / off-thread / switch ----

    @Test
    fun `each track is built at most once across repeated navigations`() {
        val factoryCalls = HashMap<Int, Int>()
        val manager =
            MusicManager(
                context(),
                playerFactory = { _, resId ->
                    factoryCalls.merge(resId, 1, Int::plus)
                    mock()
                },
                injectedExecutor = syncExecutor,
            )

        // The frequent Battle↔menu churn: each transition used to release + re-decode.
        manager.playWalking()
        settle()
        manager.playBattle()
        settle()
        manager.playWalking()
        settle()
        manager.playBattle()
        settle()

        // Two distinct raw ids, each built once → two factory calls total, not four.
        assertEquals("two tracks seen (walking + battle)", 2, factoryCalls.size)
        factoryCalls.forEach { (resId, calls) ->
            assertEquals("track $resId built at most once (not once per navigation)", 1, calls)
        }
    }

    @Test
    fun `switching pauses the outgoing player and starts the incoming one`() {
        val walking = mock<MediaPlayer>()
        val battle = mock<MediaPlayer>()
        val manager =
            MusicManager(
                context(),
                playerFactory = { _, resId ->
                    if (resId ==
                        com.whitefang.stepsofbabylon.R.raw.bgm_walking
                    ) {
                        walking
                    } else {
                        battle
                    }
                },
                injectedExecutor = syncExecutor,
            )

        manager.playWalking()
        settle()
        verify(walking).start()

        manager.playBattle()
        settle()
        verify(walking).pause() // outgoing paused, not released
        verify(battle).seekTo(0) // incoming restarts at the top
        verify(battle).start()
    }

    @Test
    fun `re-decode does not happen while a decode is already in flight (build-once-or-in-flight)`() {
        val deferred = DeferredExecutor()
        val factoryCalls = HashMap<Int, Int>()
        val manager =
            MusicManager(
                context(),
                playerFactory = { _, resId ->
                    factoryCalls.merge(resId, 1, Int::plus)
                    mock()
                },
                injectedExecutor = deferred,
            )

        // Interleave: request BATTLE, then WALKING, then BATTLE again — all BEFORE any decode runs.
        manager.playBattle()
        manager.playWalking()
        manager.playBattle() // battle decode is still pending → must NOT dispatch a 2nd battle decode
        deferred.drain()
        settle()

        assertEquals(
            "battle must be decoded at most once even when re-requested mid-flight",
            1,
            factoryCalls[com.whitefang.stepsofbabylon.R.raw.bgm_battle],
        )
    }

    @Test
    fun `a muted manager does not start a deferred-decoded player`() {
        val battle = mock<MediaPlayer>()
        val deferred = DeferredExecutor()
        val manager =
            MusicManager(
                context(),
                playerFactory = { _, _ -> battle },
                injectedExecutor = deferred,
            )
        manager.setMuted(true)

        manager.playBattle()
        deferred.drain()
        settle()

        verify(battle, never()).start() // muted → cached + configured but not started
        verify(battle).isLooping = true // still configured for later unmute
    }

    @Test
    fun `release frees both built players and a late decode does not start`() {
        val walking = mock<MediaPlayer>()
        val battle = mock<MediaPlayer>()
        val deferred = DeferredExecutor()
        val manager =
            MusicManager(
                context(),
                playerFactory = { _, resId ->
                    if (resId ==
                        com.whitefang.stepsofbabylon.R.raw.bgm_walking
                    ) {
                        walking
                    } else {
                        battle
                    }
                },
                injectedExecutor = deferred,
            )

        manager.playWalking()
        deferred.drain()
        settle()
        verify(walking).start()

        manager.playBattle() // battle decode queued but not yet run
        manager.release() // tear down before the decode completes
        deferred.drain()
        settle() // late decode resolves AFTER release

        verify(walking).release()
        verify(battle, never()).start() // a decode completing post-release must not start
        verify(battle).release() // and must free the just-built player
    }
}
