package com.whitefang.stepsofbabylon.domain.model

data class OwnedCard(
    val id: Int,
    val type: CardType,
    val level: Int,
    val isEquipped: Boolean,
    val copyCount: Int = 1,
)
