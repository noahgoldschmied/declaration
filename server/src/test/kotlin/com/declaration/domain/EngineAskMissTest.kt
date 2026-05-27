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

class EngineAskMissTest {

    private val engine: Engine = DeclarationEngine()

    private val state = GameStates.of(
        hands = listOf(
            // Alice holds 2S (LOW_S). Asks Bob for 3S, but Charlie holds 3S — miss.
            Triple(ALICE,   TEAM_RED,  setOf(CardId("2S"))),
            Triple(BOB,     TEAM_BLUE, setOf(CardId("5H"))),
            Triple(CHARLIE, TEAM_RED,  setOf(CardId("3S"))),
            Triple(DAN,     TEAM_BLUE, setOf(CardId("4H"))),
            Triple(EVE,     TEAM_RED,  setOf(CardId("6H"))),
            Triple(FRANK,   TEAM_BLUE, setOf(CardId("7H"))),
        ),
        turn = ALICE,
    )

    @Test
    fun `miss transfers no cards`() {
        val before = state
        val result = engine.apply(state, ALICE, Action.Ask(BOB, CardId("3S"))) as ActionResult.Ok
        before.players.zip(result.newState.players).forEach { (b, a) ->
            assertEquals(b.hand, a.hand, "hand for ${b.id.value} should be unchanged")
        }
    }

    @Test
    fun `miss passes turn to the asked player`() {
        val result = engine.apply(state, ALICE, Action.Ask(BOB, CardId("3S"))) as ActionResult.Ok
        assertEquals(BOB, result.newState.turn)
    }

    @Test
    fun `miss emits Event_Ask with MISS outcome`() {
        val result = engine.apply(state, ALICE, Action.Ask(BOB, CardId("3S"))) as ActionResult.Ok
        val ev = result.events.single() as Event.Ask
        assertEquals(AskOutcome.MISS, ev.outcome)
        assertEquals(ALICE, ev.asker)
        assertEquals(BOB, ev.asked)
        assertEquals(CardId("3S"), ev.card)
    }
}
