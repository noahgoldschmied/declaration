package com.declaration.domain

import kotlinx.serialization.Serializable

@Serializable
data class SelfView(
    val id: PlayerId,
    val team: TeamId,
    val seat: Int,
    val hand: Set<CardId>,
)

@Serializable
data class OpponentView(
    val id: PlayerId,
    val team: TeamId,
    val seat: Int,
    val handSize: Int,
)

@Serializable
data class PlayerView(
    val you: SelfView,
    val others: List<OpponentView>,
    val turn: PlayerId,
    val phase: Phase,
    val winner: TeamId?,
    val capturedDecks: Map<DeckId, TeamId>,
)
