package com.whitefang.stepsofbabylon.presentation.battle.effects

import com.whitefang.stepsofbabylon.domain.model.EnemyType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object DeathEffect {
    private val COLORS =
        mapOf(
            EnemyType.BASIC to 0xFFE53935.toInt(),
            EnemyType.FAST to 0xFFFF9800.toInt(),
            EnemyType.TANK to 0xFF8B0000.toInt(),
            EnemyType.RANGED to 0xFF9C27B0.toInt(),
            EnemyType.BOSS to 0xFF4A0000.toInt(),
            EnemyType.SCATTER to 0xFF4CAF50.toInt(),
        )

    fun spawn(
        pool: ParticlePool,
        x: Float,
        y: Float,
        type: EnemyType,
    ) {
        val color = COLORS[type] ?: 0xFFE53935.toInt()
        val count: Int
        val speedBase: Float
        when (type) {
            EnemyType.BASIC -> {
                count = 8
                speedBase = 80f
            }

            EnemyType.FAST -> {
                count = 6
                speedBase = 120f
            }

            EnemyType.TANK -> {
                count = 12
                speedBase = 40f
            }

            EnemyType.RANGED -> {
                count = 8
                speedBase = 70f
            }

            EnemyType.BOSS -> {
                count = 20
                speedBase = 100f
            }

            EnemyType.SCATTER -> {
                count = 6
                speedBase = 60f
            }
        }
        repeat(count) { i ->
            val p = pool.acquire()
            p.x = x
            p.y = y
            p.color = color
            p.lifetime = 0.4f + Random.nextFloat() * 0.3f
            p.size = if (type == EnemyType.BOSS) 5f + Random.nextFloat() * 3f else 3f + Random.nextFloat() * 2f
            when (type) {
                EnemyType.FAST -> {
                    p.vx = (
                        if (i % 2 ==
                            0
                        ) {
                            1f
                        } else {
                            -1f
                        }
                    ) * speedBase * (0.5f + Random.nextFloat())
                    p.vy = Random.nextFloat() * 30f - 15f
                }

                EnemyType.RANGED -> {
                    val angle = (PI / 2.0 * i / count.coerceAtLeast(1)).toFloat()
                    p.vx = cos(angle) * speedBase
                    p.vy = sin(angle) * speedBase
                }

                else -> {
                    val angle = (2.0 * PI * i / count).toFloat() + Random.nextFloat() * 0.3f
                    p.vx = cos(angle) * speedBase * (0.6f + Random.nextFloat() * 0.4f)
                    p.vy = sin(angle) * speedBase * (0.6f + Random.nextFloat() * 0.4f)
                }
            }
        }
    }
}
