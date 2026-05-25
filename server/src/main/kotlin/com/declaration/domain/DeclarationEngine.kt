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

        // Validity passes. Transition deferred to Task 7.
        return ActionResult.Invalid("ask transition not implemented")
    }
}
