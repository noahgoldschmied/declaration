package com.declaration.domain

enum class AskOutcome { HIT, MISS, SELF_OVERLAP }

sealed class Event {
    data class Ask(
        val asker: PlayerId,
        val asked: PlayerId,
        val card: CardId,
        val outcome: AskOutcome,
    ) : Event()

    data class Declaration(
        val declarer: PlayerId,
        val deck: DeckId,
        val assignments: Map<CardId, PlayerId>,
        val correct: Boolean,
        val awardedTo: TeamId,
    ) : Event()
}
