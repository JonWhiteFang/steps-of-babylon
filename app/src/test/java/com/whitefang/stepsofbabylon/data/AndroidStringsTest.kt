package com.whitefang.stepsofbabylon.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.whitefang.stepsofbabylon.domain.model.EnemyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * NEW (#260): AndroidStrings had no test. Verifies the engine seam produces localized, plural-correct
 * text and never surfaces a raw CONSTANT_CASE enum name. Robolectric resolves the real resources.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AndroidStringsTest {

    private val strings = AndroidStrings(ApplicationProvider.getApplicationContext<Context>())

    @Test fun `enemyTypeName is localized title-case, never raw name`() {
        assertEquals("Basic", strings.enemyTypeName(EnemyType.BASIC))
        assertEquals("Boss", strings.enemyTypeName(EnemyType.BOSS))
        EnemyType.entries.forEach {
            assertFalse("raw name leaked for $it", strings.enemyTypeName(it) == it.name)
        }
    }

    @Test fun `waveComposition joins entries in insertion order with no raw name`() {
        val comp = linkedMapOf(EnemyType.BASIC to 12, EnemyType.RANGED to 4)
        assertEquals("Next: 12 Basic, 4 Ranged", strings.waveComposition(comp))
    }

    @Test fun `waveComposition boss wave puts BOSS first (insertion order, not re-sorted)`() {
        val comp = linkedMapOf(EnemyType.BOSS to 1, EnemyType.BASIC to 9)
        assertEquals("Next: 1 Boss, 9 Basic", strings.waveComposition(comp))
    }

    @Test fun `bossCountdown is plural-correct`() {
        assertEquals("Boss next wave", strings.bossCountdown(1))
        assertEquals("Boss in 2 waves", strings.bossCountdown(2))
    }
}
