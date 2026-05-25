package com.declaration.domain

class DeclarationEngine : Engine {

    override fun apply(state: GameState, actor: PlayerId, action: Action): ActionResult =
        when (action) {
            is Action.Ask -> applyAsk(state, actor, action)
            is Action.Declare -> ActionResult.Invalid("not implemented")
        }

    private fun applyAsk(state: GameState, actor: PlayerId, ask: Action.Ask): ActionResult {
        if (state.phase != Phase.PLAYING) return ActionResult.Invalid("game has ended")
        if (actor != state.turn) return ActionResult.Invalid("not your turn")

        val actorPlayer = state.playerById(actor)
            ?: return ActionResult.Invalid("unknown player")
        if (actorPlayer.hand.isEmpty()) return ActionResult.Invalid("you have no cards")

        if (ask.target == actor) return ActionResult.Invalid("cannot ask yourself")
        val target = state.playerById(ask.target)
            ?: return ActionResult.Invalid("unknown player")
        if (target.team == actorPlayer.team) return ActionResult.Invalid("cannot ask a teammate")

        val deckOfCard = DeckCatalog.deckByCard[ask.card]
            ?: return ActionResult.Invalid("unknown card")
        val deckCards = DeckCatalog.cardsByDeck[deckOfCard]!!
        if (actorPlayer.hand.none { it in deckCards }) {
            return ActionResult.Invalid("you don't hold any card from that deck")
        }

        val holder = state.holderOf(ask.card)
        val outcome = when (holder) {
            target.id -> AskOutcome.HIT
            actor -> AskOutcome.SELF_OVERLAP
            else -> AskOutcome.MISS
        }

        val newPlayers = when (outcome) {
            AskOutcome.HIT -> state.players.map { p ->
                when (p.id) {
                    actor -> p.copy(hand = p.hand + ask.card)
                    target.id -> p.copy(hand = p.hand - ask.card)
                    else -> p
                }
            }
            AskOutcome.MISS, AskOutcome.SELF_OVERLAP -> state.players
        }

        val nominalNext = when (outcome) {
            AskOutcome.HIT -> actor
            AskOutcome.MISS, AskOutcome.SELF_OVERLAP -> target.id
        }
        val nextTurn = advancePastEmpty(newPlayers, nominalNext)

        val event = Event.Ask(
            asker = actor,
            asked = target.id,
            card = ask.card,
            outcome = outcome,
        )

        return ActionResult.Ok(
            newState = state.copy(players = newPlayers, turn = nextTurn),
            events = listOf(event),
        )
    }

    /**
     * If [from] has a non-empty hand, return [from]. Otherwise walk forward by seat
     * (wrapping at 5 → 0) until a player with cards is found. If no player has cards,
     * return [from] unchanged — no Ask will be valid in that state anyway.
     */
    private fun advancePastEmpty(players: List<Player>, from: PlayerId): PlayerId {
        val bySeat = players.sortedBy { it.seat }
        val startSeat = bySeat.first { it.id == from }.seat
        if (bySeat[startSeat].hand.isNotEmpty()) return from
        for (offset in 1..5) {
            val candidate = bySeat[(startSeat + offset) % 6]
            if (candidate.hand.isNotEmpty()) return candidate.id
        }
        return from
    }
}
