package com.declaration.domain

import com.declaration.domain.GameStates.ALICE
import com.declaration.domain.GameStates.BOB
import com.declaration.domain.GameStates.CHARLIE
import com.declaration.domain.GameStates.DAN
import com.declaration.domain.GameStates.EVE
import com.declaration.domain.GameStates.FRANK
import kotlin.test.Test
import kotlin.test.assertEquals

class EngineEmptyHandSkipTest {

    private val engine: Engine = DeclarationEngine()

    @Test
    fun `miss skips empty-handed asked player to next non-empty seat`() {
        // Seats: 0 Alice (Red), 1 Bob (Blue, EMPTY), 2 Charlie (Red), 3 Dan (Blue), ...
        // Alice misses Bob -> turn should pass to Bob, but Bob is empty, so it skips to Dan (next Blue with cards).
        // Actually the skip is by SEAT order regardless of team — next seat with cards.
        // Seats after Bob (1): 2 Charlie, 3 Dan, 4 Eve, 5 Frank.
        val state = GameStates.of(
            hands = listOf(
                Triple(ALICE,   TEAM_RED,  setOf(CardId("2S"))),
                Triple(BOB,     TEAM_BLUE, emptySet()),
                Triple(CHARLIE, TEAM_RED,  setOf(CardId("4S"))),
                Triple(DAN,     TEAM_BLUE, setOf(CardId("5S"))),
                Triple(EVE,     TEAM_RED,  setOf(CardId("6S"))),
                Triple(FRANK,   TEAM_BLUE, setOf(CardId("7S"))),
            ),
            turn = ALICE,
        )

        // Alice (turn) asks Bob for 3S. Bob is on Blue (valid target) and has empty hand -> miss.
        val result = engine.apply(state, ALICE, Action.Ask(BOB, CardId("3S"))) as ActionResult.Ok

        // Turn passes to Bob nominally, but Bob is empty -> skip to next seat (Charlie).
        assertEquals(CHARLIE, result.newState.turn)
    }

    @Test
    fun `skip wraps around seats`() {
        // Alice misses Frank. Frank is seat 5. Frank has empty hand -> skip to seat 0 (Alice).
        // Alice has cards, so turn returns to Alice.
        val state = GameStates.of(
            hands = listOf(
                Triple(ALICE,   TEAM_RED,  setOf(CardId("2S"))),
                Triple(BOB,     TEAM_BLUE, setOf(CardId("3S"))),
                Triple(CHARLIE, TEAM_RED,  setOf(CardId("4S"))),
                Triple(DAN,     TEAM_BLUE, setOf(CardId("5S"))),
                Triple(EVE,     TEAM_RED,  setOf(CardId("6S"))),
                Triple(FRANK,   TEAM_BLUE, emptySet()),
            ),
            turn = ALICE,
        )

        // Alice asks Frank for 7S. Frank is empty -> miss. Turn passes to Frank -> skip -> Alice.
        val result = engine.apply(state, ALICE, Action.Ask(FRANK, CardId("7S"))) as ActionResult.Ok
        assertEquals(ALICE, result.newState.turn)
    }

    @Test
    fun `hit on opponent that drains their hand keeps turn with asker`() {
        // Bob has exactly one card: 3S. Alice asks for it -> hit. Bob is now empty.
        // But on HIT, turn stays with Alice — no skip needed.
        val state = GameStates.of(
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
        val result = engine.apply(state, ALICE, Action.Ask(BOB, CardId("3S"))) as ActionResult.Ok
        assertEquals(ALICE, result.newState.turn)
        // Bob should now be empty.
        assertEquals(0, result.newState.playerById(BOB)!!.hand.size)
    }
}
