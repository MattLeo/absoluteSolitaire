package com.example.absolutesolitaire

enum class Suit(val color: CardColor) {
    HEARTS(CardColor.RED),
    DIAMONDS(CardColor.RED),
    CLUBS(CardColor.BLACK),
    SPADES(CardColor.BLACK)
}

enum class CardColor {
    RED, BLACK
}

enum class Rank(val value: Int) {
    ACE(1), TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7),
    EIGHT(8), NINE(9), TEN(10), JACK(11), QUEEN(12), KING(13)
}

data class Card(
    val suit: Suit,
    val rank: Rank,
    var isFaceUp: Boolean = false
) {
    val color: CardColor get() = suit.color

    fun canPlaceOnTableau(other: Card): Boolean {
        return this.rank.value == other.rank.value - 1 &&
                this.color != other.color
    }

    fun canPlaceOnFoundation(other: Card?): Boolean {
        return if (other == null) {
            this.rank == Rank.ACE
        } else {
            this.suit == other.suit && this.rank.value == other.rank.value + 1
        }
    }
}