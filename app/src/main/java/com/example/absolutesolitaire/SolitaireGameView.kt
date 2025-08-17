package com.example.absolutesolitaire

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.*

class SolitaireGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var game: KlondikeGame? = null

    // Drawing resources
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Card dimensions and spacing
    private var cardWidth = 0f
    private var cardHeight = 0f
    private var margin = 0f
    private var tableauSpacing = 0f

    // Layout areas
    private val stockRect = RectF()
    private val wasteRect = RectF()
    private val foundationRects = Array(4) { RectF() }
    private val tableauRects = Array(7) { RectF() }

    // Touch handling
    private var touchSlop = 0f
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragCurrentX = 0f
    private var dragCurrentY = 0f
    private var selectedCard: SelectedCard? = null
    private var draggedCards = mutableListOf<Card>()

    data class SelectedCard(
        val source: MoveSource,
        val cards: List<Card>,
        val startRect: RectF
    )

    init {
        setupPaints()
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    }

    private fun setupPaints() {
        // Card background
        cardPaint.color = Color.WHITE
        cardPaint.style = Paint.Style.FILL

        // Card outline
        outlinePaint.color = Color.BLACK
        outlinePaint.style = Paint.Style.STROKE
        outlinePaint.strokeWidth = 2f

        // Text for rank/suit
        textPaint.color = Color.BLACK
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 24f
        textPaint.typeface = Typeface.DEFAULT_BOLD

        // Background
        backgroundPaint.color = Color.parseColor("#0F7B0F") // Green felt
    }

    fun setGame(game: KlondikeGame) {
        this.game = game
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateLayout()
    }

    private fun calculateLayout() {
        margin = width * 0.02f
        cardWidth = (width - margin * 8) / 7f // 7 columns with margins
        cardHeight = cardWidth * 1.4f // Standard card ratio
        tableauSpacing = cardHeight * 0.25f

        // Top row: Stock, Waste, and 4 Foundations
        val topY = margin

        // Stock pile (top-left)
        stockRect.set(margin, topY, margin + cardWidth, topY + cardHeight)

        // Waste pile (next to stock)
        wasteRect.set(margin * 2 + cardWidth, topY, margin * 2 + cardWidth * 2, topY + cardHeight)

        // Foundation piles (top-right, 4 piles)
        for (i in 0..3) {
            val x = width - margin - (4 - i) * (cardWidth + margin)
            foundationRects[i].set(x, topY, x + cardWidth, topY + cardHeight)
        }

        // Tableau columns (bottom, 7 columns)
        val tableauY = topY + cardHeight + margin * 2
        for (i in 0..6) {
            val x = margin + i * (cardWidth + margin)
            tableauRects[i].set(x, tableauY, x + cardWidth, tableauY + cardHeight)
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        canvas.drawColor(backgroundPaint.color)

        game?.let { game ->
            drawGameState(canvas, game.gameState)
            drawDraggedCards(canvas)
        }
    }

    private fun drawGameState(canvas: Canvas, gameState: GameState) {
        // Draw stock pile
        if (gameState.stock.isNotEmpty()) {
            drawCardBack(canvas, stockRect)
            drawCardCount(canvas, stockRect, gameState.stock.size)
        } else {
            drawEmptyPile(canvas, stockRect)
        }

        // Draw waste pile
        if (gameState.waste.isNotEmpty()) {
            val topCard = gameState.waste.last()
            drawCard(canvas, topCard, wasteRect)
        } else {
            drawEmptyPile(canvas, wasteRect)
        }

        // Draw foundation piles
        for (i in 0..3) {
            val topCard = gameState.foundations[i].lastOrNull()
            if (topCard != null) {
                drawCard(canvas, topCard, foundationRects[i])
            } else {
                drawEmptyFoundation(canvas, foundationRects[i], i)
            }
        }

        // Draw tableau columns
        for (col in 0..6) {
            drawTableauColumn(canvas, gameState.tableau[col], col)
        }
    }

    private fun drawTableauColumn(canvas: Canvas, cards: List<Card>, columnIndex: Int) {
        val baseRect = tableauRects[columnIndex]

        if (cards.isEmpty()) {
            drawEmptyPile(canvas, baseRect)
            return
        }

        cards.forEachIndexed { index, card ->
            val rect = RectF(baseRect)
            rect.offset(0f, index * tableauSpacing)

            if (card.isFaceUp) {
                drawCard(canvas, card, rect)
            } else {
                drawCardBack(canvas, rect)
            }
        }
    }

    private fun drawCard(canvas: Canvas, card: Card, rect: RectF) {
        // Skip if this card is being dragged
        if (draggedCards.contains(card)) return

        // Draw card background
        canvas.drawRoundRect(rect, 8f, 8f, cardPaint)
        canvas.drawRoundRect(rect, 8f, 8f, outlinePaint)

        // Set text color based on suit
        textPaint.color = if (card.color == CardColor.RED) Color.RED else Color.BLACK

        // Draw rank
        val rankText = when (card.rank) {
            Rank.ACE -> "A"
            Rank.JACK -> "J"
            Rank.QUEEN -> "Q"
            Rank.KING -> "K"
            else -> card.rank.value.toString()
        }

        // Draw suit symbol
        val suitText = when (card.suit) {
            Suit.HEARTS -> "♥"
            Suit.DIAMONDS -> "♦"
            Suit.CLUBS -> "♣"
            Suit.SPADES -> "♠"
        }

        // Draw text in top-left and bottom-right
        val textX = rect.left + cardWidth * 0.15f
        val textY = rect.top + cardHeight * 0.25f

        canvas.drawText(rankText, textX, textY, textPaint)
        canvas.drawText(suitText, textX, textY + 30f, textPaint)

        // Draw large suit in center
        val centerTextPaint = Paint(textPaint)
        centerTextPaint.textSize = 48f
        centerTextPaint.alpha = 50
        canvas.drawText(suitText, rect.centerX(), rect.centerY() + 16f, centerTextPaint)
    }

    private fun drawCardBack(canvas: Canvas, rect: RectF) {
        val backPaint = Paint(cardPaint)
        backPaint.color = Color.parseColor("#000080") // Dark blue

        canvas.drawRoundRect(rect, 8f, 8f, backPaint)
        canvas.drawRoundRect(rect, 8f, 8f, outlinePaint)

        // Draw pattern
        val patternPaint = Paint()
        patternPaint.color = Color.parseColor("#4169E1")
        patternPaint.style = Paint.Style.STROKE
        patternPaint.strokeWidth = 2f

        val centerX = rect.centerX()
        val centerY = rect.centerY()
        val size = min(rect.width(), rect.height()) * 0.3f

        canvas.drawRect(
            centerX - size/2, centerY - size/2,
            centerX + size/2, centerY + size/2,
            patternPaint
        )
    }

    private fun drawEmptyPile(canvas: Canvas, rect: RectF) {
        val emptyPaint = Paint()
        emptyPaint.color = Color.parseColor("#0A5A0A")
        emptyPaint.style = Paint.Style.STROKE
        emptyPaint.strokeWidth = 3f
        emptyPaint.pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)

        canvas.drawRoundRect(rect, 8f, 8f, emptyPaint)
    }

    private fun drawEmptyFoundation(canvas: Canvas, rect: RectF, foundationIndex: Int) {
        drawEmptyPile(canvas, rect)

        // Draw suit symbol for foundation
        val suitText = when (foundationIndex) {
            0 -> "♣"
            1 -> "♦"
            2 -> "♥"
            3 -> "♠"
            else -> ""
        }

        val suitPaint = Paint(textPaint)
        suitPaint.textSize = 32f
        suitPaint.alpha = 100
        suitPaint.color = if (foundationIndex == 1 || foundationIndex == 2) Color.RED else Color.BLACK

        canvas.drawText(suitText, rect.centerX(), rect.centerY() + 12f, suitPaint)
    }

    private fun drawCardCount(canvas: Canvas, rect: RectF, count: Int) {
        val countPaint = Paint(textPaint)
        countPaint.color = Color.WHITE
        countPaint.textSize = 16f
        canvas.drawText(count.toString(), rect.centerX(), rect.bottom - 5f, countPaint)
    }

    private fun drawDraggedCards(canvas: Canvas) {
        selectedCard?.let { selected ->
            draggedCards.forEachIndexed { index, card ->
                val rect = RectF(
                    dragCurrentX - cardWidth / 2,
                    dragCurrentY - cardHeight / 2 + index * tableauSpacing,
                    dragCurrentX + cardWidth / 2,
                    dragCurrentY + cardHeight / 2 + index * tableauSpacing
                )

                // Draw with slight transparency
                val alpha = cardPaint.alpha
                cardPaint.alpha = 180
                drawCard(canvas, card, rect)
                cardPaint.alpha = alpha
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.x
                dragStartY = event.y
                dragCurrentX = event.x
                dragCurrentY = event.y
                handleTouchDown(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                dragCurrentX = event.x
                dragCurrentY = event.y

                if (!isDragging) {
                    val deltaX = abs(event.x - dragStartX)
                    val deltaY = abs(event.y - dragStartY)
                    if (deltaX > touchSlop || deltaY > touchSlop) {
                        isDragging = true
                        invalidate()
                    }
                } else {
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    handleDrop(event.x, event.y)
                } else {
                    handleTap(event.x, event.y)
                }

                isDragging = false
                selectedCard = null
                draggedCards.clear()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouchDown(x: Float, y: Float) {
        game?.let { game ->
            selectedCard = findCardAtPosition(x, y, game.gameState)
            selectedCard?.let { selected ->
                draggedCards.clear()
                draggedCards.addAll(selected.cards)
            }
        }
    }

    private fun handleTap(x: Float, y: Float) {
        game?.let { game ->
            // Handle stock pile tap
            if (stockRect.contains(x, y) || wasteRect.contains(x, y)) {
                game.dealFromStock()
                invalidate()
            }
        }
    }

    private fun handleDrop(x: Float, y: Float) {
        selectedCard?.let { selected ->
            game?.let { game ->
                val destination = findDropTarget(x, y)
                destination?.let { dest ->
                    if (game.moveCard(selected.source, dest)) {
                        invalidate()
                    }
                }
            }
        }
    }

    private fun findCardAtPosition(x: Float, y: Float, gameState: GameState): SelectedCard? {
        // Check waste pile
        if (wasteRect.contains(x, y) && gameState.waste.isNotEmpty()) {
            val card = gameState.waste.last()
            return SelectedCard(
                MoveSource.Waste(),
                listOf(card),
                wasteRect
            )
        }

        // Check foundation piles
        for (i in 0..3) {
            if (foundationRects[i].contains(x, y) && gameState.foundations[i].isNotEmpty()) {
                val card = gameState.foundations[i].last()
                return SelectedCard(
                    MoveSource.Foundation(i),
                    listOf(card),
                    foundationRects[i]
                )
            }
        }

        // Check tableau columns
        for (col in 0..6) {
            val cards = gameState.tableau[col]
            if (cards.isNotEmpty()) {
                val cardIndex = findTableauCardIndex(x, y, col, cards)
                if (cardIndex >= 0 && cards[cardIndex].isFaceUp) {
                    val selectedCards = cards.subList(cardIndex, cards.size)
                    val rect = RectF(tableauRects[col])
                    rect.offset(0f, cardIndex * tableauSpacing)

                    return SelectedCard(
                        MoveSource.Tableau(col, cardIndex),
                        selectedCards,
                        rect
                    )
                }
            }
        }

        return null
    }

    private fun findTableauCardIndex(x: Float, y: Float, columnIndex: Int, cards: List<Card>): Int {
        val baseRect = tableauRects[columnIndex]

        for (i in cards.indices.reversed()) {
            val cardRect = RectF(baseRect)
            cardRect.offset(0f, i * tableauSpacing)
            if (cardRect.contains(x, y)) {
                return i
            }
        }
        return -1
    }

    private fun findDropTarget(x: Float, y: Float): MoveDestination? {
        // Check foundation piles
        for (i in 0..3) {
            if (foundationRects[i].contains(x, y)) {
                return MoveDestination.Foundation(i)
            }
        }

        // Check tableau columns
        for (i in 0..6) {
            if (isInTableauColumn(x, y, i)) {
                return MoveDestination.Tableau(i)
            }
        }

        return null
    }

    private fun isInTableauColumn(x: Float, y: Float, columnIndex: Int): Boolean {
        val rect = tableauRects[columnIndex]
        // Extend the drop zone vertically for easier dropping
        val extendedRect = RectF(rect.left, rect.top, rect.right, height.toFloat())
        return extendedRect.contains(x, y)
    }
}