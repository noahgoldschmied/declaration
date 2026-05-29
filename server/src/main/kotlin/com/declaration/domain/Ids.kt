package com.declaration.domain

import kotlinx.serialization.Serializable

@Serializable @JvmInline value class PlayerId(val value: String)
@Serializable @JvmInline value class TeamId(val value: String)
@Serializable @JvmInline value class CardId(val value: String)
@Serializable @JvmInline value class DeckId(val value: String)
