package com.declaration.domain

data class Player(
    val id: PlayerId,
    val team: TeamId,
    val seat: Int,
    val hand: Set<CardId>,
)
