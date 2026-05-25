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

class EngineAskSelfOverlapTest {

    private val engine: Engine = DeclarationEngine()

    private val state = GameStates.of(
        hands = listOf(
            // Alice holds 2S AND 3S. If she asks Bob for 3S, that's self-overlap.
            Triple(ALICE,   TEAM_RED,  setOf(CardId("2S"), CardId("3S"))),
            Triple(BOB,     TEAM_BLUE, setOf(CardId("5H"))),
            Triple(CHARLIE, TEAM_RED,  setOf(CardId("4S"))),
            Triple(DAN,     TEAM_BLUE, setOf(CardId("4H"))),
            Triple(EVE,     TEAM_RED,  setOf(CardId("6H"))),
            Triple(FRANK,   TEAM_BLUE, setOf(CardId("7H"))),
        ),
        turn = ALICE,
    )

    @Test
    fun `self-overlap transfers no cards`() {
        val result = engine.apply(state, ALICE, Action.Ask(BOB, CardId("3S"))) as ActionResult.Ok
        assertTrue(CardId("3S") in result.newState.playerById(ALICE)!!.hand)
        assertTrue(CardId("3S") !in result.newState.playerById(BOB)!!.hand)
    }

    @Test
    fun `self-overlap passes turn to the asked player`() {
        val result = engine.apply(state, ALICE, Action.Ask(BOB, CardId("3S"))) as ActionResult.Ok
        assertEquals(BOB, result.newState.turn)
    }

    @Test
    fun `self-overlap emits Event_Ask with SELF_OVERLAP outcome`() {
        val result = engine.apply(state, ALICE, Action.Ask(BOB, CardId("3S"))) as ActionResult.Ok
        val ev = result.events.single() as Event.Ask
        assertEquals(AskOutcome.SELF_OVERLAP, ev.outcome)
        assertEquals(CardId("3S"), ev.card)
    }
}
