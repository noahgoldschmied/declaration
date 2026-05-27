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
