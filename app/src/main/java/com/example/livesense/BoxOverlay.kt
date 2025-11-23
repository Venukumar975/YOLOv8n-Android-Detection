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
    // Assuming YOLO model input size is 640x640, matching Live/Image logic
    private val YOLO_SIZE = 640f

    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
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

    fun setBoxes(newBoxes: List<Box>) {
        boxes = newBoxes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Scaling factor for the current screen size
        val xScale = width / YOLO_SIZE
        val yScale = height / YOLO_SIZE

        for (box in boxes) {
            // Apply scale factor to the absolute (0-640) coordinates from YOLO
            val left = box.rect.left * xScale
            val top = box.rect.top * yScale
            val right = box.rect.right * xScale
            val bottom = box.rect.bottom * yScale

            canvas.drawRect(left, top, right, bottom, boxPaint)

            val tag = box.label
            val textWidth = textPaint.measureText(tag)
            val textHeight = textPaint.descent() - textPaint.ascent()

            // Text positioning is now relative to the scaled box
            val textPadding = 8f
            val textX = left + textPadding

            val textBgRight = left + textWidth + 2 * textPadding
            val textBgTop = top - textHeight - textPadding * 2

            // Check if text would go off screen (top edge)
            if (textBgTop < 0) {
                // Draw text inside the box if no space above
                canvas.drawRect(left, top, textBgRight, top + textHeight + 2 * textPadding, textBackgroundPaint)
                canvas.drawText(tag, textX, top + textHeight + textPadding, textPaint)
            } else {
                // Draw text above the box (standard)
                canvas.drawRect(left, textBgTop, textBgRight, top, textBackgroundPaint)
                canvas.drawText(tag, textX, top - textPadding, textPaint)
            }
        }
    }

    data class Box(val rect: RectF, val label: String)
}