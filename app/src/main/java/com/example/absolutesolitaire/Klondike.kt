package com.example.absolutesolitaire

class KlondikeGame {
    private val deck = Deck()
    var gameState = GameState()
        private set

    // Game completion tracking
    var isGameCompleted = false
        private set

    var completionTime: Long? = null
        private set

    private var gameStartTime: Long = 0

    fun newGame() {
        deck.reset()
        gameState = GameState()
        isGameCompleted = false
        completionTime = null
        gameStartTime = System.currentTimeMillis()
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
        if (isGameCompleted) return false // Can't make moves after game is won

        return if (gameState.stock.isNotEmpty()) {
            val card = gameState.stock.removeAt(gameState.stock.lastIndex)
            card.isFaceUp = true
            gameState.waste.add(card)
            gameState.moves++
            true
        } else if (gameState.waste.isNotEmpty()) {
            // Reset stock from waste when stock is empty
            while (gameState.waste.isNotEmpty()) {
                val card = gameState.waste.removeAt(gameState.waste.lastIndex)
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
        if (isGameCompleted) return false // Can't make moves after game is won

        val card = getCardFromSource(from) ?: return false

        val moveSuccessful = when (to) {
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

        // Check for game completion after each successful move
        if (moveSuccessful) {
            checkGameCompletion()
        }

        return moveSuccessful
    }

    private fun checkGameCompletion() {
        if (isGameCompleted) return // Already completed

        // Check if all foundation piles are complete (13 cards each)
        val foundationComplete = gameState.foundations.all { foundation ->
            foundation.size == 13
        }

        if (foundationComplete) {
            isGameCompleted = true
            completionTime = System.currentTimeMillis()
            gameState.score += calculateCompletionBonus()
        }
    }

    private fun calculateCompletionBonus(): Int {
        val timeBonus = when {
            getGameDurationSeconds() < 120 -> 500 // Under 2 minutes
            getGameDurationSeconds() < 300 -> 300 // Under 5 minutes
            getGameDurationSeconds() < 600 -> 100 // Under 10 minutes
            else -> 50
        }

        val moveBonus = when {
            gameState.moves < 150 -> 200 // Very efficient
            gameState.moves < 200 -> 100 // Efficient
            gameState.moves < 300 -> 50  // Average
            else -> 0
        }

        return timeBonus + moveBonus
    }

    fun getGameDurationSeconds(): Long {
        val endTime = completionTime ?: System.currentTimeMillis()
        return (endTime - gameStartTime) / 1000
    }

    fun getFormattedGameTime(): String {
        val seconds = getGameDurationSeconds()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    /**
     * Returns the number of cards in foundation piles
     */
    fun getFoundationCardCount(): Int {
        return gameState.foundations.sumOf { it.size }
    }

    /**
     * Returns completion percentage (0-100)
     */
    fun getCompletionPercentage(): Int {
        return (getFoundationCardCount() * 100) / 52
    }

    /**
     * Check if the game is potentially winnable
     * This is a basic check - a more sophisticated analysis would be needed for accurate detection
     */
    fun isPotentiallyWinnable(): Boolean {
        if (isGameCompleted) return true

        // Basic check: if there are face-down cards, there's still potential
        val hasHiddenCards = gameState.tableau.any { column ->
            column.any { !it.isFaceUp }
        }

        // If no hidden cards, check if any moves are possible
        return hasHiddenCards || hasAvailableMoves()
    }

    private fun hasAvailableMoves(): Boolean {
        // Check if waste card can move anywhere
        gameState.waste.lastOrNull()?.let { wasteCard ->
            // Check foundations
            gameState.foundations.forEachIndexed { index, foundation ->
                if (wasteCard.canPlaceOnFoundation(foundation.lastOrNull())) {
                    return true
                }
            }

            // Check tableau
            gameState.tableau.forEach { column ->
                val topCard = column.lastOrNull()
                if ((topCard == null && wasteCard.rank == Rank.KING) ||
                    (topCard != null && wasteCard.canPlaceOnTableau(topCard))) {
                    return true
                }
            }
        }

        // Check if any tableau cards can move
        gameState.tableau.forEachIndexed { colIndex, column ->
            column.forEachIndexed { cardIndex, card ->
                if (card.isFaceUp) {
                    // Check if this card can move to foundations
                    gameState.foundations.forEach { foundation ->
                        if (card.canPlaceOnFoundation(foundation.lastOrNull())) {
                            return true
                        }
                    }

                    // Check if this card can move to other tableau columns
                    gameState.tableau.forEachIndexed { targetCol, targetColumn ->
                        if (targetCol != colIndex) {
                            val targetCard = targetColumn.lastOrNull()
                            if ((targetCard == null && card.rank == Rank.KING) ||
                                (targetCard != null && card.canPlaceOnTableau(targetCard))) {
                                return true
                            }
                        }
                    }
                }
            }
        }

        return false
    }

    private fun moveCardSequence(from: MoveSource, toTableauIndex: Int) {
        when (from) {
            is MoveSource.Waste -> {
                val card = gameState.waste.removeAt(gameState.waste.lastIndex)
                gameState.tableau[toTableauIndex].add(card)
            }
            is MoveSource.Tableau -> {
                val fromColumn = gameState.tableau[from.columnIndex]
                val cards = fromColumn.subList(from.cardIndex, fromColumn.size).toList()
                repeat(cards.size) {
                    fromColumn.removeAt(fromColumn.lastIndex)
                }
                gameState.tableau[toTableauIndex].addAll(cards)
                revealTableauCard(from)
            }
            is MoveSource.Foundation -> {
                val card = gameState.foundations[from.index].removeAt(gameState.foundations[from.index].lastIndex)
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
            is MoveSource.Waste -> {
                if (gameState.waste.isNotEmpty()) {
                    gameState.waste.removeAt(gameState.waste.lastIndex)
                }
            }
            is MoveSource.Tableau -> {
                val column = gameState.tableau[source.columnIndex]
                if (source.cardIndex < column.size) {
                    val cardsToRemove = column.size - source.cardIndex
                    repeat(cardsToRemove) {
                        column.removeAt(column.lastIndex)
                    }
                }
            }
            is MoveSource.Foundation -> {
                val foundation = gameState.foundations[source.index]
                if (foundation.isNotEmpty()) {
                    foundation.removeAt(foundation.lastIndex)
                }
            }
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