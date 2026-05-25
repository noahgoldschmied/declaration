package com.declaration.domain

import com.declaration.domain.GameStates.ALICE
import com.declaration.domain.GameStates.BOB
import com.declaration.domain.GameStates.CHARLIE
import com.declaration.domain.GameStates.DAN
import com.declaration.domain.GameStates.EVE
import com.declaration.domain.GameStates.FRANK
import kotlin.test.Test
import kotlin.test.assertTrue

class EngineSkeletonTest {

    @Test
    fun `engine apply returns an ActionResult`() {
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
        val engine: Engine = DeclarationEngine()
        val result = engine.apply(state, ALICE, Action.Ask(BOB, CardId("3S")))
        assertTrue(result is ActionResult.Ok || result is ActionResult.Invalid)
    }
}
