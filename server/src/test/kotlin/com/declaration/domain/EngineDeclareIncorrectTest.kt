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

class EngineDeclareIncorrectTest {

    private val engine: Engine = DeclarationEngine()

    private val state = GameStates.of(
        hands = listOf(
            // 7S is actually with EVE, but declarer will assign it to ALICE -> wrong.
            Triple(ALICE,   TEAM_RED,  setOf(CardId("2S"), CardId("3S"))),
            Triple(BOB,     TEAM_BLUE, setOf(CardId("9H"))),
            Triple(CHARLIE, TEAM_RED,  setOf(CardId("4S"), CardId("5S"))),
            Triple(DAN,     TEAM_BLUE, setOf(CardId("TH"))),
            Triple(EVE,     TEAM_RED,  setOf(CardId("6S"), CardId("7S"))),
            Triple(FRANK,   TEAM_BLUE, setOf(CardId("JH"))),
        ),
        turn = BOB,
    )

    private val wrong = mapOf(
        CardId("2S") to ALICE,
        CardId("3S") to ALICE,
        CardId("4S") to CHARLIE,
        CardId("5S") to CHARLIE,
        CardId("6S") to EVE,
        CardId("7S") to ALICE, // wrong — actually with EVE
    )

    @Test
    fun `incorrect declare awards deck to opposing team`() {
        val result = engine.apply(state, ALICE, Action.Declare(DeckId("LOW_S"), wrong)) as ActionResult.Ok
        assertEquals(TEAM_BLUE, result.newState.capturedDecks[DeckId("LOW_S")])
    }

    @Test
    fun `incorrect declare still removes all 6 cards from hands`() {
        val result = engine.apply(state, ALICE, Action.Declare(DeckId("LOW_S"), wrong)) as ActionResult.Ok
        val lowSpades = DeckCatalog.cardsByDeck[DeckId("LOW_S")]!!
        result.newState.players.forEach { p ->
            assertTrue(p.hand.none { it in lowSpades })
        }
    }

    @Test
    fun `incorrect declare emits Event with correct=false and awardedTo=opposing team`() {
        val result = engine.apply(state, ALICE, Action.Declare(DeckId("LOW_S"), wrong)) as ActionResult.Ok
        val ev = result.events.single() as Event.Declaration
        assertEquals(false, ev.correct)
        assertEquals(TEAM_BLUE, ev.awardedTo)
    }

    @Test
    fun `declare assigning a card actually held by opponent is awarded to opposing team`() {
        // Set up: declarer says CHARLIE has 7S, but EVE has it. Both Red — but the card
        // is on Red's team, just wrong teammate. Still incorrect.
        val almost = wrong.toMutableMap().apply { put(CardId("7S"), CHARLIE) }
        val result = engine.apply(state, ALICE, Action.Declare(DeckId("LOW_S"), almost)) as ActionResult.Ok
        val ev = result.events.single() as Event.Declaration
        assertEquals(false, ev.correct)
        assertEquals(TEAM_BLUE, ev.awardedTo)
    }
}
