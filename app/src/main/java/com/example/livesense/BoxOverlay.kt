package com.example.livesense

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.math.max

class BoxOverlay(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var boxes = listOf<Box>()
    private var sourceWidth = 0f
    private var sourceHeight = 0f
    private var isFitCenter = false // NEW: Toggle between "Zoom" and "Fit"

    // Debug Text Paint
    private val debugTextPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
        textSize = 40f
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val debugPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }

    private val textBackgroundPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textSize = 50f
    }

    // UPDATED: Added 'fitCenter' parameter with a default of false
    fun setBoxes(newBoxes: List<Box>, bitmapWidth: Int, bitmapHeight: Int, fitCenter: Boolean = false) {
        boxes = newBoxes
        sourceWidth = bitmapWidth.toFloat()
        sourceHeight = bitmapHeight.toFloat()
        isFitCenter = fitCenter
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw Debug Border
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), debugPaint)

        if (sourceWidth <= 0f || sourceHeight <= 0f) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // 2. CRITICAL FIX: Choose Scale Logic
        // If isFitCenter is TRUE (Images), use MIN to see the whole image.
        // If isFitCenter is FALSE (Camera), use MAX to fill the screen.
        val scale = if (isFitCenter) {
            min(viewWidth / sourceWidth, viewHeight / sourceHeight)
        } else {
            max(viewWidth / sourceWidth, viewHeight / sourceHeight)
        }

        val dx = (viewWidth - sourceWidth * scale) / 2f
        val dy = (viewHeight - sourceHeight * scale) / 2f

        // Debug info
        val modeStr = if (isFitCenter) "FIT" else "FILL"
        val stats = "$modeStr Img:${sourceWidth.toInt()}x${sourceHeight.toInt()} View:${viewWidth.toInt()}x${viewHeight.toInt()} Scale:%.2f".format(scale)
        canvas.drawText(stats, 50f, 100f, debugTextPaint)

        if (boxes.isNotEmpty()) {
            val b = boxes[0]
            val tLeft = b.rect.left * scale + dx
            val tTop = b.rect.top * scale + dy
            canvas.drawText("Box Screen: ${tLeft.toInt()}, ${tTop.toInt()}", 50f, 220f, debugTextPaint)
        }

        for (box in boxes) {
            val left = box.rect.left * scale + dx
            val top = box.rect.top * scale + dy
            val right = box.rect.right * scale + dx
            val bottom = box.rect.bottom * scale + dy

            canvas.drawRect(left, top, right, bottom, boxPaint)

            val tag = box.label
            val textWidth = textPaint.measureText(tag)
            val textHeight = textPaint.descent() - textPaint.ascent()
            val textPadding = 8f
            val textX = left + textPadding
            val textBgRight = left + textWidth + 2 * textPadding
            val textBgTop = top - textHeight - textPadding * 2

            if (textBgTop < 0) {
                canvas.drawRect(left, top, textBgRight, top + textHeight + 2 * textPadding, textBackgroundPaint)
                canvas.drawText(tag, textX, top + textHeight + textPadding, textPaint)
            } else {
                canvas.drawRect(left, textBgTop, textBgRight, top, textBackgroundPaint)
                canvas.drawText(tag, textX, top - textPadding, textPaint)
            }
        }
    }

    data class Box(val rect: RectF, val label: String)
}