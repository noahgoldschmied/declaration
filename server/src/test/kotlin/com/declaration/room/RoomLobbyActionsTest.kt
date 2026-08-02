package com.declaration.room

import com.declaration.domain.DeclarationEngine
import com.declaration.domain.TEAM_BLUE
import com.declaration.domain.TEAM_RED
import com.declaration.protocol.BotDifficulty
import com.declaration.protocol.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RoomLobbyActionsTest {

    private fun room(scope: CoroutineScope) =
        Room(RoomCode("ABCD"), DeclarationEngine(), Random(1L), 120.seconds, scope)

    private fun registry(scope: CoroutineScope) =
        RoomRegistry(DeclarationEngine(), Random(1L), 120.seconds, scope)

    @Test
    fun `host can add a bot to a team`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        val hostSink = FakeSink()
        room.connect(host.sessionToken, hostSink)
        advanceUntilIdle()
        hostSink.clear()

        val result = room.addBot(host.sessionToken, listOf("TestBot"), TEAM_RED, BotDifficulty.IMPOSSIBLE, FakeSink())
        advanceUntilIdle()

        assertNotNull(result)
        val state = hostSink.last<ServerMessage.RoomState>()
        assertNotNull(state)
        assertEquals(2, state.players.size)
        val bot = state.players.single { it.playerId == result.playerId }
        assertTrue(bot.isBot)
        assertEquals(TEAM_RED, bot.team)
        assertEquals(BotDifficulty.IMPOSSIBLE, bot.botDifficulty)
        assertTrue(bot.connected, "a bot's sink is attached immediately, so it should read as connected")
    }

    @Test
    fun `two bots requesting the same candidate name get distinct names`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        val hostSink = FakeSink()
        room.connect(host.sessionToken, hostSink)
        advanceUntilIdle()
        hostSink.clear()

        val first = room.addBot(host.sessionToken, listOf("Doc", "Reno"), TEAM_RED, BotDifficulty.IMPOSSIBLE, FakeSink())
        val second = room.addBot(host.sessionToken, listOf("Doc", "Reno"), TEAM_RED, BotDifficulty.IMPOSSIBLE, FakeSink())
        val third = room.addBot(host.sessionToken, listOf("Doc", "Reno"), TEAM_RED, BotDifficulty.IMPOSSIBLE, FakeSink())
        advanceUntilIdle()

        assertNotNull(first)
        assertNotNull(second)
        assertNotNull(third)
        val state = hostSink.last<ServerMessage.RoomState>()
        assertNotNull(state)
        val names = state.players.map { it.displayName }
        assertEquals(names.size, names.toSet().size, "bot names must be unique: $names")
        assertTrue(names.containsAll(listOf("Doc", "Reno")))
    }

    @Test
    fun `non-host cannot add a bot`() = runTest {
        val room = room(backgroundScope)
        room.join("Alice")
        val bob = room.join("Bob")
        val bobSink = FakeSink()
        room.connect(bob.sessionToken, bobSink)
        advanceUntilIdle()
        bobSink.clear()

        val result = room.addBot(bob.sessionToken, listOf("TestBot"), TEAM_RED, BotDifficulty.IMPOSSIBLE, FakeSink())
        advanceUntilIdle()

        assertNull(result)
        val err = bobSink.last<ServerMessage.ActionError>()
        assertNotNull(err)
        assertEquals("only the host can add a bot", err.reason)
    }

    @Test
    fun `adding a bot to a full room is rejected`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        val hostSink = FakeSink()
        room.connect(host.sessionToken, hostSink)
        listOf("Bob", "Charlie", "Dan", "Eve", "Frank").forEach { room.join(it) }
        advanceUntilIdle()
        hostSink.clear()

        val result = room.addBot(host.sessionToken, listOf("TestBot"), TEAM_RED, BotDifficulty.IMPOSSIBLE, FakeSink())
        advanceUntilIdle()

        assertNull(result)
        assertEquals("room is full", hostSink.last<ServerMessage.ActionError>()?.reason)
    }

    @Test
    fun `adding a bot to a full team is rejected`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        val hostSink = FakeSink()
        room.connect(host.sessionToken, hostSink)
        room.chooseTeam(host.sessionToken, TEAM_RED)
        advanceUntilIdle()
        room.addBot(host.sessionToken, listOf("Bot1"), TEAM_RED, BotDifficulty.IMPOSSIBLE, FakeSink())
        room.addBot(host.sessionToken, listOf("Bot2"), TEAM_RED, BotDifficulty.IMPOSSIBLE, FakeSink())
        advanceUntilIdle()
        hostSink.clear()

        val result = room.addBot(host.sessionToken, listOf("Bot3"), TEAM_RED, BotDifficulty.IMPOSSIBLE, FakeSink())
        advanceUntilIdle()

        assertNull(result)
        assertEquals("team RED is full", hostSink.last<ServerMessage.ActionError>()?.reason)
    }

    @Test
    fun `host can kick a bot, freeing its seat`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        val hostSink = FakeSink()
        room.connect(host.sessionToken, hostSink)
        advanceUntilIdle()
        val bot = room.addBot(host.sessionToken, listOf("TestBot"), TEAM_RED, BotDifficulty.IMPOSSIBLE, FakeSink())!!
        advanceUntilIdle()
        hostSink.clear()

        room.kickPlayer(host.sessionToken, bot.playerId)
        advanceUntilIdle()

        val state = hostSink.last<ServerMessage.RoomState>()
        assertNotNull(state)
        assertEquals(1, state.players.size)
        assertTrue(state.players.none { it.playerId == bot.playerId })
    }

    @Test
    fun `host can kick a human, who receives Kicked`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        val hostSink = FakeSink()
        room.connect(host.sessionToken, hostSink)
        val bob = room.join("Bob")
        val bobSink = FakeSink()
        room.connect(bob.sessionToken, bobSink)
        advanceUntilIdle()
        hostSink.clear()
        bobSink.clear()

        room.kickPlayer(host.sessionToken, bob.playerId)
        advanceUntilIdle()

        assertTrue(bobSink.all<ServerMessage.Kicked>().isNotEmpty())
        val state = hostSink.last<ServerMessage.RoomState>()
        assertNotNull(state)
        assertEquals(1, state.players.size)
    }

    @Test
    fun `non-host cannot kick`() = runTest {
        val room = room(backgroundScope)
        room.join("Alice")
        val bob = room.join("Bob")
        val bobSink = FakeSink()
        room.connect(bob.sessionToken, bobSink)
        val carol = room.join("Carol")
        advanceUntilIdle()
        bobSink.clear()

        room.kickPlayer(bob.sessionToken, carol.playerId)
        advanceUntilIdle()

        assertEquals("only the host can remove a player", bobSink.last<ServerMessage.ActionError>()?.reason)
    }

    @Test
    fun `host cannot kick themselves`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        val hostSink = FakeSink()
        room.connect(host.sessionToken, hostSink)
        advanceUntilIdle()
        hostSink.clear()

        room.kickPlayer(host.sessionToken, host.playerId)
        advanceUntilIdle()

        assertEquals("cannot remove yourself", hostSink.last<ServerMessage.ActionError>()?.reason)
    }

    @Test
    fun `randomizeTeams produces a valid 3-3 split for 6 seated players`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        val hostSink = FakeSink()
        room.connect(host.sessionToken, hostSink)
        listOf("Bob", "Charlie", "Dan", "Eve", "Frank").forEach { room.join(it) }
        advanceUntilIdle()
        hostSink.clear()

        room.randomizeTeams(host.sessionToken)
        advanceUntilIdle()

        val state = hostSink.last<ServerMessage.RoomState>()
        assertNotNull(state)
        assertEquals(6, state.players.count { it.team == TEAM_RED } + state.players.count { it.team == TEAM_BLUE })
        assertEquals(3, state.players.count { it.team == TEAM_RED })
        assertEquals(3, state.players.count { it.team == TEAM_BLUE })
    }

    @Test
    fun `non-host cannot randomize teams`() = runTest {
        val room = room(backgroundScope)
        room.join("Alice")
        val bob = room.join("Bob")
        val bobSink = FakeSink()
        room.connect(bob.sessionToken, bobSink)
        advanceUntilIdle()
        bobSink.clear()

        room.randomizeTeams(bob.sessionToken)
        advanceUntilIdle()

        assertEquals("only the host can randomize teams", bobSink.last<ServerMessage.ActionError>()?.reason)
    }

    @Test
    fun `a room abandoned by every human but still holding a bot gets reaped`() = runTest {
        val registry = registry(backgroundScope)
        val created = registry.create("Alice")
        val room = registry.get(created.code)!!
        room.connect(created.host.sessionToken, FakeSink())
        advanceUntilIdle()
        room.addBot(created.host.sessionToken, listOf("TestBot"), TEAM_RED, BotDifficulty.IMPOSSIBLE, FakeSink())
        advanceUntilIdle()
        assertNotNull(registry.get(created.code), "sanity check: room exists with host + bot")

        room.disconnect(created.host.sessionToken)
        advanceTimeBy(121.seconds)
        advanceUntilIdle()

        assertNull(registry.get(created.code), "a bot left behind should not keep an abandoned room alive")
    }

    @Test
    fun `host can enable move history and it broadcasts to everyone`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        val hostSink = FakeSink()
        room.connect(host.sessionToken, hostSink)
        val bob = room.join("Bob")
        val bobSink = FakeSink()
        room.connect(bob.sessionToken, bobSink)
        advanceUntilIdle()
        hostSink.clear()
        bobSink.clear()

        room.setMoveHistoryEnabled(host.sessionToken, true, 20)
        advanceUntilIdle()

        assertEquals(true, hostSink.last<ServerMessage.RoomState>()?.moveHistoryEnabled)
        assertEquals(true, bobSink.last<ServerMessage.RoomState>()?.moveHistoryEnabled)
        assertEquals(20, hostSink.last<ServerMessage.RoomState>()?.moveHistoryVisibleCount)
        assertEquals(20, bobSink.last<ServerMessage.RoomState>()?.moveHistoryVisibleCount)
    }

    @Test
    fun `move history visible count is clamped to the allowed range`() = runTest {
        val room = room(backgroundScope)
        val host = room.join("Alice")
        val hostSink = FakeSink()
        room.connect(host.sessionToken, hostSink)
        advanceUntilIdle()
        hostSink.clear()

        room.setMoveHistoryEnabled(host.sessionToken, true, 9999)
        advanceUntilIdle()

        assertEquals(
            com.declaration.protocol.MoveHistoryLimits.MAX_VISIBLE_COUNT,
            hostSink.last<ServerMessage.RoomState>()?.moveHistoryVisibleCount,
        )
    }

    @Test
    fun `non-host cannot change the move history setting`() = runTest {
        val room = room(backgroundScope)
        room.join("Alice")
        val bob = room.join("Bob")
        val bobSink = FakeSink()
        room.connect(bob.sessionToken, bobSink)
        advanceUntilIdle()
        bobSink.clear()

        room.setMoveHistoryEnabled(bob.sessionToken, true, 10)
        advanceUntilIdle()

        assertEquals("only the host can change the move history setting", bobSink.last<ServerMessage.ActionError>()?.reason)
        assertEquals(false, bobSink.all<ServerMessage.RoomState>().lastOrNull()?.moveHistoryEnabled ?: false)
    }

    @Test
    fun `move history setting is locked once the game has started`() = runTest {
        val room = room(backgroundScope)
        val names = listOf("A", "B", "C", "D", "E", "F")
        val tokens = names.map { room.join(it) }
        val sinks = tokens.map { FakeSink() }
        tokens.zip(sinks).forEach { (t, s) -> room.connect(t.sessionToken, s) }
        val teams = listOf(TEAM_RED, TEAM_BLUE, TEAM_RED, TEAM_BLUE, TEAM_RED, TEAM_BLUE)
        tokens.zip(teams).forEach { (t, team) -> room.chooseTeam(t.sessionToken, team) }
        room.startGame(tokens[0].sessionToken)
        advanceUntilIdle()
        sinks.forEach { it.clear() }

        room.setMoveHistoryEnabled(tokens[0].sessionToken, true, 10)
        advanceUntilIdle()

        assertEquals(
            "move history is locked once the game has started",
            sinks[0].last<ServerMessage.ActionError>()?.reason,
        )
    }
}
