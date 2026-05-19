package com.whitefang.stepsofbabylon.presentation.battle.engine

import com.whitefang.stepsofbabylon.domain.model.BattleConditionEffects
import com.whitefang.stepsofbabylon.domain.model.EnemyType
import com.whitefang.stepsofbabylon.presentation.battle.entities.EnemyEntity
import kotlin.math.min
import kotlin.random.Random

enum class WavePhase { SPAWNING, COOLDOWN }

class WaveSpawner(
    private val onSpawnEnemy: (EnemyEntity) -> Unit,
    private val zigguratX: Float,
    private val zigguratY: Float,
    private val onEnemyDeath: (EnemyEntity) -> Unit,
    private val onMeleeHit: (EnemyEntity, Double) -> Unit,
    private val onEnemyFireProjectile: (Float, Float, Float, Float, Double) -> Unit,
    private val onWaveComplete: (waveNumber: Int) -> Unit = {},
    private val conditions: BattleConditionEffects = BattleConditionEffects(),
    private val enemyTint: Int = 0,
    /**
     * Wave number the spawner starts on (RO-11 #B.1, WAVE_SKIP lab research). Default `1`
     * preserves the pre-RO-11 "start at wave 1" behaviour for every call site that
     * doesn't yet thread the value through. [BattleViewModel] passes `1 + WAVE_SKIP_level`
     * (max L10 → wave 11) via [GameEngine.init]; cooldown→increment lifecycle is unchanged
     * so subsequent waves climb from the start value normally. Floor of 1 is the caller's
     * responsibility — the constructor does not clamp.
     */
    private val startWave: Int = 1,
) {
    var currentWave: Int = startWave; private set
    var phase: WavePhase = WavePhase.SPAWNING; private set
    var enemiesAlive: Int = 0; private set

    var phaseTimer = 0f; private set
    private var spawnTimer = 0f
    private var enemiesSpawned = 0
    private var totalToSpawn = 0

    companion object {
        const val SPAWN_DURATION = 26f
        const val COOLDOWN_DURATION = 9f
    }

    init { startWave() }

    private fun startWave() {
        phase = WavePhase.SPAWNING
        phaseTimer = 0f
        spawnTimer = 0f
        enemiesSpawned = 0
        totalToSpawn = enemiesPerWave(currentWave)
    }

    fun onEnemyKilled() { enemiesAlive-- }

    fun update(deltaTime: Float, screenWidth: Float, screenHeight: Float) {
        phaseTimer += deltaTime
        when (phase) {
            WavePhase.SPAWNING -> {
                if (enemiesSpawned < totalToSpawn) {
                    val interval = SPAWN_DURATION / totalToSpawn
                    spawnTimer += deltaTime
                    while (spawnTimer >= interval && enemiesSpawned < totalToSpawn) {
                        spawnTimer -= interval
                        spawnEnemy(screenWidth, screenHeight)
                    }
                }
                if (phaseTimer >= SPAWN_DURATION) {
                    while (enemiesSpawned < totalToSpawn) spawnEnemy(screenWidth, screenHeight)
                    onWaveComplete(currentWave)
                    phase = WavePhase.COOLDOWN
                    phaseTimer = 0f
                }
            }
            WavePhase.COOLDOWN -> {
                if (phaseTimer >= COOLDOWN_DURATION) {
                    currentWave++
                    startWave()
                }
            }
        }
    }

    private fun spawnEnemy(screenWidth: Float, screenHeight: Float) {
        val type = pickType(currentWave, enemiesSpawned)
        val hp = EnemyScaler.scaleHealth(type, currentWave)
        val dmg = EnemyScaler.scaleDamage(type, currentWave)
        val spd = EnemyScaler.scaleSpeed(type) * conditions.enemySpeedMultiplier
        val atkInterval = 1f / conditions.enemyAttackSpeedMultiplier
        val (sx, sy) = spawnPosition(screenWidth, screenHeight, type)

        val enemy = EnemyEntity(
            enemyType = type, currentHp = hp, maxHp = hp, speed = spd, damage = dmg,
            targetX = zigguratX, targetY = zigguratY,
            onDeath = onEnemyDeath,
            onMeleeHit = if (type != EnemyType.RANGED) onMeleeHit else null,
            onFireProjectile = if (type == EnemyType.RANGED) onEnemyFireProjectile else null,
            attackInterval = atkInterval,
            armorHits = conditions.armorHits,
            enemyTint = enemyTint,
        ).apply { x = sx; y = sy; initDistance() }

        enemiesAlive++
        enemiesSpawned++
        onSpawnEnemy(enemy)
    }

    private fun pickType(wave: Int, index: Int): EnemyType {
        if (wave % conditions.bossWaveInterval == 0 && index == 0) return EnemyType.BOSS
        val roll = Random.nextFloat()
        return when {
            wave <= 5 -> EnemyType.BASIC
            wave <= 10 -> if (roll < 0.20f) EnemyType.FAST else EnemyType.BASIC
            wave <= 20 -> when {
                roll < 0.10f -> EnemyType.TANK
                roll < 0.30f -> EnemyType.FAST
                else -> EnemyType.BASIC
            }
            wave <= 30 -> when {
                roll < 0.10f -> EnemyType.TANK
                roll < 0.25f -> EnemyType.RANGED
                roll < 0.45f -> EnemyType.FAST
                else -> EnemyType.BASIC
            }
            else -> when {
                roll < 0.10f -> EnemyType.SCATTER
                roll < 0.20f -> EnemyType.TANK
                roll < 0.35f -> EnemyType.RANGED
                roll < 0.55f -> EnemyType.FAST
                else -> EnemyType.BASIC
            }
        }
    }

    private fun enemiesPerWave(wave: Int): Int = min(5 + (wave - 1) * 2, 40)

    private fun spawnPosition(screenWidth: Float, screenHeight: Float, type: EnemyType): Pair<Float, Float> {
        val margin = if (type == EnemyType.BOSS) 50f else 30f
        return when (Random.nextInt(3)) {
            0 -> Random.nextFloat() * screenWidth to -margin
            1 -> -margin to Random.nextFloat() * screenHeight * 0.6f
            else -> screenWidth + margin to Random.nextFloat() * screenHeight * 0.6f
        }
    }
}
