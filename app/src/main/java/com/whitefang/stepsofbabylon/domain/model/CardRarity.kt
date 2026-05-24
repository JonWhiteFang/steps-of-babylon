package com.whitefang.stepsofbabylon.domain.model

enum class CardRarity(
    val copiesPerLevel: Int,
    @Deprecated("Card Dust removed in R4-08. Kept for one release to avoid breaking serialization.")
    val dustValue: Long = 0,
    @Deprecated("Card Dust removed in R4-08. Kept for one release to avoid breaking serialization.")
    val upgradeDustPerLevel: Long = 0,
) {
    COMMON(copiesPerLevel = 3, dustValue = 5, upgradeDustPerLevel = 10),
    RARE(copiesPerLevel = 4, dustValue = 15, upgradeDustPerLevel = 25),
    EPIC(copiesPerLevel = 5, dustValue = 50, upgradeDustPerLevel = 50),
}
