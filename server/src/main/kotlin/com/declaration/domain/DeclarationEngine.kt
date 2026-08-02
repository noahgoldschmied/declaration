package com.declaration.domain

class DeclarationEngine : Engine {

    override fun apply(state: GameState, actor: PlayerId, action: Action): ActionResult =
        when (action) {
            is Action.Ask -> applyAsk(state, actor, action)
            is Action.Declare -> applyDeclare(state, actor, action)
        }

    private fun applyDeclare(state: GameState, actor: PlayerId, declare: Action.Declare): ActionResult {
        if (state.phase != Phase.PLAYING) return ActionResult.Invalid("game has ended")

        val declarer = state.playerById(actor)
            ?: return ActionResult.Invalid("unknown player")

        val deckCards = DeckCatalog.cardsByDeck[declare.deck]
            ?: return ActionResult.Invalid("unknown deck")

        if (declare.deck in state.capturedDecks) {
            return ActionResult.Invalid("deck already captured")
        }

        if (declare.assignments.keys != deckCards) {
            return ActionResult.Invalid("assignments must name exactly the 6 cards of the deck")
        }

        val teammateIds = state.players.filter { it.team == declarer.team }.map { it.id }.toSet()
        if (declare.assignments.values.any { it !in teammateIds }) {
            return ActionResult.Invalid("can only assign cards to teammates")
        }

        val actualHolders = deckCards.associateWith { card -> state.holderOf(card)!! }
        val correct = declare.assignments.all { (card, claimed) -> actualHolders[card] == claimed }
        val awardedTo = if (correct) declarer.team else opposingTeam(declarer.team)

        // Remove all 6 cards of this deck from every player's hand.
        val newPlayers = state.players.map { p ->
            p.copy(hand = p.hand - deckCards)
        }

        val newCaptured = state.capturedDecks + (declare.deck to awardedTo)
        val awardedCount = newCaptured.count { it.value == awardedTo }
        val gameEnded = awardedCount >= 5

        val newPhase = if (gameEnded) Phase.ENDED else state.phase
        val newWinner = if (gameEnded) awardedTo else state.winner
        // A declare can be submitted by anyone at any time, not just the player on turn, and it
        // can empty out the current turn-holder's hand (e.g. they just got the deck's last cards
        // via a HIT and then someone declares that same deck out from under them). Without
        // re-checking here, `state.turn` would keep pointing at a player who now holds no cards
        // and can never legally Ask -- and since nobody else is on turn either, the game freezes
        // with no valid move for anyone.
        val nextTurn = if (gameEnded) state.turn else advancePastEmpty(newPlayers, state.turn)

        val event = Event.Declaration(
            declarer = actor,
            deck = declare.deck,
            assignments = declare.assignments,
            correct = correct,
            awardedTo = awardedTo,
            actualHolders = actualHolders,
        )

        return ActionResult.Ok(
            newState = state.copy(
                players = newPlayers,
                capturedDecks = newCaptured,
                phase = newPhase,
                winner = newWinner,
                turn = nextTurn,
            ),
            events = listOf(event),
        )
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
