package com.whitefang.stepsofbabylon.presentation.audio

import android.content.Context
import android.media.MediaPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #246: [MusicManager.createPlayer] called `MediaPlayer.create(context, resId).apply { … }` on the
 * non-null return type — but `MediaPlayer.create()` is documented to return **null** on failure
 * (codec init failure, transient OOM, asset decode error). A null return therefore threw a
 * KotlinNullPointerException on the main thread inside playWalking()/playBattle() (called from
 * MainActivity), crashing the app.
 *
 * The fix injects a player-factory seam (default `MediaPlayer::create`) so the null path is testable,
 * makes createPlayer null-tolerant, and degrades gracefully to silent when creation fails.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MusicManagerNullPlayerTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `playWalking does not crash when MediaPlayer create returns null`() {
        // Factory simulates MediaPlayer.create() failing (returns null) — the real crash trigger.
        val manager = MusicManager(context(), playerFactory = { _, _ -> null })

        manager.playWalking() // pre-fix: KotlinNullPointerException from `.apply{}` on null

        // Degrades to silent: no active player retained, so a subsequent pause/resume is also safe.
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
        val manager = MusicManager(context(), playerFactory = { _, _ -> null })

        manager.playBattle() // pre-fix: KotlinNullPointerException

        assertNull(manager.activePlayerOrNullForTest())
    }

    @Test
    fun `a successfully-created player is retained and started`() {
        val player = mock<MediaPlayer>()
        val manager = MusicManager(context(), playerFactory = { _, _ -> player })

        manager.playWalking()

        // Looping + initial volume are configured on the created player, and it is started
        // (manager defaults to unmuted), proving the happy path (the full createPlayer config) is
        // unchanged by the null-guard. The default volume is 0.5f (MusicManager.volume), and
        // createPlayer seeds it via setVolume(volume, volume) — assert it so a regression dropping
        // the volume seeding on creation is caught, not just looping+start.
        verify(player).isLooping = true
        verify(player).setVolume(0.5f, 0.5f)
        verify(player).start()
    }
}
