package com.declaration.domain

sealed class Action {
    data class Ask(val target: PlayerId, val card: CardId) : Action()
    data class Declare(val deck: DeckId, val assignments: Map<CardId, PlayerId>) : Action()
}
