package com.declaration.protocol

import com.declaration.domain.Action
import com.declaration.domain.CardId
import com.declaration.domain.PlayerId
import com.declaration.domain.TEAM_RED
import com.declaration.domain.TeamId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WireFormatTest {

    private val json = WireJson.json

    @Test
    fun `Pong encodes with only a type discriminator`() {
        assertEquals("""{"type":"Pong"}""", json.encodeToString(ServerMessage.serializer(), ServerMessage.Pong))
    }

    @Test
    fun `Welcome encodes its fields`() {
        val msg: ServerMessage = ServerMessage.Welcome(PlayerId("p0"), "tok", "Alice")
        val encoded = json.encodeToString(ServerMessage.serializer(), msg)
        assertTrue(encoded.contains("\"type\":\"Welcome\""), encoded)
        assertTrue(encoded.contains("\"playerId\":\"p0\""), encoded)
        assertEquals(msg, json.decodeFromString(ServerMessage.serializer(), encoded))
    }

    @Test
    fun `RoomState round-trips with players`() {
        val msg: ServerMessage = ServerMessage.RoomState(
            roomCode = "ABCD",
            phase = RoomPhase.LOBBY,
            hostId = PlayerId("p0"),
            players = listOf(PlayerInfo(PlayerId("p0"), "Alice", TEAM_RED, true)),
        )
        val encoded = json.encodeToString(ServerMessage.serializer(), msg)
        assertTrue(encoded.contains("\"type\":\"RoomState\""), encoded)
        assertEquals(msg, json.decodeFromString(ServerMessage.serializer(), encoded))
    }

    @Test
    fun `client ChooseTeam decodes from json`() {
        val decoded = json.decodeFromString(
            ClientMessage.serializer(),
            """{"type":"ChooseTeam","team":"RED"}""",
        )
        assertEquals(ClientMessage.ChooseTeam(TeamId("RED")), decoded)
    }

    @Test
    fun `client SubmitAction with a nested Ask decodes`() {
        val decoded = json.decodeFromString(
            ClientMessage.serializer(),
            """{"type":"SubmitAction","action":{"type":"Ask","target":"p1","card":"3S"}}""",
        )
        assertEquals(ClientMessage.SubmitAction(Action.Ask(PlayerId("p1"), CardId("3S"))), decoded)
    }

    @Test
    fun `client Hello and Ping decode as objects`() {
        assertEquals(ClientMessage.Hello, json.decodeFromString(ClientMessage.serializer(), """{"type":"Hello"}"""))
        assertEquals(ClientMessage.Ping, json.decodeFromString(ClientMessage.serializer(), """{"type":"Ping"}"""))
    }
}
