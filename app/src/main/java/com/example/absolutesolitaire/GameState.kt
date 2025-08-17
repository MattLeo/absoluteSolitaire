package com.example.absolutesolitaire

data class GameState(
    val stock: MutableList<Card> = mutableListOf(),
    val waste: MutableList<Card> = mutableListOf(),
    val foundations: Array<MutableList<Card>> = Array(4) { mutableListOf() },
    val tableau: Array<MutableList<Card>> = Array(7) { mutableListOf() },
    var score: Int = 0,
    var moves: Int = 0
) {
    fun isGameWon(): Boolean {
        return foundations.all { it.size == 13 }
    }

    fun getTopCard(pile: List<Card>): Card? = pile.lastOrNull()

    fun getTopFoundationCard(foundationIndex: Int): Card? {
        return foundations[foundationIndex].lastOrNull()
    }

    fun getVisibleTableauCards(columnIndex: Int): List<Card> {
        return tableau[columnIndex].filter { it.isFaceUp }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GameState

        if (score != other.score) return false
        if (moves != other.moves) return false
        if (stock != other.stock) return false
        if (waste != other.waste) return false
        if (!foundations.contentEquals(other.foundations)) return false
        if (!tableau.contentEquals(other.tableau)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = score
        result = 31 * result + moves
        result = 31 * result + stock.hashCode()
        result = 31 * result + waste.hashCode()
        result = 31 * result + foundations.contentHashCode()
        result = 31 * result + tableau.contentHashCode()
        return result
    }
}