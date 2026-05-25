package com.declaration.domain

object DeckCatalog {

    private val LOW_RANKS = listOf("2", "3", "4", "5", "6", "7")
    private val HIGH_RANKS = listOf("9", "T", "J", "Q", "K", "A")
    private val SUITS = listOf("S", "H", "D", "C")

    val cardsByDeck: Map<DeckId, Set<CardId>> = buildMap {
        SUITS.forEach { suit ->
            put(DeckId("LOW_$suit"), LOW_RANKS.map { CardId("$it$suit") }.toSet())
            put(DeckId("HIGH_$suit"), HIGH_RANKS.map { CardId("$it$suit") }.toSet())
        }
        put(
            DeckId("EIGHTS_JOKERS"),
            setOf(CardId("8S"), CardId("8H"), CardId("8D"), CardId("8C"), CardId("JK1"), CardId("JK2"))
        )
    }

    val deckByCard: Map<CardId, DeckId> = buildMap {
        cardsByDeck.forEach { (deck, cards) ->
            cards.forEach { card -> put(card, deck) }
        }
    }

    val allCards: Set<CardId> = deckByCard.keys
}
