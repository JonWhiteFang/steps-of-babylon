package com.whitefang.stepsofbabylon.presentation.battle.engine

import android.graphics.Canvas
import com.whitefang.stepsofbabylon.domain.battle.entity.EntityProtocol

abstract class Entity(
    override var x: Float = 0f,
    override var y: Float = 0f,
    override var width: Float = 0f,
    var height: Float = 0f,
    override var isAlive: Boolean = true,
) : EntityProtocol {
    abstract override fun update(deltaTime: Float)

    abstract fun render(canvas: Canvas)
}
