package com.declaration.domain

enum class Phase { PLAYING, ENDED }

val TEAM_RED = TeamId("RED")
val TEAM_BLUE = TeamId("BLUE")

fun opposingTeam(team: TeamId): TeamId =
    when (team) {
        TEAM_RED -> TEAM_BLUE
        TEAM_BLUE -> TEAM_RED
        else -> error("unknown team: ${team.value}")
    }

data class GameState(
    val players: List<Player>,
    val turn: PlayerId,
    val capturedDecks: Map<DeckId, TeamId> = emptyMap(),
    val phase: Phase = Phase.PLAYING,
    val winner: TeamId? = null,
) {
    init {
        require(players.size == 6) { "Declaration is a 6-player game; got ${players.size}" }
        require(players.map { it.id }.toSet().size == 6) { "player ids must be unique" }
        require(players.map { it.seat }.toSet() == (0..5).toSet()) { "seats must be 0..5" }
    }

    fun playerById(id: PlayerId): Player? = players.firstOrNull { it.id == id }

    fun holderOf(card: CardId): PlayerId? =
        players.firstOrNull { card in it.hand }?.id
}
