package com.declaration.room

import com.declaration.domain.DeclarationEngine
import com.declaration.domain.TEAM_BLUE
import com.declaration.domain.TEAM_RED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RoomJoinRejectionTest {

    private fun room(scope: CoroutineScope) =
        Room(RoomCode("ABCD"), DeclarationEngine(), Random(1L), 120.seconds, scope)

    @Test
    fun `the seventh join is rejected because the room is full`() = runTest {
        val room = room(backgroundScope)
        repeat(6) { room.join("p$it") }
        advanceUntilIdle()

        val ex = assertFailsWith<RoomJoinException> { room.join("latecomer") }
        assertEquals("room is full", ex.reason)
    }

    @Test
    fun `joining after the game has started is rejected`() = runTest {
        val room = room(backgroundScope)
        val tokens = (0 until 6).map { room.join("p$it") }
        val teams = listOf(TEAM_RED, TEAM_BLUE, TEAM_RED, TEAM_BLUE, TEAM_RED, TEAM_BLUE)
        tokens.zip(teams).forEach { (t, team) -> room.chooseTeam(t.sessionToken, team) }
        room.startGame(tokens[0].sessionToken)
        advanceUntilIdle()

        val ex = assertFailsWith<RoomJoinException> { room.join("latecomer") }
        assertEquals("game already started", ex.reason)
    }

    @Test
    fun `a normal join into an open lobby still succeeds`() = runTest {
        val room = room(backgroundScope)
        val result = room.join("Alice")
        advanceUntilIdle()
        assertEquals("p0", result.playerId.value)
    }
}
