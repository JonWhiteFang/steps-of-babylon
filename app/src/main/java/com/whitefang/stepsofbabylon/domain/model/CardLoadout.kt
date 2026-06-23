package com.whitefang.stepsofbabylon.domain.model

data class CardLoadout(
    val cards: List<CardType> = emptyList(),
) {
    init {
        require(cards.size <= MAX_SIZE) { "Loadout cannot exceed $MAX_SIZE cards" }
        require(cards.distinct().size == cards.size) { "Loadout cannot contain duplicates" }
    }

    fun add(card: CardType): CardLoadout = copy(cards = cards + card)

    fun remove(card: CardType): CardLoadout = copy(cards = cards - card)

    companion object {
        const val MAX_SIZE = 3
    }
}
