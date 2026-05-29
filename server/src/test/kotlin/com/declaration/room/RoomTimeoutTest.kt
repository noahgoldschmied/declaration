package com.declaration.room

import com.declaration.domain.DeclarationEngine
import com.declaration.protocol.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RoomTimeoutTest {

    private fun room(scope: CoroutineScope) =
        Room(RoomCode("ABCD"), DeclarationEngine(), Random(1L), 120.seconds, scope)

    @Test
    fun `a player still disconnected after the grace period is removed`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        val bob = room.join("Bob")
        val hostSink = FakeSink()
        room.connect(host.sessionToken, hostSink)
        room.connect(bob.sessionToken, FakeSink())
        advanceUntilIdle()

        room.disconnect(bob.sessionToken)
        advanceTimeBy(119.seconds)
        runCurrent()
        // Before threshold: re-emit roster to host, still 2 players.
        room.connect(host.sessionToken, hostSink)
        advanceUntilIdle()
        assertEquals(2, hostSink.last<ServerMessage.RoomState>()!!.players.size)

        // Cross the threshold.
        advanceTimeBy(2.seconds)
        advanceUntilIdle()

        val finalState = hostSink.all<ServerMessage.RoomState>().last()
        assertEquals(1, finalState.players.size)
        assertEquals(host.playerId, finalState.players.single().playerId)
    }

    @Test
    fun `a player who reconnects before the grace period is not removed`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        val bob = room.join("Bob")
        room.connect(host.sessionToken, FakeSink())
        room.connect(bob.sessionToken, FakeSink())
        advanceUntilIdle()

        room.disconnect(bob.sessionToken)
        advanceTimeBy(60.seconds)
        runCurrent()
        room.connect(bob.sessionToken, FakeSink()) // reconnect before grace elapses
        runCurrent()

        advanceTimeBy(120.seconds) // original timer fires, must be a no-op (epoch bumped)
        advanceUntilIdle()

        val hostSink = FakeSink()
        room.connect(host.sessionToken, hostSink)
        advanceUntilIdle()
        assertEquals(2, hostSink.last<ServerMessage.RoomState>()!!.players.size)
    }

    @Test
    fun `when the host is removed the next remaining player becomes host`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")   // p0, host
        val bob = room.join("Bob")       // p1
        val carol = room.join("Carol")   // p2
        val bobSink = FakeSink()
        room.connect(host.sessionToken, FakeSink())
        room.connect(bob.sessionToken, bobSink)
        room.connect(carol.sessionToken, FakeSink())
        advanceUntilIdle()

        room.disconnect(host.sessionToken)
        advanceTimeBy(121.seconds)
        advanceUntilIdle()

        val state = bobSink.all<ServerMessage.RoomState>().last()
        assertEquals(2, state.players.size)
        assertEquals(bob.playerId, state.hostId) // p1 is the new host
    }
}
