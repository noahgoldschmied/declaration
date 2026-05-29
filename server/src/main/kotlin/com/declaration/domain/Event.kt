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
    ) : Event()
}
