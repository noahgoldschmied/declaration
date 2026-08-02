package com.declaration.bot

import com.declaration.domain.Action
import com.declaration.domain.AskOutcome
import com.declaration.domain.CardId
import com.declaration.domain.DeckCatalog
import com.declaration.domain.DeckId
import com.declaration.domain.Event
import com.declaration.domain.OpponentView
import com.declaration.domain.PlayerId
import com.declaration.domain.PlayerView
import com.declaration.protocol.BotDifficulty
import kotlin.random.Random

/**
 * Pure per-bot belief state and decision logic — no coroutines, no Spring, no [com.declaration.room.Room]
 * reference, so it unit-tests exactly like the domain engine. [observe] is the single entry point: feed it
 * each [PlayerView]/[Event] pair as it arrives and it returns the next [Action] to submit, or null if the
 * bot has nothing to do yet.
 *
 * Belief is built from three sources, matching what a sharp human player would track:
 *  - the bot's own hand (always exact)
 *  - HIT/SELF_OVERLAP outcomes (the asker holds that card, definitely)
 *  - MISS outcomes (neither asker nor target holds that card — an elimination)
 *
 * Two mechanisms turn eliminations into certainty beyond direct observation:
 *  - elimination-by-exhaustion: a card has 5 possible holders besides the bot itself; once MISS events
 *    eliminate 4, the 5th is known.
 *  - ask-reveals-membership: the engine only allows asking for a card from a deck you hold >=1 card in
 *    (see DeclarationEngine.applyAsk's "you don't hold any card from that deck" check), so *any* Ask event
 *    (HIT or MISS; SELF_OVERLAP already fully resolves via the asked card itself) proves its asker holds
 *    >=1 of that deck's other 5 cards. Tracked as a per-(player, deck) constraint that shrinks as other
 *    facts arrive, promoted to a known holder once it collapses to exactly one candidate.
 */
class BotBrain(
    private val random: Random,
    private val difficulty: BotDifficulty,
) {
    private val knownHolder = mutableMapOf<CardId, PlayerId>()
    private val ruledOut = mutableMapOf<CardId, MutableSet<PlayerId>>()
    private val constraints = mutableMapOf<Pair<PlayerId, DeckId>, MutableSet<CardId>>()

    // The deck of this bot's own most recent ask. Humans tend to keep asking within the suit
    // they're already "going down" rather than hopping decks every turn, so chooseAsk()'s
    // guessing fallback prefers to continue here when it's still a live option. Tracked
    // unconditionally (not subject to the forget roll below) -- remembering your own last move
    // isn't a deduction that should be forgettable the way inferences about other players are.
    private var focusDeck: DeckId? = null

    fun observe(view: PlayerView, events: List<Event>): Action? {
        val allPlayers = view.others.map { it.id }.toSet() + view.you.id
        forgetCapturedDecks(view)
        seedOwnHand(view)
        for (event in events) {
            if (event !is Event.Ask) continue
            if (event.asker == view.you.id) {
                focusDeck = DeckCatalog.deckByCard.getValue(event.card)
            }
            if (random.nextDouble() < difficulty.forgetRate) continue
            recordAsk(event)
        }
        propagate(allPlayers)

        declareIfCertain(view)?.let { return it }
        if (view.turn != view.you.id || view.you.hand.isEmpty()) return null
        return chooseAsk(view)
    }

    private fun forgetCapturedDecks(view: PlayerView) {
        for (deck in view.capturedDecks.keys) {
            for (card in DeckCatalog.cardsByDeck.getValue(deck)) {
                knownHolder.remove(card)
                ruledOut.remove(card)
            }
            constraints.keys.filter { it.second == deck }.forEach { constraints.remove(it) }
        }
    }

    private fun seedOwnHand(view: PlayerView) {
        for (card in DeckCatalog.allCards) {
            if (card in view.you.hand) {
                knownHolder[card] = view.you.id
            } else if (knownHolder[card] != view.you.id) {
                ruledOut.getOrPut(card) { mutableSetOf() }.add(view.you.id)
            }
        }
    }

    private fun recordAsk(event: Event.Ask) {
        when (event.outcome) {
            AskOutcome.HIT, AskOutcome.SELF_OVERLAP -> knownHolder[event.card] = event.asker
            AskOutcome.MISS -> {
                // Ground-truth proof neither party holds this card right now -- discard any
                // earlier belief to the contrary (e.g. a stale fact from before the card changed
                // hands via an ask this bot forgot). Without this, a wrong knownHolder entry
                // never self-corrects: chooseAsk's opportunistic loop trusts it unconditionally,
                // so the bot repeats the exact same disproven ask forever instead of learning
                // from the MISS it just received.
                if (knownHolder[event.card] == event.asker || knownHolder[event.card] == event.asked) {
                    knownHolder.remove(event.card)
                }
                ruledOut.getOrPut(event.card) { mutableSetOf() }.add(event.asker)
                ruledOut.getOrPut(event.card) { mutableSetOf() }.add(event.asked)
            }
        }
        if (event.outcome == AskOutcome.SELF_OVERLAP) return
        val deck = DeckCatalog.deckByCard.getValue(event.card)
        val otherCards = DeckCatalog.cardsByDeck.getValue(deck) - event.card
        constraints[event.asker to deck] = otherCards.toMutableSet()
    }

    private fun propagate(allPlayers: Set<PlayerId>) {
        var changed = true
        while (changed) {
            changed = false

            for (card in DeckCatalog.allCards) {
                if (card in knownHolder) continue
                val excluded = ruledOut[card] ?: continue
                val remaining = allPlayers - excluded
                if (remaining.size == 1) {
                    knownHolder[card] = remaining.single()
                    changed = true
                }
            }

            val resolved = mutableListOf<Pair<PlayerId, DeckId>>()
            for ((key, candidates) in constraints) {
                val (player, _) = key
                if (candidates.any { knownHolder[it] == player }) {
                    resolved += key
                    continue
                }
                candidates.removeAll { card -> knownHolder[card] != null || ruledOut[card]?.contains(player) == true }
                when {
                    candidates.size == 1 -> {
                        knownHolder[candidates.first()] = player
                        resolved += key
                        changed = true
                    }
                    candidates.isEmpty() -> resolved += key
                }
            }
            resolved.forEach { constraints.remove(it) }
        }
    }

    private fun declareIfCertain(view: PlayerView): Action.Declare? {
        val teammateIds = view.others.filter { it.team == view.you.team }.map { it.id }.toSet() + view.you.id
        for ((deck, cards) in DeckCatalog.cardsByDeck) {
            if (deck in view.capturedDecks) continue
            val assignments = mutableMapOf<CardId, PlayerId>()
            for (card in cards) {
                val holder = knownHolder[card] ?: break
                if (holder !in teammateIds) break
                assignments[card] = holder
            }
            if (assignments.size == cards.size) return Action.Declare(deck, assignments)
        }
        return null
    }

    private fun chooseAsk(view: PlayerView): Action.Ask? {
        val myHand = view.you.hand
        val askableDecks = myHand.map { DeckCatalog.deckByCard.getValue(it) }.toSet() - view.capturedDecks.keys
        if (askableDecks.isEmpty()) return null

        val opponents = view.others.filter { it.team != view.you.team }
        if (opponents.isEmpty()) return null
        val opponentHandSize = opponents.associate { it.id to it.handSize }

        for (deck in askableDecks) {
            for (card in DeckCatalog.cardsByDeck.getValue(deck)) {
                if (card in myHand) continue
                val holder = knownHolder[card] ?: continue
                if ((opponentHandSize[holder] ?: 0) > 0) return Action.Ask(holder, card)
            }
        }

        // Prefer to keep guessing within the deck we're already pursuing (our own last ask) --
        // matches how a human sticks to a suit they're "going down" instead of hopping decks
        // every turn -- and only fall back to the full pool once that deck has nothing left.
        val focus = focusDeck?.takeIf { it in askableDecks }
        if (focus != null) {
            val focusPool = guessPool(setOf(focus), myHand, opponents)
            if (focusPool.isNotEmpty()) return focusPool[random.nextInt(focusPool.size)]
        }

        val pool = guessPool(askableDecks, myHand, opponents)
        if (pool.isEmpty()) return null
        return pool[random.nextInt(pool.size)]
    }

    private fun guessPool(decks: Set<DeckId>, myHand: Set<CardId>, opponents: List<OpponentView>): List<Action.Ask> {
        val safeCandidates = mutableListOf<Action.Ask>()
        val allCandidates = mutableListOf<Action.Ask>()
        for (deck in decks) {
            for (card in DeckCatalog.cardsByDeck.getValue(deck)) {
                if (card in myHand || knownHolder[card] != null) continue
                val excluded = ruledOut[card] ?: emptySet()
                for (opp in opponents) {
                    val ask = Action.Ask(opp.id, card)
                    allCandidates += ask
                    if (opp.id !in excluded && opp.handSize > 0) safeCandidates += ask
                }
            }
        }
        return safeCandidates.ifEmpty { allCandidates }
    }
}
