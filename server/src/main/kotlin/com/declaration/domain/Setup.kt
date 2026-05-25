package com.declaration.domain

import kotlin.random.Random

object Setup {

    /**
     * Build the initial GameState. Players appear at seats 0..5 in the order given.
     * The deck is shuffled with [random] and dealt 9-9-9-9-9-9. The starting player
     * is also chosen via [random].
     */
    fun newGame(players: List<Pair<PlayerId, TeamId>>, random: Random): GameState {
        require(players.size == 6) { "Declaration requires exactly 6 players" }

        val shuffled = DeckCatalog.allCards.toList().shuffled(random)
        val hands = (0..5).map { seat ->
            shuffled.subList(seat * 9, seat * 9 + 9).toSet()
        }

        val seated = players.mapIndexed { seat, (id, team) ->
            Player(id = id, team = team, seat = seat, hand = hands[seat])
        }

        val startSeat = random.nextInt(6)
        val starter = seated[startSeat].id

        return GameState(
            players = seated,
            turn = starter,
            capturedDecks = emptyMap(),
            phase = Phase.PLAYING,
            winner = null,
        )
    }
}
