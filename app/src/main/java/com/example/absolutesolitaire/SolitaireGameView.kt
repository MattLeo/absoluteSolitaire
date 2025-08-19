package com.example.absolutesolitaire

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.*

class SolitaireGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var game: KlondikeGame? = null

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var cardWidth = 0f
    private var cardHeight = 0f
    private var margin = 0f
    private var tableauSpacing = 0f
    private var topOffset = 0f

    private val stockRect = RectF()
    private val wasteRect = RectF()
    private val foundationRects = Array(4) { RectF() }
    private val tableauRects = Array(7) { RectF() }

    private var touchSlop = 0f
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragCurrentX = 0f
    private var dragCurrentY = 0f
    private var selectedCard: SelectedCard? = null
    private var draggedCards = mutableListOf<Card>()

    private var dragScale = 1.0f
    private var dragRotation = 0f
    private var dragAlpha = 255
    private var dragOffsetY = 0f
    private var isAnimatingDrop = false
    private var dropAnimator: ValueAnimator? = null

    private companion object {
        const val PICKUP_SCALE = 1.1f
        const val DRAG_ALPHA = 200
        const val MAX_ROTATION = 5f
        const val DROP_ANIMATION_DURATION = 200L
        const val CARD_RATIO = 1.4f
        const val MAX_TABLEAU_CARDS = 10
    }

    private var gameStateChangeCallback: (() -> Unit)? = null

    data class SelectedCard(
        val source: MoveSource,
        val cards: List<Card>,
        val startRect: RectF
    )

    init {
        setupPaints()
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topOffset = systemBars.top.toFloat()
            calculateLayout()
            insets
        }
    }

    private fun setupPaints() {
        cardPaint.color = Color.WHITE
        cardPaint.style = Paint.Style.FILL
        outlinePaint.color = Color.BLACK
        outlinePaint.style = Paint.Style.STROKE
        textPaint.color = Color.BLACK
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        backgroundPaint.color = Color.parseColor("#0F7B0F")
        shadowPaint.color = Color.argb(100, 0, 0, 0)
        shadowPaint.style = Paint.Style.FILL
    }

    fun setGame(game: KlondikeGame) {
        this.game = game
        calculateLayout()
    }

    fun setTopOffset(offset: Float) {
        topOffset = offset
        calculateLayout()
    }

    fun setOnGameStateChangeListener(callback: () -> Unit) {
        gameStateChangeCallback = callback
    }

    private fun notifyGameStateChanged() {
        gameStateChangeCallback?.invoke()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateLayout()
    }

    private fun calculateLayout() {
        margin = min(width, height) * 0.02f

        val isLandscape = width > height
        val screenHeight = height.toFloat()
        val usableHeight = screenHeight - topOffset - margin * 2

        val screenWidth = width.toFloat()
        val usableWidth = screenWidth - margin * 8
        cardWidth = usableWidth / 7f

        val topRowHeight = cardWidth * CARD_RATIO
        val gapHeight = margin * 1
        val remainingHeight = usableHeight - topRowHeight - gapHeight

        val worstCaseTableauHeight = topRowHeight + (MAX_TABLEAU_CARDS - 1) * (topRowHeight * 0.1f)

        if (worstCaseTableauHeight <= remainingHeight) {
            cardHeight = topRowHeight
            tableauSpacing = cardHeight * 0.3f
        } else {
            val maxTableauSpacing = remainingHeight / MAX_TABLEAU_CARDS
            tableauSpacing = max(maxTableauSpacing, topRowHeight * 0.3f)
            cardHeight = topRowHeight

            val actualWorstCase = cardHeight + (MAX_TABLEAU_CARDS - 1) * tableauSpacing
            if (actualWorstCase > remainingHeight) {
                val compressionRatio = remainingHeight / actualWorstCase
                cardHeight = cardHeight * compressionRatio
                cardWidth = cardHeight / CARD_RATIO
                tableauSpacing = tableauSpacing * compressionRatio
            }
        }

        tableauSpacing = max(tableauSpacing, 2f)

        updateTextSizes()
        layoutCards()
    }

    private fun updateTextSizes() {
        textPaint.textSize = max(6f, cardHeight * 0.12f)
        outlinePaint.strokeWidth = max(1f, cardWidth * 0.008f)
    }

    private fun layoutCards() {
        val startX = (width - (cardWidth * 7 + margin * 6)) / 2
        val topY = margin + topOffset

        stockRect.set(startX, topY, startX + cardWidth, topY + cardHeight)
        wasteRect.set(startX + cardWidth + margin, topY, startX + cardWidth * 2 + margin, topY + cardHeight)

        val foundationStartX = startX + cardWidth * 3 + margin * 3
        for (i in 0..3) {
            val x = foundationStartX + i * (cardWidth + margin)
            foundationRects[i].set(x, topY, x + cardWidth, topY + cardHeight)
        }

        val tableauY = topY + cardHeight + margin * 2
        for (i in 0..6) {
            val x = startX + i * (cardWidth + margin)
            tableauRects[i].set(x, tableauY, x + cardWidth, tableauY + cardHeight)
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(backgroundPaint.color)

        game?.let { game ->
            drawGameState(canvas, game.gameState)
            drawDraggedCards(canvas)
        }
    }

    private fun drawGameState(canvas: Canvas, gameState: GameState) {
        if (gameState.stock.isNotEmpty()) {
            drawCardBack(canvas, stockRect)
            drawCardCount(canvas, stockRect, gameState.stock.size)
        } else {
            drawEmptyPile(canvas, stockRect)
        }

        if (gameState.waste.isNotEmpty()) {
            drawCard(canvas, gameState.waste.last(), wasteRect)
        } else {
            drawEmptyPile(canvas, wasteRect)
        }

        for (i in 0..3) {
            val topCard = gameState.foundations[i].lastOrNull()
            if (topCard != null) {
                drawCard(canvas, topCard, foundationRects[i])
            } else {
                drawEmptyFoundation(canvas, foundationRects[i], i)
            }
        }

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
        if (draggedCards.contains(card)) return

        val cornerRadius = cardWidth * 0.06f
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cardPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, outlinePaint)

        val isRed = card.color == CardColor.RED
        textPaint.color = if (isRed) Color.RED else Color.BLACK

        val rankText = when (card.rank) {
            Rank.ACE -> "A"
            Rank.JACK -> "J"
            Rank.QUEEN -> "Q"
            Rank.KING -> "K"
            else -> card.rank.value.toString()
        }

        val suitText = when (card.suit) {
            Suit.HEARTS -> "♥"
            Suit.DIAMONDS -> "♦"
            Suit.CLUBS -> "♣"
            Suit.SPADES -> "♠"
        }

        val rankSize = max(6f, cardHeight * 0.1f)
        val suitSize = max(6f, cardHeight * 0.08f)

        val rankPaint = Paint(textPaint)
        rankPaint.textSize = rankSize

        val suitPaint = Paint(textPaint)
        suitPaint.textSize = suitSize

        val cornerMargin = cardWidth * 0.06f
        val rankY = rect.top + rankSize + cornerMargin
        val suitY = rankY + suitSize + 1f

        canvas.drawText(rankText, rect.left + cornerMargin, rankY, rankPaint)
        canvas.drawText(suitText, rect.left + cornerMargin, suitY, suitPaint)

        canvas.save()
        canvas.rotate(180f, rect.centerX(), rect.centerY())
        canvas.drawText(rankText, rect.left + cornerMargin, rankY, rankPaint)
        canvas.drawText(suitText, rect.left + cornerMargin, suitY, suitPaint)
        canvas.restore()

        if (cardHeight > 25f) {
            val centerSize = max(suitSize * 1.8f, cardHeight * 0.2f)
            val centerPaint = Paint(suitPaint)
            centerPaint.textSize = centerSize
            centerPaint.alpha = 60
            centerPaint.textAlign = Paint.Align.CENTER

            canvas.drawText(suitText, rect.centerX(), rect.centerY() + centerSize * 0.3f, centerPaint)
        }
    }

    private fun drawCardBack(canvas: Canvas, rect: RectF) {
        val backPaint = Paint(cardPaint)
        backPaint.color = Color.parseColor("#8B0000")

        val cornerRadius = cardWidth * 0.06f
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, backPaint)

        val borderPaint = Paint()
        borderPaint.color = Color.WHITE
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = max(1f, cardWidth * 0.008f)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        if (cardWidth > 15f && cardHeight > 20f) {
            val patternPaint = Paint()
            patternPaint.color = Color.parseColor("#FFD700")
            patternPaint.style = Paint.Style.FILL

            val centerX = rect.centerX()
            val centerY = rect.centerY()
            val size = min(cardWidth, cardHeight) * 0.15f

            canvas.drawOval(centerX - size, centerY - size, centerX + size, centerY + size, patternPaint)

            patternPaint.color = Color.parseColor("#8B0000")
            canvas.drawOval(centerX - size*0.6f, centerY - size*0.6f, centerX + size*0.6f, centerY + size*0.6f, patternPaint)
        }
    }

    private fun drawEmptyPile(canvas: Canvas, rect: RectF) {
        val emptyPaint = Paint()
        emptyPaint.color = Color.parseColor("#0A5A0A")
        emptyPaint.style = Paint.Style.STROKE
        emptyPaint.strokeWidth = max(1f, cardWidth * 0.008f)
        emptyPaint.pathEffect = DashPathEffect(floatArrayOf(4f, 2f), 0f)

        val cornerRadius = cardWidth * 0.06f
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, emptyPaint)
    }

    private fun drawEmptyFoundation(canvas: Canvas, rect: RectF, foundationIndex: Int) {
        drawEmptyPile(canvas, rect)

        val suitText = when (foundationIndex) {
            0 -> "♣"
            1 -> "♦"
            2 -> "♥"
            3 -> "♠"
            else -> ""
        }

        val suitPaint = Paint()
        suitPaint.textSize = max(8f, cardHeight * 0.15f)
        suitPaint.alpha = 120
        suitPaint.color = if (foundationIndex == 1 || foundationIndex == 2) Color.RED else Color.BLACK
        suitPaint.textAlign = Paint.Align.CENTER

        canvas.drawText(suitText, rect.centerX(), rect.centerY() + suitPaint.textSize * 0.3f, suitPaint)
    }

    private fun drawCardCount(canvas: Canvas, rect: RectF, count: Int) {
        val countPaint = Paint()
        countPaint.color = Color.WHITE
        countPaint.textSize = max(6f, cardHeight * 0.1f)
        countPaint.textAlign = Paint.Align.CENTER
        countPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(count.toString(), rect.centerX(), rect.bottom - 2f, countPaint)
    }

    private fun drawDraggedCards(canvas: Canvas) {
        selectedCard?.let {
            if (draggedCards.isNotEmpty()) {
                canvas.save()

                val centerX = dragCurrentX
                val centerY = dragCurrentY + dragOffsetY
                val shadowOffset = max(3f, cardWidth * 0.015f)

                drawCardShadow(canvas, centerX + shadowOffset, centerY + shadowOffset)
                canvas.rotate(dragRotation, centerX, centerY)

                draggedCards.forEachIndexed { index, card ->
                    val rect = RectF(
                        centerX - (cardWidth * dragScale) / 2,
                        centerY - (cardHeight * dragScale) / 2 + index * tableauSpacing * dragScale,
                        centerX + (cardWidth * dragScale) / 2,
                        centerY + (cardHeight * dragScale) / 2 + index * tableauSpacing * dragScale
                    )

                    val originalAlpha = cardPaint.alpha
                    cardPaint.alpha = dragAlpha
                    drawAnimatedCard(canvas, card, rect, dragScale)
                    cardPaint.alpha = originalAlpha
                }

                canvas.restore()
            }
        }
    }

    private fun drawCardShadow(canvas: Canvas, centerX: Float, centerY: Float) {
        draggedCards.forEachIndexed { index, _ ->
            val shadowRect = RectF(
                centerX - (cardWidth * dragScale) / 2,
                centerY - (cardHeight * dragScale) / 2 + index * tableauSpacing * dragScale,
                centerX + (cardWidth * dragScale) / 2,
                centerY + (cardHeight * dragScale) / 2 + index * tableauSpacing * dragScale
            )
            val cornerRadius = cardWidth * 0.06f * dragScale
            canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint)
        }
    }

    private fun drawAnimatedCard(canvas: Canvas, card: Card, rect: RectF, scale: Float) {
        val cornerRadius = cardWidth * 0.06f * scale
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cardPaint)

        val borderPaint = Paint(outlinePaint)
        borderPaint.strokeWidth = max(1f, cardWidth * 0.008f * scale)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        val isRed = card.color == CardColor.RED
        val animatedTextPaint = Paint()
        animatedTextPaint.color = if (isRed) Color.RED else Color.BLACK
        animatedTextPaint.textAlign = Paint.Align.LEFT
        animatedTextPaint.typeface = Typeface.DEFAULT_BOLD

        val rankText = when (card.rank) {
            Rank.ACE -> "A"
            Rank.JACK -> "J"
            Rank.QUEEN -> "Q"
            Rank.KING -> "K"
            else -> card.rank.value.toString()
        }

        val suitText = when (card.suit) {
            Suit.HEARTS -> "♥"
            Suit.DIAMONDS -> "♦"
            Suit.CLUBS -> "♣"
            Suit.SPADES -> "♠"
        }

        val rankSize = max(6f, cardHeight * 0.1f * scale)
        val suitSize = max(6f, cardHeight * 0.08f * scale)

        animatedTextPaint.textSize = rankSize
        val cornerMargin = cardWidth * 0.06f * scale
        val rankY = rect.top + rankSize + cornerMargin
        val suitY = rankY + suitSize + 1f

        canvas.drawText(rankText, rect.left + cornerMargin, rankY, animatedTextPaint)

        animatedTextPaint.textSize = suitSize
        canvas.drawText(suitText, rect.left + cornerMargin, suitY, animatedTextPaint)

        if (cardHeight * scale > 25f) {
            val centerSize = max(suitSize * 1.8f, cardHeight * 0.2f * scale)
            val centerPaint = Paint(animatedTextPaint)
            centerPaint.textSize = centerSize
            centerPaint.alpha = 60
            centerPaint.textAlign = Paint.Align.CENTER

            canvas.drawText(suitText, rect.centerX(), rect.centerY() + centerSize * 0.3f, centerPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isAnimatingDrop) return true
                dragStartX = event.x
                dragStartY = event.y
                dragCurrentX = event.x
                dragCurrentY = event.y
                handleTouchDown(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isAnimatingDrop) return true
                dragCurrentX = event.x
                dragCurrentY = event.y

                if (!isDragging) {
                    val deltaX = abs(event.x - dragStartX)
                    val deltaY = abs(event.y - dragStartY)
                    if (deltaX > touchSlop || deltaY > touchSlop) {
                        isDragging = true
                        startDragAnimation()
                    }
                } else {
                    updateDragAnimation(event.x, event.y)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (isAnimatingDrop) return true

                if (isDragging) {
                    handleDrop(event.x, event.y)
                } else {
                    handleTap(event.x, event.y)
                }

                endDragAnimation()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startDragAnimation() {
        ValueAnimator.ofFloat(1.0f, PICKUP_SCALE).apply {
            duration = 150
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                dragScale = animator.animatedValue as Float
                dragAlpha = (255 - (255 - DRAG_ALPHA) * animator.animatedFraction).toInt()
                dragOffsetY = (-cardHeight * 0.1f) * animator.animatedFraction
                invalidate()
            }
            start()
        }
    }

    private fun updateDragAnimation(x: Float, y: Float) {
        val deltaX = x - dragStartX
        val rotationFactor = deltaX / width
        dragRotation = (rotationFactor * MAX_ROTATION).coerceIn(-MAX_ROTATION, MAX_ROTATION)
    }

    private fun endDragAnimation() {
        isDragging = false

        ValueAnimator.ofFloat(dragScale, 1.0f).apply {
            duration = 150
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                dragScale = animator.animatedValue as Float
                dragAlpha = (DRAG_ALPHA + (255 - DRAG_ALPHA) * animator.animatedFraction).toInt()
                dragOffsetY = (-cardHeight * 0.1f) * (1 - animator.animatedFraction)
                dragRotation *= (1 - animator.animatedFraction)
                invalidate()
            }
            doOnEnd {
                selectedCard = null
                draggedCards.clear()
                dragScale = 1.0f
                dragAlpha = 255
                dragOffsetY = 0f
                dragRotation = 0f
                invalidate()
            }
            start()
        }
    }

    private fun animateCardDrop(fromX: Float, fromY: Float, toRect: RectF, onComplete: () -> Unit) {
        isAnimatingDrop = true

        dropAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DROP_ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float

                dragCurrentX = fromX + (toRect.centerX() - fromX) * progress
                dragCurrentY = fromY + (toRect.centerY() - fromY) * progress

                dragScale = PICKUP_SCALE + (1.0f - PICKUP_SCALE) * progress
                dragAlpha = (DRAG_ALPHA + (255 - DRAG_ALPHA) * progress).toInt()
                dragRotation *= (1 - progress)

                invalidate()
            }
            doOnEnd {
                isAnimatingDrop = false
                onComplete()
            }
            start()
        }
    }

    private fun getDestinationRect(destination: MoveDestination): RectF {
        return when (destination) {
            is MoveDestination.Foundation -> foundationRects[destination.index]
            is MoveDestination.Tableau -> {
                val rect = RectF(tableauRects[destination.index])
                game?.gameState?.tableau?.get(destination.index)?.let { column ->
                    rect.offset(0f, column.size * tableauSpacing)
                }
                rect
            }
        }
    }

    private fun ValueAnimator.doOnEnd(action: () -> Unit) {
        addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationEnd(animation: android.animation.Animator) { action() }
            override fun onAnimationCancel(animation: android.animation.Animator) { action() }
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
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
            if (stockRect.contains(x, y) || wasteRect.contains(x, y)) {
                if (game.dealFromStock()) {
                    invalidate()
                    notifyGameStateChanged()
                }
            }
        }
    }

    private fun handleDrop(x: Float, y: Float) {
        selectedCard?.let { selected ->
            game?.let { game ->
                val destination = findDropTarget(x, y)
                destination?.let { dest ->
                    if (game.moveCard(selected.source, dest)) {
                        val targetRect = getDestinationRect(dest)
                        animateCardDrop(dragCurrentX, dragCurrentY, targetRect) {
                            invalidate()
                            notifyGameStateChanged()
                        }
                        return
                    }
                }
            }
        }
        endDragAnimation()
    }

    private fun findCardAtPosition(x: Float, y: Float, gameState: GameState): SelectedCard? {
        if (wasteRect.contains(x, y) && gameState.waste.isNotEmpty()) {
            val card = gameState.waste.last()
            return SelectedCard(MoveSource.Waste(), listOf(card), wasteRect)
        }

        for (i in 0..3) {
            if (foundationRects[i].contains(x, y) && gameState.foundations[i].isNotEmpty()) {
                val card = gameState.foundations[i].last()
                return SelectedCard(MoveSource.Foundation(i), listOf(card), foundationRects[i])
            }
        }

        for (col in 0..6) {
            val cards = gameState.tableau[col]
            if (cards.isNotEmpty()) {
                val cardIndex = findTableauCardIndex(x, y, col, cards)
                if (cardIndex >= 0 && cards[cardIndex].isFaceUp) {
                    val selectedCards = cards.subList(cardIndex, cards.size)
                    val rect = RectF(tableauRects[col])
                    rect.offset(0f, cardIndex * tableauSpacing)
                    return SelectedCard(MoveSource.Tableau(col, cardIndex), selectedCards, rect)
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
        for (i in 0..3) {
            if (foundationRects[i].contains(x, y)) {
                return MoveDestination.Foundation(i)
            }
        }

        for (i in 0..6) {
            if (isInTableauColumn(x, y, i)) {
                return MoveDestination.Tableau(i)
            }
        }

        return null
    }

    private fun isInTableauColumn(x: Float, y: Float, columnIndex: Int): Boolean {
        val rect = tableauRects[columnIndex]
        val extendedRect = RectF(rect.left, rect.top, rect.right, height.toFloat())
        return extendedRect.contains(x, y)
    }
}