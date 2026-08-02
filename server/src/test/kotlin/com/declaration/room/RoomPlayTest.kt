package com.declaration.room

import com.declaration.domain.Action
import com.declaration.domain.CardId
import com.declaration.domain.DeckId
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
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RoomPlayTest {

    private fun room(scope: CoroutineScope) =
        Room(RoomCode("ABCD"), DeclarationEngine(), Random(1L), 120.seconds, scope)

    private suspend fun startedGame(room: Room): Pair<List<JoinResult>, List<FakeSink>> {
        val names = listOf("A", "B", "C", "D", "E", "F")
        val tokens = names.map { room.join(it) }
        val sinks = tokens.map { FakeSink() }
        tokens.zip(sinks).forEach { (t, s) -> room.connect(t.sessionToken, s) }
        val teams = listOf(TEAM_RED, TEAM_BLUE, TEAM_RED, TEAM_BLUE, TEAM_RED, TEAM_BLUE)
        tokens.zip(teams).forEach { (t, team) -> room.chooseTeam(t.sessionToken, team) }
        room.startGame(tokens[0].sessionToken)
        return tokens to sinks
    }

    @Test
    fun `an invalid action is rejected with ActionError to the actor only`() = runTest {
        val room = room(backgroundScope)
        val (tokens, sinks) = startedGame(room)
        advanceUntilIdle()
        sinks.forEach { it.clear() }

        // Ask for a non-existent card "ZZ" from p0. Whatever the dealt turn order, p0 gets an
        // ActionError: "unknown card" if on turn, else "not your turn". Either way exactly one
        // ActionError to p0 and none to anyone else.
        room.submitAction(tokens[0].sessionToken, Action.Ask(tokens[1].playerId, CardId("ZZ")))
        advanceUntilIdle()

        val err = sinks[0].last<ServerMessage.ActionError>()
        assertNotNull(err, "actor should receive an ActionError")
        (1..5).forEach { i ->
            assertEquals(0, sinks[i].all<ServerMessage.ActionError>().size, "player p$i should get no error")
        }
    }

    @Test
    fun `submitting before the game starts is rejected`() = runTest {
        val room = room(backgroundScope)
        val names = listOf("A", "B", "C", "D", "E", "F")
        val tokens = names.map { room.join(it) }
        val sinks = tokens.map { FakeSink() }
        tokens.zip(sinks).forEach { (t, s) -> room.connect(t.sessionToken, s) }
        advanceUntilIdle()
        sinks.forEach { it.clear() }

        room.submitAction(tokens[0].sessionToken, Action.Ask(tokens[1].playerId, CardId("3S")))
        advanceUntilIdle()

        val err = sinks[0].last<ServerMessage.ActionError>()
        assertNotNull(err)
        assertEquals("game is not in progress", err.reason)
    }

    @Test
    fun `commands submitted in a burst are processed serially and losslessly`() = runTest {
        val room = room(backgroundScope)
        val (tokens, sinks) = startedGame(room)
        advanceUntilIdle()
        sinks.forEach { it.clear() }

        // 20 illegal mid-game ChooseTeam commands; each yields exactly one ActionError to sender.
        repeat(20) { room.chooseTeam(tokens[0].sessionToken, TEAM_RED) }
        advanceUntilIdle()

        assertEquals(20, sinks[0].all<ServerMessage.ActionError>().size)
    }

    @Test
    fun `setDeclaring broadcasts the declaring set to every player`() = runTest {
        val room = room(backgroundScope)
        val (tokens, sinks) = startedGame(room)
        advanceUntilIdle()
        sinks.forEach { it.clear() }

        room.setDeclaring(tokens[0].sessionToken, true)
        advanceUntilIdle()

        sinks.forEach { sink ->
            assertEquals(setOf(tokens[0].playerId), sink.last<ServerMessage.DeclaringPlayers>()?.playerIds)
        }
    }

    @Test
    fun `submitting a declare auto-clears isDeclaring without a separate SetDeclaring(false)`() = runTest {
        val room = room(backgroundScope)
        val (tokens, sinks) = startedGame(room)
        advanceUntilIdle()
        room.setDeclaring(tokens[0].sessionToken, true)
        advanceUntilIdle()
        sinks.forEach { it.clear() }

        val lowSpades = setOf("2S", "3S", "4S", "5S", "6S", "7S").map { CardId(it) }
        room.submitAction(tokens[0].sessionToken, Action.Declare(DeckId("LOW_S"), lowSpades.associateWith { tokens[0].playerId }))
        advanceUntilIdle()

        assertEquals(emptySet(), sinks[0].last<ServerMessage.DeclaringPlayers>()?.playerIds)
    }

    @Test
    fun `disconnecting while declaring clears it for everyone`() = runTest {
        val room = room(backgroundScope)
        val (tokens, sinks) = startedGame(room)
        advanceUntilIdle()
        room.setDeclaring(tokens[0].sessionToken, true)
        advanceUntilIdle()
        sinks.forEach { it.clear() }

        room.disconnect(tokens[0].sessionToken)
        advanceUntilIdle()

        assertEquals(emptySet(), sinks[1].last<ServerMessage.DeclaringPlayers>()?.playerIds)
    }

    @Test
    fun `another player's action is rejected while someone is declaring`() = runTest {
        val room = room(backgroundScope)
        val (tokens, sinks) = startedGame(room)
        advanceUntilIdle()
        room.setDeclaring(tokens[0].sessionToken, true)
        advanceUntilIdle()
        sinks.forEach { it.clear() }

        room.submitAction(tokens[1].sessionToken, Action.Ask(tokens[2].playerId, CardId("3S")))
        advanceUntilIdle()

        val err = sinks[1].last<ServerMessage.ActionError>()
        assertNotNull(err)
        assertEquals("A is declaring — the game is paused", err.reason)
    }

    @Test
    fun `a second player cannot start declaring while another already is`() = runTest {
        val room = room(backgroundScope)
        val (tokens, sinks) = startedGame(room)
        advanceUntilIdle()
        room.setDeclaring(tokens[0].sessionToken, true)
        advanceUntilIdle()
        sinks.forEach { it.clear() }

        room.setDeclaring(tokens[1].sessionToken, true)
        advanceUntilIdle()

        val err = sinks[1].last<ServerMessage.ActionError>()
        assertNotNull(err)
        assertEquals("A is already declaring — wait for them to finish", err.reason)
        // Rejected -- no new broadcast, so nobody's told player B is declaring too.
        assertEquals(0, sinks[1].all<ServerMessage.DeclaringPlayers>().size)
    }

    @Test
    fun `the declaring player's own action is not blocked by their own declaring flag`() = runTest {
        val room = room(backgroundScope)
        val (tokens, sinks) = startedGame(room)
        advanceUntilIdle()
        room.setDeclaring(tokens[0].sessionToken, true)
        advanceUntilIdle()
        sinks.forEach { it.clear() }

        val lowSpades = setOf("2S", "3S", "4S", "5S", "6S", "7S").map { CardId(it) }
        room.submitAction(tokens[0].sessionToken, Action.Declare(DeckId("LOW_S"), lowSpades.associateWith { tokens[0].playerId }))
        advanceUntilIdle()

        assertEquals(0, sinks[0].all<ServerMessage.ActionError>().count { it.reason.contains("paused") })
    }
}
