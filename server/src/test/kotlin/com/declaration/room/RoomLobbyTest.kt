package com.declaration.room

import com.declaration.domain.DeclarationEngine
import com.declaration.domain.TEAM_BLUE
import com.declaration.domain.TEAM_RED
import com.declaration.protocol.RoomPhase
import com.declaration.protocol.ServerMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RoomLobbyTest {

    private fun room(scope: kotlinx.coroutines.CoroutineScope) =
        Room(
            code = RoomCode("ABCD"),
            engine = DeclarationEngine(),
            random = Random(1L),
            gracePeriod = 120.seconds,
            scope = scope,
        )

    @Test
    fun `join returns a player id and token and first joiner is host`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        advanceUntilIdle()

        assertEquals("p0", host.playerId.value)
        assertEquals(32, host.sessionToken.length)
    }

    @Test
    fun `connecting sends Welcome then RoomState to that player`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        val sink = FakeSink()
        room.connect(host.sessionToken, sink)
        advanceUntilIdle()

        val welcome = sink.last<ServerMessage.Welcome>()
        assertNotNull(welcome)
        assertEquals(host.playerId, welcome.playerId)
        assertEquals("Alice", welcome.displayName)

        val state = sink.last<ServerMessage.RoomState>()
        assertNotNull(state)
        assertEquals(RoomPhase.LOBBY, state.phase)
        assertEquals(host.playerId, state.hostId)
        assertEquals(1, state.players.size)
        assertEquals("Alice", state.players.single().displayName)
        assertTrue(state.players.single().connected)
        assertEquals(null, state.players.single().team)
    }

    @Test
    fun `a second join is broadcast to already-connected players`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        val hostSink = FakeSink()
        room.connect(host.sessionToken, hostSink)
        advanceUntilIdle()
        hostSink.clear()

        room.join("Bob")
        advanceUntilIdle()

        val state = hostSink.last<ServerMessage.RoomState>()
        assertNotNull(state)
        assertEquals(2, state.players.size)
        assertEquals(listOf("Alice", "Bob"), state.players.map { it.displayName })
    }

    @Test
    fun `chooseTeam sets the team and rebroadcasts`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        val sink = FakeSink()
        room.connect(host.sessionToken, sink)
        advanceUntilIdle()
        sink.clear()

        room.chooseTeam(host.sessionToken, TEAM_RED)
        advanceUntilIdle()

        val state = sink.last<ServerMessage.RoomState>()
        assertNotNull(state)
        assertEquals(TEAM_RED, state.players.single().team)
    }

    @Test
    fun `chooseTeam is rejected when the team already has three players`() = runTest {
        val room = room(backgroundScope)
        val tokens = listOf("Alice", "Bob", "Charlie", "Dan").map { room.join(it) }
        val sinks = tokens.map { FakeSink() }
        tokens.zip(sinks).forEach { (t, s) -> room.connect(t.sessionToken, s) }
        advanceUntilIdle()
        sinks.forEach { it.clear() }

        room.chooseTeam(tokens[0].sessionToken, TEAM_RED)
        room.chooseTeam(tokens[1].sessionToken, TEAM_RED)
        room.chooseTeam(tokens[2].sessionToken, TEAM_RED)
        advanceUntilIdle()
        room.chooseTeam(tokens[3].sessionToken, TEAM_RED)
        advanceUntilIdle()

        val err = sinks[3].last<ServerMessage.ActionError>()
        assertNotNull(err)
        assertEquals("team RED is full", err.reason)
    }

    @Test
    fun `connect with an unknown token gets an explicit rejection, not silence`() = runTest {
        val room = room(backgroundScope)
        room.join("Alice")
        val sink = FakeSink()
        room.connect("not-a-real-token", sink)
        advanceUntilIdle()
        val err = sink.last<ServerMessage.ActionError>()
        assertNotNull(err, "unknown token should get an explicit ActionError, not silence")
    }
}
