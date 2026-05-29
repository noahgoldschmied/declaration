package com.declaration.room

import com.declaration.domain.DeclarationEngine
import com.declaration.domain.TEAM_BLUE
import com.declaration.domain.TEAM_RED
import com.declaration.protocol.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RoomStartTest {

    private fun room(scope: CoroutineScope) =
        Room(RoomCode("ABCD"), DeclarationEngine(), Random(1L), 120.seconds, scope)

    private suspend fun seatedSix(room: Room): Pair<List<JoinResult>, List<FakeSink>> {
        val names = listOf("A", "B", "C", "D", "E", "F")
        val tokens = names.map { room.join(it) }
        val sinks = tokens.map { FakeSink() }
        tokens.zip(sinks).forEach { (t, s) -> room.connect(t.sessionToken, s) }
        val teams = listOf(TEAM_RED, TEAM_BLUE, TEAM_RED, TEAM_BLUE, TEAM_RED, TEAM_BLUE)
        tokens.zip(teams).forEach { (t, team) -> room.chooseTeam(t.sessionToken, team) }
        return tokens to sinks
    }

    @Test
    fun `host can start a full balanced room and everyone gets a GameUpdate`() = runTest {
        val room = room(backgroundScope)
        val (tokens, sinks) = seatedSix(room)
        advanceUntilIdle()
        sinks.forEach { it.clear() }

        room.startGame(tokens[0].sessionToken)
        advanceUntilIdle()

        sinks.forEach { sink ->
            val update = sink.last<ServerMessage.GameUpdate>()
            assertNotNull(update, "every connected player should receive a GameUpdate")
        }
    }

    @Test
    fun `each player's GameUpdate shows only their own hand`() = runTest {
        val room = room(backgroundScope)
        val (tokens, sinks) = seatedSix(room)
        advanceUntilIdle()
        sinks.forEach { it.clear() }

        room.startGame(tokens[0].sessionToken)
        advanceUntilIdle()

        val p0 = sinks[0].last<ServerMessage.GameUpdate>()!!
        assertEquals(tokens[0].playerId, p0.view.you.id)
        assertEquals(9, p0.view.you.hand.size)
        assertEquals(5, p0.view.others.size)
        p0.view.others.forEach { assertEquals(9, it.handSize) }
    }

    @Test
    fun `non-host cannot start`() = runTest {
        val room = room(backgroundScope)
        val (tokens, sinks) = seatedSix(room)
        advanceUntilIdle()
        sinks.forEach { it.clear() }

        room.startGame(tokens[1].sessionToken)
        advanceUntilIdle()

        val err = sinks[1].last<ServerMessage.ActionError>()
        assertNotNull(err)
        assertEquals("only the host can start the game", err.reason)
        sinks.forEach { assertNull(it.last<ServerMessage.GameUpdate>()) }
    }

    @Test
    fun `cannot start without 6 players`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("A")
        val sink = FakeSink()
        room.connect(host.sessionToken, sink)
        room.chooseTeam(host.sessionToken, TEAM_RED)
        advanceUntilIdle()
        sink.clear()

        room.startGame(host.sessionToken)
        advanceUntilIdle()

        val err = sink.last<ServerMessage.ActionError>()
        assertNotNull(err)
        assertEquals("need 6 players split 3-3 to start", err.reason)
    }

    @Test
    fun `cannot start with unbalanced teams`() = runTest {
        val room = room(backgroundScope)
        val names = listOf("A", "B", "C", "D", "E", "F")
        val tokens = names.map { room.join(it) }
        val sinks = tokens.map { FakeSink() }
        tokens.zip(sinks).forEach { (t, s) -> room.connect(t.sessionToken, s) }
        room.chooseTeam(tokens[0].sessionToken, TEAM_RED)
        room.chooseTeam(tokens[1].sessionToken, TEAM_RED)
        room.chooseTeam(tokens[2].sessionToken, TEAM_RED)
        room.chooseTeam(tokens[3].sessionToken, TEAM_BLUE)
        room.chooseTeam(tokens[4].sessionToken, TEAM_BLUE)
        // tokens[5] never picks a team.
        advanceUntilIdle()
        sinks.forEach { it.clear() }

        room.startGame(tokens[0].sessionToken)
        advanceUntilIdle()

        val err = sinks[0].last<ServerMessage.ActionError>()
        assertNotNull(err)
        assertEquals("need 6 players split 3-3 to start", err.reason)
    }
}
