package com.declaration.domain

object GameStates {

    /**
     * Build a GameState from explicit hand assignments. Players appear in seat order 0..5
     * matching the order of `hands`. Teams must be supplied for each player.
     *
     * The hands across all 6 players must collectively contain a subset of [DeckCatalog.allCards];
     * any card not assigned is assumed to be in a captured deck.
     */
    fun of(
        hands: List<Triple<PlayerId, TeamId, Set<CardId>>>,
        turn: PlayerId,
        capturedDecks: Map<DeckId, TeamId> = emptyMap(),
        phase: Phase = Phase.PLAYING,
        winner: TeamId? = null,
    ): GameState {
        require(hands.size == 6) { "need 6 players, got ${hands.size}" }
        val players = hands.mapIndexed { seat, (id, team, hand) ->
            Player(id = id, team = team, seat = seat, hand = hand)
        }
        return GameState(
            players = players,
            turn = turn,
            capturedDecks = capturedDecks,
            phase = phase,
            winner = winner,
        )
    }

    val ALICE = PlayerId("alice")
    val BOB = PlayerId("bob")
    val CHARLIE = PlayerId("charlie")
    val DAN = PlayerId("dan")
    val EVE = PlayerId("eve")
    val FRANK = PlayerId("frank")
}
