package com.declaration.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Action {
    @Serializable
    @SerialName("Ask")
    data class Ask(val target: PlayerId, val card: CardId) : Action()

    @Serializable
    @SerialName("Declare")
    data class Declare(val deck: DeckId, val assignments: Map<CardId, PlayerId>) : Action()
}
