package com.declaration.domain

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainWireFormatTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `value classes encode as bare strings`() {
        assertEquals("\"2S\"", json.encodeToString(CardId("2S")))
        assertEquals("\"RED\"", json.encodeToString(TeamId("RED")))
        assertEquals("\"p0\"", json.encodeToString(PlayerId("p0")))
        assertEquals("\"LOW_S\"", json.encodeToString(DeckId("LOW_S")))
    }

    @Test
    fun `Phase and AskOutcome encode as their names`() {
        assertEquals("\"PLAYING\"", json.encodeToString(Phase.PLAYING))
        assertEquals("\"HIT\"", json.encodeToString(AskOutcome.HIT))
    }

    @Test
    fun `PlayerView round-trips including value-class map keys`() {
        val view = PlayerView(
            you = SelfView(PlayerId("p0"), TEAM_RED, 0, setOf(CardId("2S"), CardId("3S"))),
            others = listOf(OpponentView(PlayerId("p1"), TEAM_BLUE, 1, 9)),
            turn = PlayerId("p0"),
            phase = Phase.PLAYING,
            winner = null,
            capturedDecks = mapOf(DeckId("LOW_S") to TEAM_RED),
        )
        val encoded = json.encodeToString(view)
        val decoded = json.decodeFromString<PlayerView>(encoded)
        assertEquals(view, decoded)
    }

    @Test
    fun `capturedDecks renders value-class keys as JSON object keys`() {
        val view = PlayerView(
            you = SelfView(PlayerId("p0"), TEAM_RED, 0, emptySet()),
            others = emptyList(),
            turn = PlayerId("p0"),
            phase = Phase.PLAYING,
            winner = null,
            capturedDecks = mapOf(DeckId("LOW_S") to TEAM_RED),
        )
        val encoded = json.encodeToString(view)
        assert(encoded.contains("\"capturedDecks\":{\"LOW_S\":\"RED\"}")) {
            "expected object-keyed capturedDecks, got: $encoded"
        }
    }

    @Test
    fun `Event is polymorphic with a type discriminator`() {
        val event: Event = Event.Ask(PlayerId("p0"), PlayerId("p1"), CardId("3S"), AskOutcome.MISS)
        val encoded = json.encodeToString(event)
        assert(encoded.contains("\"type\":\"Ask\"")) { "expected type=Ask, got: $encoded" }
        assertEquals(event, json.decodeFromString<Event>(encoded))
    }

    @Test
    fun `Action round-trips polymorphically`() {
        val ask: Action = Action.Ask(PlayerId("p1"), CardId("3S"))
        val declare: Action = Action.Declare(
            DeckId("LOW_S"),
            mapOf(CardId("2S") to PlayerId("p0")),
        )
        assertEquals(ask, json.decodeFromString<Action>(json.encodeToString(ask)))
        assertEquals(declare, json.decodeFromString<Action>(json.encodeToString(declare)))
    }
}
