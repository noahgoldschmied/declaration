package com.declaration.domain

import com.declaration.domain.GameStates.ALICE
import com.declaration.domain.GameStates.BOB
import com.declaration.domain.GameStates.CHARLIE
import com.declaration.domain.GameStates.DAN
import com.declaration.domain.GameStates.EVE
import com.declaration.domain.GameStates.FRANK
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EngineAskHitTest {

    private val engine: Engine = DeclarationEngine()

    private val state = GameStates.of(
        hands = listOf(
            Triple(ALICE,   TEAM_RED,  setOf(CardId("2S"))),
            Triple(BOB,     TEAM_BLUE, setOf(CardId("3S"))),
            Triple(CHARLIE, TEAM_RED,  setOf(CardId("4S"))),
            Triple(DAN,     TEAM_BLUE, setOf(CardId("5S"))),
            Triple(EVE,     TEAM_RED,  setOf(CardId("6S"))),
            Triple(FRANK,   TEAM_BLUE, setOf(CardId("7S"))),
        ),
        turn = ALICE,
    )

    @Test
    fun `hit transfers card from target to asker`() {
        val result = engine.apply(state, ALICE, Action.Ask(BOB, CardId("3S")))
        assertTrue(result is ActionResult.Ok)
        val s = result.newState
        assertTrue(CardId("3S") in s.playerById(ALICE)!!.hand)
        assertTrue(CardId("3S") !in s.playerById(BOB)!!.hand)
    }

    @Test
    fun `hit keeps the asker's turn`() {
        val result = engine.apply(state, ALICE, Action.Ask(BOB, CardId("3S"))) as ActionResult.Ok
        assertEquals(ALICE, result.newState.turn)
    }

    @Test
    fun `hit emits Event_Ask with HIT outcome`() {
        val result = engine.apply(state, ALICE, Action.Ask(BOB, CardId("3S"))) as ActionResult.Ok
        assertEquals(1, result.events.size)
        val ev = result.events.single() as Event.Ask
        assertEquals(ALICE, ev.asker)
        assertEquals(BOB, ev.asked)
        assertEquals(CardId("3S"), ev.card)
        assertEquals(AskOutcome.HIT, ev.outcome)
    }

    @Test
    fun `hit preserves total card count across all hands`() {
        val before = state.players.sumOf { it.hand.size }
        val result = engine.apply(state, ALICE, Action.Ask(BOB, CardId("3S"))) as ActionResult.Ok
        val after = result.newState.players.sumOf { it.hand.size }
        assertEquals(before, after)
    }

    @Test
    fun `hit preserves phase and captured decks`() {
        val result = engine.apply(state, ALICE, Action.Ask(BOB, CardId("3S"))) as ActionResult.Ok
        assertEquals(Phase.PLAYING, result.newState.phase)
        assertEquals(emptyMap(), result.newState.capturedDecks)
        assertEquals(null, result.newState.winner)
    }
}
