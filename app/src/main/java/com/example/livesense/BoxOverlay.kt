package com.example.livesense

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class BoxOverlay(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var boxes = listOf<Box>()
    private var sourceWidth = 0f
    private var sourceHeight = 0f

    private val debugTextPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
        textSize = 40f
        setShadowLayer(5f, 0f, 0f, Color.BLACK) // Shadow allows reading on bright backgrounds
    }
    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    // DEBUG PAINT: Draws a green border around the view to prove it is visible
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

    fun setBoxes(newBoxes: List<Box>, bitmapWidth: Int, bitmapHeight: Int) {
        boxes = newBoxes
        sourceWidth = bitmapWidth.toFloat()
        sourceHeight = bitmapHeight.toFloat()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw Debug Border (If you don't see this green box, the view is hidden)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), debugPaint)

        if (sourceWidth <= 0f || sourceHeight <= 0f) return

        // 2. Calculate Scale to Fill Screen (Center Crop logic)
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // Calculate the scale needed to make the image fill the screen
        val scale = java.lang.Float.max(viewWidth / sourceWidth, viewHeight / sourceHeight)

        // Calculate the shift to center the image
        val dx = (viewWidth - sourceWidth * scale) / 2f
        val dy = (viewHeight - sourceHeight * scale) / 2f
        // --- PASTE THIS START ---

        // Print the math stats to the screen
        val stats = "Img: ${sourceWidth.toInt()}x${sourceHeight.toInt()} View: ${viewWidth.toInt()}x${viewHeight.toInt()} Scale: %.2f".format(scale)
        canvas.drawText(stats, 50f, 100f, debugTextPaint)

        if (boxes.isNotEmpty()) {
            val b = boxes[0]
            val tLeft = b.rect.left * scale + dx
            val tTop = b.rect.top * scale + dy

            // Print coordinate data
            canvas.drawText("Box Raw: ${b.rect.toShortString()}", 50f, 160f, debugTextPaint)
            canvas.drawText("Box Screen: ${tLeft.toInt()}, ${tTop.toInt()}", 50f, 220f, debugTextPaint)
        } else {
            canvas.drawText("No Objects Detected", 50f, 160f, debugTextPaint)
        }

        // --- PASTE THIS END ---

        for (box in boxes) {
            // 3. Map Coordinates
            val left = box.rect.left * scale + dx
            val top = box.rect.top * scale + dy
            val right = box.rect.right * scale + dx
            val bottom = box.rect.bottom * scale + dy

            // 4. Draw Box
            canvas.drawRect(left, top, right, bottom, boxPaint)

            // 5. Draw Text
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