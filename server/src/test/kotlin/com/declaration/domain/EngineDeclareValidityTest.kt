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

class EngineDeclareValidityTest {

    private val engine: Engine = DeclarationEngine()

    private fun stateWithAllLowSpadesOnRed(): GameState = GameStates.of(
        hands = listOf(
            Triple(ALICE,   TEAM_RED,  setOf(CardId("2S"), CardId("3S"))),
            Triple(BOB,     TEAM_BLUE, setOf(CardId("9H"))),
            Triple(CHARLIE, TEAM_RED,  setOf(CardId("4S"), CardId("5S"))),
            Triple(DAN,     TEAM_BLUE, setOf(CardId("TH"))),
            Triple(EVE,     TEAM_RED,  setOf(CardId("6S"), CardId("7S"))),
            Triple(FRANK,   TEAM_BLUE, setOf(CardId("JH"))),
        ),
        turn = BOB, // Doesn't matter; declare is interrupt
    )

    private val lowSpadesCorrect = mapOf(
        CardId("2S") to ALICE,
        CardId("3S") to ALICE,
        CardId("4S") to CHARLIE,
        CardId("5S") to CHARLIE,
        CardId("6S") to EVE,
        CardId("7S") to EVE,
    )

    @Test
    fun `rejects declare for unknown deck`() {
        val result = engine.apply(
            stateWithAllLowSpadesOnRed(), ALICE,
            Action.Declare(DeckId("NOPE"), lowSpadesCorrect),
        )
        assertTrue(result is ActionResult.Invalid)
        assertEquals("unknown deck", result.reason)
    }

    @Test
    fun `rejects declare missing a card`() {
        val incomplete = lowSpadesCorrect - CardId("7S")
        val result = engine.apply(
            stateWithAllLowSpadesOnRed(), ALICE,
            Action.Declare(DeckId("LOW_S"), incomplete),
        )
        assertTrue(result is ActionResult.Invalid)
        assertEquals("assignments must name exactly the 6 cards of the deck", result.reason)
    }

    @Test
    fun `rejects declare with extra card`() {
        val extra = lowSpadesCorrect + (CardId("8S") to ALICE)
        val result = engine.apply(
            stateWithAllLowSpadesOnRed(), ALICE,
            Action.Declare(DeckId("LOW_S"), extra),
        )
        assertTrue(result is ActionResult.Invalid)
        assertEquals("assignments must name exactly the 6 cards of the deck", result.reason)
    }

    @Test
    fun `rejects declare assigning a card to an opponent`() {
        val crossTeam = lowSpadesCorrect + (CardId("7S") to BOB) // overwrites 7S->EVE with 7S->BOB
        val result = engine.apply(
            stateWithAllLowSpadesOnRed(), ALICE,
            Action.Declare(DeckId("LOW_S"), crossTeam),
        )
        assertTrue(result is ActionResult.Invalid)
        assertEquals("can only assign cards to teammates", result.reason)
    }

    @Test
    fun `rejects declare for an already-captured deck`() {
        val state = stateWithAllLowSpadesOnRed().copy(
            capturedDecks = mapOf(DeckId("LOW_S") to TEAM_BLUE),
        )
        val result = engine.apply(state, ALICE, Action.Declare(DeckId("LOW_S"), lowSpadesCorrect))
        assertTrue(result is ActionResult.Invalid)
        assertEquals("deck already captured", result.reason)
    }

    @Test
    fun `rejects declare after game has ended`() {
        val state = stateWithAllLowSpadesOnRed().copy(phase = Phase.ENDED, winner = TEAM_RED)
        val result = engine.apply(state, ALICE, Action.Declare(DeckId("LOW_S"), lowSpadesCorrect))
        assertTrue(result is ActionResult.Invalid)
        assertEquals("game has ended", result.reason)
    }

    @Test
    fun `rejects declare from unknown declarer`() {
        val result = engine.apply(
            stateWithAllLowSpadesOnRed(), PlayerId("ghost"),
            Action.Declare(DeckId("LOW_S"), lowSpadesCorrect),
        )
        assertTrue(result is ActionResult.Invalid)
        assertEquals("unknown player", result.reason)
    }
}
