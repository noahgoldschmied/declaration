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

class EngineWinConditionTest {

    private val engine: Engine = DeclarationEngine()

    private fun stateWithRedAt4(): GameState = GameStates.of(
        hands = listOf(
            Triple(ALICE,   TEAM_RED,  setOf(CardId("2S"), CardId("3S"))),
            Triple(BOB,     TEAM_BLUE, setOf(CardId("9H"))),
            Triple(CHARLIE, TEAM_RED,  setOf(CardId("4S"), CardId("5S"))),
            Triple(DAN,     TEAM_BLUE, setOf(CardId("TH"))),
            Triple(EVE,     TEAM_RED,  setOf(CardId("6S"), CardId("7S"))),
            Triple(FRANK,   TEAM_BLUE, setOf(CardId("JH"))),
        ),
        turn = BOB,
        capturedDecks = mapOf(
            DeckId("LOW_H") to TEAM_RED,
            DeckId("LOW_D") to TEAM_RED,
            DeckId("LOW_C") to TEAM_RED,
            DeckId("HIGH_S") to TEAM_RED,
        ),
    )

    private val correctLowSpades = mapOf(
        CardId("2S") to ALICE,
        CardId("3S") to ALICE,
        CardId("4S") to CHARLIE,
        CardId("5S") to CHARLIE,
        CardId("6S") to EVE,
        CardId("7S") to EVE,
    )

    @Test
    fun `fifth deck capture transitions phase to ENDED`() {
        val result = engine.apply(stateWithRedAt4(), ALICE, Action.Declare(DeckId("LOW_S"), correctLowSpades)) as ActionResult.Ok
        assertEquals(Phase.ENDED, result.newState.phase)
    }

    @Test
    fun `fifth deck capture sets winner to the awarded team`() {
        val result = engine.apply(stateWithRedAt4(), ALICE, Action.Declare(DeckId("LOW_S"), correctLowSpades)) as ActionResult.Ok
        assertEquals(TEAM_RED, result.newState.winner)
    }

    @Test
    fun `wrong declare that gives opponent their fifth deck ends the game in their favor`() {
        val blueAt4 = stateWithRedAt4().copy(
            capturedDecks = mapOf(
                DeckId("LOW_H") to TEAM_BLUE,
                DeckId("LOW_D") to TEAM_BLUE,
                DeckId("LOW_C") to TEAM_BLUE,
                DeckId("HIGH_S") to TEAM_BLUE,
            ),
        )
        val wrong = correctLowSpades.toMutableMap().apply { put(CardId("7S"), CHARLIE) }
        val result = engine.apply(blueAt4, ALICE, Action.Declare(DeckId("LOW_S"), wrong)) as ActionResult.Ok
        assertEquals(Phase.ENDED, result.newState.phase)
        assertEquals(TEAM_BLUE, result.newState.winner)
    }

    @Test
    fun `actions are rejected after game has ended`() {
        val ended = stateWithRedAt4().copy(phase = Phase.ENDED, winner = TEAM_RED)
        val askResult = engine.apply(ended, BOB, Action.Ask(ALICE, CardId("2S")))
        assertTrue(askResult is ActionResult.Invalid)
        assertEquals("game has ended", askResult.reason)

        val declareResult = engine.apply(ended, ALICE, Action.Declare(DeckId("HIGH_H"), emptyMap()))
        assertTrue(declareResult is ActionResult.Invalid)
        assertEquals("game has ended", declareResult.reason)
    }
}
