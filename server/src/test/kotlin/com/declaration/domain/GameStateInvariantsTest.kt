package com.declaration.domain

import com.declaration.domain.GameStates.ALICE
import com.declaration.domain.GameStates.BOB
import com.declaration.domain.GameStates.CHARLIE
import com.declaration.domain.GameStates.DAN
import com.declaration.domain.GameStates.EVE
import com.declaration.domain.GameStates.FRANK
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GameStateInvariantsTest {

    @Test
    fun `rejects fewer than 6 players`() {
        assertFailsWith<IllegalArgumentException> {
            GameState(
                players = listOf(
                    Player(ALICE, TEAM_RED, 0, emptySet()),
                    Player(BOB, TEAM_BLUE, 1, emptySet()),
                    Player(CHARLIE, TEAM_RED, 2, emptySet()),
                    Player(DAN, TEAM_BLUE, 3, emptySet()),
                    Player(EVE, TEAM_RED, 4, emptySet()),
                ),
                turn = ALICE,
            )
        }
    }

    @Test
    fun `rejects duplicate player ids`() {
        assertFailsWith<IllegalArgumentException> {
            GameState(
                players = listOf(
                    Player(ALICE, TEAM_RED, 0, emptySet()),
                    Player(ALICE, TEAM_BLUE, 1, emptySet()), // duplicate id
                    Player(CHARLIE, TEAM_RED, 2, emptySet()),
                    Player(DAN, TEAM_BLUE, 3, emptySet()),
                    Player(EVE, TEAM_RED, 4, emptySet()),
                    Player(FRANK, TEAM_BLUE, 5, emptySet()),
                ),
                turn = ALICE,
            )
        }
    }

    @Test
    fun `rejects malformed seats`() {
        assertFailsWith<IllegalArgumentException> {
            GameState(
                players = listOf(
                    Player(ALICE, TEAM_RED, 0, emptySet()),
                    Player(BOB, TEAM_BLUE, 0, emptySet()), // duplicate seat
                    Player(CHARLIE, TEAM_RED, 2, emptySet()),
                    Player(DAN, TEAM_BLUE, 3, emptySet()),
                    Player(EVE, TEAM_RED, 4, emptySet()),
                    Player(FRANK, TEAM_BLUE, 5, emptySet()),
                ),
                turn = ALICE,
            )
        }
    }

    @Test
    fun `rejects all players on one team`() {
        assertFailsWith<IllegalArgumentException> {
            GameState(
                players = listOf(
                    Player(ALICE, TEAM_RED, 0, emptySet()),
                    Player(BOB, TEAM_RED, 1, emptySet()),
                    Player(CHARLIE, TEAM_RED, 2, emptySet()),
                    Player(DAN, TEAM_RED, 3, emptySet()),
                    Player(EVE, TEAM_RED, 4, emptySet()),
                    Player(FRANK, TEAM_RED, 5, emptySet()),
                ),
                turn = ALICE,
            )
        }
    }

    @Test
    fun `rejects unbalanced teams 4 and 2`() {
        assertFailsWith<IllegalArgumentException> {
            GameState(
                players = listOf(
                    Player(ALICE, TEAM_RED, 0, emptySet()),
                    Player(BOB, TEAM_RED, 1, emptySet()),
                    Player(CHARLIE, TEAM_RED, 2, emptySet()),
                    Player(DAN, TEAM_RED, 3, emptySet()),
                    Player(EVE, TEAM_BLUE, 4, emptySet()),
                    Player(FRANK, TEAM_BLUE, 5, emptySet()),
                ),
                turn = ALICE,
            )
        }
    }

    @Test
    fun `accepts valid balanced configuration`() {
        // Should not throw.
        GameState(
            players = listOf(
                Player(ALICE, TEAM_RED, 0, emptySet()),
                Player(BOB, TEAM_BLUE, 1, emptySet()),
                Player(CHARLIE, TEAM_RED, 2, emptySet()),
                Player(DAN, TEAM_BLUE, 3, emptySet()),
                Player(EVE, TEAM_RED, 4, emptySet()),
                Player(FRANK, TEAM_BLUE, 5, emptySet()),
            ),
            turn = ALICE,
        )
    }
}
