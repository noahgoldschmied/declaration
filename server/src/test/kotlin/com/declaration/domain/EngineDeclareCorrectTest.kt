package com.declaration.domain

import com.declaration.domain.GameStates.ALICE
import com.declaration.domain.GameStates.BOB
import com.declaration.domain.GameStates.CHARLIE
import com.declaration.domain.GameStates.DAN
import com.declaration.domain.GameStates.EVE
import com.declaration.domain.GameStates.FRANK
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineDeclareCorrectTest {

    private val engine: Engine = DeclarationEngine()

    private val state = GameStates.of(
        hands = listOf(
            Triple(ALICE,   TEAM_RED,  setOf(CardId("2S"), CardId("3S"))),
            Triple(BOB,     TEAM_BLUE, setOf(CardId("9H"))),
            Triple(CHARLIE, TEAM_RED,  setOf(CardId("4S"), CardId("5S"))),
            Triple(DAN,     TEAM_BLUE, setOf(CardId("TH"))),
            Triple(EVE,     TEAM_RED,  setOf(CardId("6S"), CardId("7S"))),
            Triple(FRANK,   TEAM_BLUE, setOf(CardId("JH"))),
        ),
        turn = BOB, // declares are interrupts; turn unchanged
    )

    private val correct = mapOf(
        CardId("2S") to ALICE,
        CardId("3S") to ALICE,
        CardId("4S") to CHARLIE,
        CardId("5S") to CHARLIE,
        CardId("6S") to EVE,
        CardId("7S") to EVE,
    )

    @Test
    fun `correct declare awards deck to declarer's team`() {
        val result = engine.apply(state, ALICE, Action.Declare(DeckId("LOW_S"), correct)) as ActionResult.Ok
        assertEquals(TEAM_RED, result.newState.capturedDecks[DeckId("LOW_S")])
    }

    @Test
    fun `correct declare removes all 6 cards from player hands`() {
        val result = engine.apply(state, ALICE, Action.Declare(DeckId("LOW_S"), correct)) as ActionResult.Ok
        val lowSpades = DeckCatalog.cardsByDeck[DeckId("LOW_S")]!!
        result.newState.players.forEach { p ->
            assertTrue(p.hand.none { it in lowSpades }, "${p.id.value} still holds a low-spade")
        }
    }

    @Test
    fun `correct declare preserves turn`() {
        val result = engine.apply(state, ALICE, Action.Declare(DeckId("LOW_S"), correct)) as ActionResult.Ok
        assertEquals(BOB, result.newState.turn)
    }

    @Test
    fun `a declare that empties the turn-holder's hand skips their turn instead of freezing it`() {
        // Regression test for a real observed freeze: BOB is on turn holding cards from a deck
        // that someone else (FRANK, BOB's own teammate) then declares -- incorrectly, but that
        // doesn't matter: the deck's cards get stripped from every hand either way. Declares are
        // off-turn interrupts, so this is legal mid-BOB's-turn, but it can strip BOB down to zero
        // cards. If `state.turn` is left pointing at BOB, nobody can ever act again: BOB can't
        // Ask with an empty hand, and it's nobody else's turn either.
        val stateWithBobOnLowS = GameStates.of(
            hands = listOf(
                Triple(ALICE,   TEAM_RED,  setOf(CardId("2S"), CardId("3S"))),
                Triple(BOB,     TEAM_BLUE, setOf(CardId("4S"), CardId("5S"))),
                Triple(CHARLIE, TEAM_RED,  emptySet()),
                Triple(DAN,     TEAM_BLUE, setOf(CardId("TH"))),
                Triple(EVE,     TEAM_RED,  setOf(CardId("6S"), CardId("7S"))),
                Triple(FRANK,   TEAM_BLUE, setOf(CardId("JH"))),
            ),
            turn = BOB,
        )
        // FRANK (BOB's own teammate) declares LOW_S, wrongly claiming he holds all 6 himself --
        // assignments must name teammates, and correctness doesn't affect whether cards get
        // stripped from hands, so this is the simplest legal way to trigger the scenario.
        val wrongAssignments = DeckCatalog.cardsByDeck.getValue(DeckId("LOW_S")).associateWith { FRANK }

        val result = engine.apply(stateWithBobOnLowS, FRANK, Action.Declare(DeckId("LOW_S"), wrongAssignments)) as ActionResult.Ok

        assertEquals(false, (result.events.single() as Event.Declaration).correct)
        assertEquals(0, result.newState.playerById(BOB)!!.hand.size)
        assertTrue(result.newState.turn != BOB, "turn must move off a player left with no cards")
        // Seats: 0 Alice, 1 Bob(now empty), 2 Charlie(empty), 3 Dan(has TH) -- next non-empty seat.
        assertEquals(DAN, result.newState.turn)
    }

    @Test
    fun `correct declare emits Event_Declaration with correct=true and awardedTo=declarer team`() {
        val result = engine.apply(state, ALICE, Action.Declare(DeckId("LOW_S"), correct)) as ActionResult.Ok
        val ev = result.events.single() as Event.Declaration
        assertEquals(ALICE, ev.declarer)
        assertEquals(DeckId("LOW_S"), ev.deck)
        assertEquals(true, ev.correct)
        assertEquals(TEAM_RED, ev.awardedTo)
        assertEquals(correct, ev.assignments)
    }
}
