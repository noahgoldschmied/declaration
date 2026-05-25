package com.declaration.domain

data class SelfView(
    val id: PlayerId,
    val team: TeamId,
    val seat: Int,
    val hand: Set<CardId>,
)

data class OpponentView(
    val id: PlayerId,
    val team: TeamId,
    val seat: Int,
    val handSize: Int,
)

data class PlayerView(
    val you: SelfView,
    val others: List<OpponentView>,
    val turn: PlayerId,
    val phase: Phase,
    val winner: TeamId?,
    val capturedDecks: Map<DeckId, TeamId>,
)
