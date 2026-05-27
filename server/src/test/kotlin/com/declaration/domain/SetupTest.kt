package com.declaration.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SetupTest {

    private val seats: List<Pair<PlayerId, TeamId>> = listOf(
        PlayerId("p0") to TEAM_RED,
        PlayerId("p1") to TEAM_BLUE,
        PlayerId("p2") to TEAM_RED,
        PlayerId("p3") to TEAM_BLUE,
        PlayerId("p4") to TEAM_RED,
        PlayerId("p5") to TEAM_BLUE,
    )

    @Test
    fun `deals exactly 9 cards to each of 6 players`() {
        val state = Setup.newGame(seats, Random(42L))
        assertEquals(6, state.players.size)
        state.players.forEach { p ->
            assertEquals(9, p.hand.size, "${p.id.value} should have 9 cards")
        }
    }

    @Test
    fun `deal covers all 54 cards with no duplicates`() {
        val state = Setup.newGame(seats, Random(42L))
        val all = state.players.flatMap { it.hand }
        assertEquals(54, all.size)
        assertEquals(54, all.toSet().size)
        assertEquals(DeckCatalog.allCards, all.toSet())
    }

    @Test
    fun `seeded Random produces identical deals across runs`() {
        val a = Setup.newGame(seats, Random(123L))
        val b = Setup.newGame(seats, Random(123L))
        assertEquals(a.players.map { it.hand }, b.players.map { it.hand })
        assertEquals(a.turn, b.turn)
    }

    @Test
    fun `different seeds produce different deals`() {
        val a = Setup.newGame(seats, Random(1L))
        val b = Setup.newGame(seats, Random(2L))
        // It is theoretically possible for two seeds to coincide, but vanishingly unlikely.
        val sameDeal = a.players.map { it.hand } == b.players.map { it.hand } && a.turn == b.turn
        assertTrue(!sameDeal, "expected different deals; both produced the same arrangement")
    }

    @Test
    fun `starting player is one of the seated players`() {
        val state = Setup.newGame(seats, Random(42L))
        assertNotNull(state.players.firstOrNull { it.id == state.turn })
    }

    @Test
    fun `seats are assigned in order 0 through 5 by input order`() {
        val state = Setup.newGame(seats, Random(42L))
        state.players.forEachIndexed { i, p ->
            assertEquals(i, p.seat)
            assertEquals(seats[i].first, p.id)
            assertEquals(seats[i].second, p.team)
        }
    }

    @Test
    fun `initial state has no captured decks and PLAYING phase`() {
        val state = Setup.newGame(seats, Random(42L))
        assertEquals(emptyMap(), state.capturedDecks)
        assertEquals(Phase.PLAYING, state.phase)
        assertEquals(null, state.winner)
    }

    @Test
    fun `requires exactly 6 players`() {
        val tooFew = seats.take(5)
        try {
            Setup.newGame(tooFew, Random(0L))
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }
}
