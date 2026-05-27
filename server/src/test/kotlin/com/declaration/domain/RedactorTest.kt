package com.declaration.domain

import com.declaration.domain.GameStates.ALICE
import com.declaration.domain.GameStates.BOB
import com.declaration.domain.GameStates.CHARLIE
import com.declaration.domain.GameStates.DAN
import com.declaration.domain.GameStates.EVE
import com.declaration.domain.GameStates.FRANK
import kotlin.test.Test
import kotlin.test.assertEquals

class RedactorTest {

    private val state = GameStates.of(
        hands = listOf(
            Triple(ALICE,   TEAM_RED,  setOf(CardId("2S"), CardId("3S"))),
            Triple(BOB,     TEAM_BLUE, setOf(CardId("9H"))),
            Triple(CHARLIE, TEAM_RED,  setOf(CardId("4S"), CardId("5S"), CardId("AH"))),
            Triple(DAN,     TEAM_BLUE, setOf(CardId("TH"))),
            Triple(EVE,     TEAM_RED,  setOf(CardId("6S"), CardId("7S"))),
            Triple(FRANK,   TEAM_BLUE, setOf(CardId("JH"))),
        ),
        turn = BOB,
        capturedDecks = mapOf(DeckId("LOW_C") to TEAM_RED),
    )

    @Test
    fun `view shows viewer's full hand`() {
        val view = Redactor.viewFor(state, ALICE)
        assertEquals(setOf(CardId("2S"), CardId("3S")), view.you.hand)
        assertEquals(ALICE, view.you.id)
        assertEquals(TEAM_RED, view.you.team)
        assertEquals(0, view.you.seat)
    }

    @Test
    fun `view exposes only hand size for every other player including teammates`() {
        val view = Redactor.viewFor(state, ALICE)
        assertEquals(5, view.others.size)
        val byId = view.others.associateBy { it.id }
        assertEquals(1, byId[BOB]!!.handSize)
        assertEquals(3, byId[CHARLIE]!!.handSize)
        assertEquals(1, byId[DAN]!!.handSize)
        assertEquals(2, byId[EVE]!!.handSize)
        assertEquals(1, byId[FRANK]!!.handSize)
    }

    @Test
    fun `view preserves turn, phase, winner, captured decks`() {
        val view = Redactor.viewFor(state, ALICE)
        assertEquals(BOB, view.turn)
        assertEquals(Phase.PLAYING, view.phase)
        assertEquals(null, view.winner)
        assertEquals(mapOf(DeckId("LOW_C") to TEAM_RED), view.capturedDecks)
    }

    @Test
    fun `view for ended game includes winner`() {
        val ended = state.copy(phase = Phase.ENDED, winner = TEAM_RED)
        val view = Redactor.viewFor(ended, ALICE)
        assertEquals(Phase.ENDED, view.phase)
        assertEquals(TEAM_RED, view.winner)
    }
}
