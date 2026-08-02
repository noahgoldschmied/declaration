package com.declaration.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AskOutcome { HIT, MISS, SELF_OVERLAP }

@Serializable
sealed class Event {
    @Serializable
    @SerialName("Ask")
    data class Ask(
        val asker: PlayerId,
        val asked: PlayerId,
        val card: CardId,
        val outcome: AskOutcome,
    ) : Event()

    @Serializable
    @SerialName("Declaration")
    data class Declaration(
        val declarer: PlayerId,
        val deck: DeckId,
        val assignments: Map<CardId, PlayerId>,
        val correct: Boolean,
        val awardedTo: TeamId,
        // The deck's true holder for each card, as it stood right before this declare captured
        // it. Equal to `assignments` when `correct`. Safe to reveal unconditionally -- the deck
        // is removed from play by this same event either way, so there's no future strategic
        // information being leaked, only a look back at who really had what.
        val actualHolders: Map<CardId, PlayerId>,
    ) : Event()
}
