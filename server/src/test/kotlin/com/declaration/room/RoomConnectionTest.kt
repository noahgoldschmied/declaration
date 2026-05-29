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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RoomConnectionTest {

    private fun room(scope: CoroutineScope) =
        Room(RoomCode("ABCD"), DeclarationEngine(), Random(1L), 120.seconds, scope)

    @Test
    fun `ping is answered with pong to the sender only`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        val sink = FakeSink()
        room.connect(host.sessionToken, sink)
        advanceUntilIdle()
        sink.clear()

        room.ping(host.sessionToken)
        advanceUntilIdle()

        assertEquals(1, sink.all<ServerMessage.Pong>().size)
    }

    @Test
    fun `disconnect marks the player not connected and rebroadcasts roster`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        val bob = room.join("Bob")
        val hostSink = FakeSink()
        val bobSink = FakeSink()
        room.connect(host.sessionToken, hostSink)
        room.connect(bob.sessionToken, bobSink)
        advanceUntilIdle()
        hostSink.clear()
        bobSink.clear()

        room.disconnect(bob.sessionToken)
        advanceUntilIdle()

        val state = hostSink.last<ServerMessage.RoomState>()
        assertNotNull(state)
        val bobInfo = state.players.single { it.playerId == bob.playerId }
        assertTrue(!bobInfo.connected)
    }

    @Test
    fun `reconnecting mid-game replays current state as a GameUpdate`() = runTest {
        val room = room(backgroundScope)
        val names = listOf("A", "B", "C", "D", "E", "F")
        val tokens = names.map { room.join(it) }
        val sinks = tokens.map { FakeSink() }
        tokens.zip(sinks).forEach { (t, s) -> room.connect(t.sessionToken, s) }
        val teams = listOf(TEAM_RED, TEAM_BLUE, TEAM_RED, TEAM_BLUE, TEAM_RED, TEAM_BLUE)
        tokens.zip(teams).forEach { (t, team) -> room.chooseTeam(t.sessionToken, team) }
        room.startGame(tokens[0].sessionToken)
        advanceUntilIdle()

        room.disconnect(tokens[3].sessionToken)
        advanceUntilIdle()
        val freshSink = FakeSink()
        room.connect(tokens[3].sessionToken, freshSink)
        advanceUntilIdle()

        assertNotNull(freshSink.last<ServerMessage.Welcome>())
        assertNotNull(freshSink.last<ServerMessage.RoomState>())
        val update = freshSink.last<ServerMessage.GameUpdate>()
        assertNotNull(update)
        assertEquals(tokens[3].playerId, update.view.you.id)
        assertEquals(9, update.view.you.hand.size)
    }
}
