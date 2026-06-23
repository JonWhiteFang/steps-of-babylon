package com.whitefang.stepsofbabylon.presentation.cards

import android.os.Parcelable
import com.whitefang.stepsofbabylon.domain.model.CardType
import com.whitefang.stepsofbabylon.domain.usecase.CardResult
import kotlinx.parcelize.Parcelize

/**
 * #234: presentation-layer Parcelable mirror of the transient pack-reveal payload so it survives
 * process death via SavedStateHandle. Domain `CardResult`/`CardType` stay pure (zero Android imports,
 * DomainPurityTest); `CardType` is encoded by `.name` and decoded via `valueOf`. The economic outcome
 * (cards + gems) is already durable in Room; this persists the reveal-once VISUAL confirmation only.
 */
@Parcelize
data class PackRevealState(
    val cards: List<RevealedCard>,
) : Parcelable

@Parcelize
data class RevealedCard(
    val cardTypeName: String,
    val isNew: Boolean,
    val copiesAwarded: Int,
) : Parcelable

/** Domain → DTO (write side; called when a pack opens). */
fun List<CardResult>.toPackRevealState(): PackRevealState =
    PackRevealState(map { RevealedCard(it.type.name, it.isNew, it.copiesAwarded) })

/** DTO → domain (read side; trusts the names — used by the test round-trip + after a known write). */
fun PackRevealState.toCardResults(): List<CardResult> =
    cards.map { CardResult(CardType.valueOf(it.cardTypeName), it.isNew, it.copiesAwarded) }

/**
 * Defensive DTO → domain for the restore path: if ANY card type name can't resolve (e.g. an enum was
 * renamed between the write and a later app version reading the saved bundle), drop the whole reveal
 * (returns null) rather than crashing. The reveal is a transient nicety; the cards are already in Room.
 */
fun PackRevealState.toCardResultsOrNull(): List<CardResult>? = runCatching { toCardResults() }.getOrNull()
