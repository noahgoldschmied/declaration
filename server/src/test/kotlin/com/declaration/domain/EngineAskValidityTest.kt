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

class EngineAskValidityTest {

    private val engine: Engine = DeclarationEngine()

    private fun baseState(turn: PlayerId = ALICE): GameState = GameStates.of(
        hands = listOf(
            Triple(ALICE,   TEAM_RED,  setOf(CardId("2S"), CardId("2H"))),
            Triple(BOB,     TEAM_BLUE, setOf(CardId("3S"), CardId("3H"))),
            Triple(CHARLIE, TEAM_RED,  setOf(CardId("4S"), CardId("4H"))),
            Triple(DAN,     TEAM_BLUE, setOf(CardId("5S"), CardId("5H"))),
            Triple(EVE,     TEAM_RED,  setOf(CardId("6S"), CardId("6H"))),
            Triple(FRANK,   TEAM_BLUE, setOf(CardId("7S"), CardId("7H"))),
        ),
        turn = turn,
    )

    @Test
    fun `rejects ask when not your turn`() {
        val result = engine.apply(baseState(turn = ALICE), CHARLIE, Action.Ask(BOB, CardId("3S")))
        assertTrue(result is ActionResult.Invalid)
        assertEquals("not your turn", result.reason)
    }

    @Test
    fun `rejects ask targeting unknown player`() {
        val result = engine.apply(baseState(), ALICE, Action.Ask(PlayerId("ghost"), CardId("3S")))
        assertTrue(result is ActionResult.Invalid)
        assertEquals("unknown player", result.reason)
    }

    @Test
    fun `rejects ask targeting a teammate`() {
        val result = engine.apply(baseState(), ALICE, Action.Ask(CHARLIE, CardId("4S")))
        assertTrue(result is ActionResult.Invalid)
        assertEquals("cannot ask a teammate", result.reason)
    }

    @Test
    fun `rejects ask targeting yourself`() {
        val result = engine.apply(baseState(), ALICE, Action.Ask(ALICE, CardId("3S")))
        assertTrue(result is ActionResult.Invalid)
        assertEquals("cannot ask yourself", result.reason)
    }

    @Test
    fun `rejects ask for unknown card`() {
        val result = engine.apply(baseState(), ALICE, Action.Ask(BOB, CardId("ZZ")))
        assertTrue(result is ActionResult.Invalid)
        assertEquals("unknown card", result.reason)
    }

    @Test
    fun `rejects ask for card from a deck you hold none of`() {
        // Alice holds 2S, 2H — has LOW_S and LOW_H. Asking for 9D (HIGH_D) is invalid.
        val result = engine.apply(baseState(), ALICE, Action.Ask(BOB, CardId("9D")))
        assertTrue(result is ActionResult.Invalid)
        assertEquals("you don't hold any card from that deck", result.reason)
    }

    @Test
    fun `rejects ask after game has ended`() {
        val ended = baseState().copy(phase = Phase.ENDED, winner = TEAM_RED)
        val result = engine.apply(ended, ALICE, Action.Ask(BOB, CardId("3S")))
        assertTrue(result is ActionResult.Invalid)
        assertEquals("game has ended", result.reason)
    }

    @Test
    fun `rejects ask when actor has empty hand`() {
        val empty = GameStates.of(
            hands = listOf(
                Triple(ALICE,   TEAM_RED,  emptySet()),
                Triple(BOB,     TEAM_BLUE, setOf(CardId("3S"))),
                Triple(CHARLIE, TEAM_RED,  setOf(CardId("4S"))),
                Triple(DAN,     TEAM_BLUE, setOf(CardId("5S"))),
                Triple(EVE,     TEAM_RED,  setOf(CardId("6S"))),
                Triple(FRANK,   TEAM_BLUE, setOf(CardId("7S"))),
            ),
            turn = ALICE,
        )
        val result = engine.apply(empty, ALICE, Action.Ask(BOB, CardId("3S")))
        assertTrue(result is ActionResult.Invalid)
        assertEquals("you have no cards", result.reason)
    }
}
