package com.example.absolutesolitaire

class Deck {
    private val cards = mutableListOf<Card>()

    init {
        reset()
    }

    fun reset() {
        cards.clear()
        for (suit in Suit.entries) {
            for (rank in Rank.entries) {
                cards.add(Card(suit, rank))
            }
        }
        shuffle()
    }

    fun shuffle() {
        cards.shuffle()
    }

    fun deal(): Card? = if (cards.isNotEmpty()) cards.removeAt(0) else null

    fun isEmpty(): Boolean = cards.isEmpty()
    fun size(): Int = cards.size
}