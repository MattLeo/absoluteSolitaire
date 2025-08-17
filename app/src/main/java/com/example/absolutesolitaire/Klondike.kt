package com.example.absolutesolitaire

class KlondikeGame {
    private val deck = Deck()
    var gameState = GameState()
        private set

    fun newGame() {
        deck.reset()
        gameState = GameState()
        dealInitialCards()
    }

    private fun dealInitialCards() {
        // Deal tableau cards (1 to 7 cards per column)
        for (col in 0..6) {
            for (row in 0..col) {
                val card = deck.deal() ?: return
                if (row == col) {
                    card.isFaceUp = true // Top card face up
                }
                gameState.tableau[col].add(card)
            }
        }

        // Remaining cards go to stock
        while (!deck.isEmpty()) {
            gameState.stock.add(deck.deal()!!)
        }
    }

    fun dealFromStock(): Boolean {
        return if (gameState.stock.isNotEmpty()) {
            val card = gameState.stock.removeAt(0)
            card.isFaceUp = true
            gameState.waste.add(card)
            gameState.moves++
            true
        } else if (gameState.waste.isNotEmpty()) {
            // Reset stock from waste when stock is empty
            while (gameState.waste.isNotEmpty()) {
                val card = gameState.waste.removeAt(0)
                card.isFaceUp = false
                gameState.stock.add(card)
            }
            gameState.moves++
            true
        } else {
            false
        }
    }

    fun moveCard(from: MoveSource, to: MoveDestination): Boolean {
        val card = getCardFromSource(from) ?: return false

        return when (to) {
            is MoveDestination.Foundation -> {
                if (card.canPlaceOnFoundation(gameState.getTopFoundationCard(to.index))) {
                    removeCardFromSource(from)
                    gameState.foundations[to.index].add(card)
                    revealTableauCard(from)
                    gameState.moves++
                    gameState.score += 10
                    true
                } else false
            }
            is MoveDestination.Tableau -> {
                val targetCard = gameState.tableau[to.index].lastOrNull()
                if (targetCard == null && card.rank == Rank.KING) {
                    // King can be placed on empty tableau
                    moveCardSequence(from, to.index)
                    true
                } else if (targetCard != null && card.canPlaceOnTableau(targetCard)) {
                    moveCardSequence(from, to.index)
                    true
                } else false
            }
        }
    }

    private fun moveCardSequence(from: MoveSource, toTableauIndex: Int) {
        when (from) {
            is MoveSource.Waste -> {
                val card = gameState.waste.removeAt(0)
                gameState.tableau[toTableauIndex].add(card)
            }
            is MoveSource.Tableau -> {
                val fromColumn = gameState.tableau[from.columnIndex]
                val cards = fromColumn.subList(from.cardIndex, fromColumn.size).toList()
                repeat(cards.size) { fromColumn.removeAt(0) }
                gameState.tableau[toTableauIndex].addAll(cards)
                revealTableauCard(from)
            }
            is MoveSource.Foundation -> {
                val card = gameState.foundations[from.index].removeAt(0)
                gameState.tableau[toTableauIndex].add(card)
            }
        }
        gameState.moves++
    }

    private fun getCardFromSource(source: MoveSource): Card? {
        return when (source) {
            is MoveSource.Waste -> gameState.waste.lastOrNull()
            is MoveSource.Tableau -> {
                val column = gameState.tableau[source.columnIndex]
                if (source.cardIndex < column.size) column[source.cardIndex] else null
            }
            is MoveSource.Foundation -> gameState.foundations[source.index].lastOrNull()
        }
    }

    private fun removeCardFromSource(source: MoveSource) {
        when (source) {
            is MoveSource.Waste -> gameState.waste.removeAt(0)
            is MoveSource.Tableau -> {
                gameState.tableau[source.columnIndex].removeAt(0)
            }
            is MoveSource.Foundation -> gameState.foundations[source.index].removeAt(0)
        }
    }

    private fun revealTableauCard(source: MoveSource) {
        if (source is MoveSource.Tableau) {
            val column = gameState.tableau[source.columnIndex]
            column.lastOrNull()?.let { card ->
                if (!card.isFaceUp) {
                    card.isFaceUp = true
                    gameState.score += 5
                }
            }
        }
    }
}

sealed class MoveSource {
    data class Waste(val dummy: Unit = Unit) : MoveSource()
    data class Tableau(val columnIndex: Int, val cardIndex: Int) : MoveSource()
    data class Foundation(val index: Int) : MoveSource()
}

sealed class MoveDestination {
    data class Foundation(val index: Int) : MoveDestination()
    data class Tableau(val index: Int) : MoveDestination()
}