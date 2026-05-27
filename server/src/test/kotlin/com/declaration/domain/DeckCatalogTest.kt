package com.declaration.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class DeckCatalogTest {

    @Test
    fun `catalog has exactly 9 decks`() {
        assertEquals(9, DeckCatalog.cardsByDeck.size)
    }

    @Test
    fun `every deck contains exactly 6 cards`() {
        DeckCatalog.cardsByDeck.forEach { (deck, cards) ->
            assertEquals(6, cards.size, "deck $deck should have 6 cards")
        }
    }

    @Test
    fun `catalog has exactly 54 unique cards`() {
        val all = DeckCatalog.cardsByDeck.values.flatten().toSet()
        assertEquals(54, all.size)
    }

    @Test
    fun `every card maps back to its deck via deckByCard`() {
        DeckCatalog.cardsByDeck.forEach { (deck, cards) ->
            cards.forEach { card ->
                assertEquals(deck, DeckCatalog.deckByCard[card], "$card should map to $deck")
            }
        }
    }

    @Test
    fun `low spades deck contains 2-7 of spades`() {
        val expected = setOf("2S", "3S", "4S", "5S", "6S", "7S").map(::CardId).toSet()
        assertEquals(expected, DeckCatalog.cardsByDeck[DeckId("LOW_S")])
    }

    @Test
    fun `high hearts deck contains 9-A of hearts`() {
        val expected = setOf("9H", "TH", "JH", "QH", "KH", "AH").map(::CardId).toSet()
        assertEquals(expected, DeckCatalog.cardsByDeck[DeckId("HIGH_H")])
    }

    @Test
    fun `eights and jokers deck contains all 8s and both jokers`() {
        val expected = setOf("8S", "8H", "8D", "8C", "JK1", "JK2").map(::CardId).toSet()
        assertEquals(expected, DeckCatalog.cardsByDeck[DeckId("EIGHTS_JOKERS")])
    }

    @Test
    fun `lookup deckByCard returns null for nonexistent card`() {
        assertNotNull(DeckCatalog.deckByCard[CardId("2S")])
        assertEquals(null, DeckCatalog.deckByCard[CardId("ZZ")])
    }

    @Test
    fun `all card IDs are distinct across all decks`() {
        val all = DeckCatalog.cardsByDeck.values.flatten()
        assertEquals(all.size, all.toSet().size, "no card may appear in more than one deck")
        assertTrue(all.isNotEmpty())
    }
}
