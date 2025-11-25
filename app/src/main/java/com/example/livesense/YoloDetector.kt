package com.example.livesense

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import kotlin.math.max
import kotlin.math.min

class YoloDetector(context: Context) {

    private var interpreter: Interpreter? = null
    private val INPUT_SIZE = 640
    var confidenceThreshold: Float = 0.5f
    private val NMS_THRESHOLD = 0.5f

    private var inputImageBuffer = TensorImage(DataType.FLOAT32)
    private var outputBuffer: TensorBuffer? = null

    init {
        try {
            val modelFile = FileUtil.loadMappedFile(context, "yolov8n.tflite")
            val options = Interpreter.Options()
            interpreter = Interpreter(modelFile, options)

            val outShape = interpreter!!.getOutputTensor(0).shape()
            outputBuffer = TensorBuffer.createFixedSize(outShape, DataType.FLOAT32)
            Log.i("YOLO_INIT", "Model loaded. Output shape: ${outShape.contentToString()}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class Detection(
        val rect: RectF,
        val score: Float,
        val classIdx: Int
    )

    fun detect(sourceBitmap: Bitmap): Pair<List<BoxOverlay.Box>, Float> {
        if (interpreter == null) return Pair(emptyList(), 0f)

        // 1. LETTERBOXING: Scale image to fit 640x640 maintaining aspect ratio
        val matrix = Matrix()
        val scaleFactor = min(INPUT_SIZE.toFloat() / sourceBitmap.width, INPUT_SIZE.toFloat() / sourceBitmap.height)
        matrix.postScale(scaleFactor, scaleFactor)

        // Calculate padding to center the image
        val scaledWidth = sourceBitmap.width * scaleFactor
        val scaledHeight = sourceBitmap.height * scaleFactor
        val padX = (INPUT_SIZE - scaledWidth) / 2f
        val padY = (INPUT_SIZE - scaledHeight) / 2f

        matrix.postTranslate(padX, padY)

        val inputBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(inputBitmap)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(sourceBitmap, matrix, Paint())

        // 2. PROCESS IMAGE
        val processor = ImageProcessor.Builder()
            .add(NormalizeOp(0f, 255f))
            .add(CastOp(DataType.FLOAT32))
            .build()

        inputImageBuffer.load(inputBitmap)
        val processedImage = processor.process(inputImageBuffer)

        // 3. RUN INFERENCE
        interpreter!!.run(processedImage.buffer, outputBuffer!!.buffer.rewind())

        val outArr = outputBuffer!!.floatArray
        val outShape = interpreter!!.getOutputTensor(0).shape()

        // 4. PROCESS OUTPUT
        val detections = processYoloOutput(outArr, outShape)
        val nmsDetections = nonMaxSuppression(detections, NMS_THRESHOLD)

        // 5. REVERSE LETTERBOX: Map boxes back to Original Source Image space
        val boxes = nmsDetections.map { det ->
            // (x - padding) / scale
            val left = (det.rect.left - padX) / scaleFactor
            val top = (det.rect.top - padY) / scaleFactor
            val right = (det.rect.right - padX) / scaleFactor
            val bottom = (det.rect.bottom - padY) / scaleFactor

            val scaledRect = RectF(left, top, right, bottom)

            BoxOverlay.Box(scaledRect, Constants.LABELS.getOrElse(det.classIdx) { "Unknown" })
        }

        val bestScore = nmsDetections.maxOfOrNull { it.score } ?: 0f
        return Pair(boxes, bestScore)
    }

    private fun processYoloOutput(output: FloatArray, shape: IntArray): List<Detection> {
        val detections = mutableListOf<Detection>()

        val numBoxes: Int
        val numFeatures: Int
        val isTransposed: Boolean

        if (shape[1] > shape[2]) {
            // [1, 8400, 84]
            numBoxes = shape[1]
            numFeatures = shape[2]
            isTransposed = false
        } else {
            // [1, 84, 8400]
            numBoxes = shape[2]
            numFeatures = shape[1]
            isTransposed = true
        }
        val numClasses = numFeatures - 4

        for (i in 0 until numBoxes) {
            var maxScore = 0f
            var classIdx = -1

            if (isTransposed) {
                for (c in 0 until numClasses) {
                    val score = output[(c + 4) * numBoxes + i]
                    if (score > maxScore) { maxScore = score; classIdx = c }
                }
            } else {
                val base = i * numFeatures
                for (c in 0 until numClasses) {
                    val score = output[base + 4 + c]
                    if (score > maxScore) { maxScore = score; classIdx = c }
                }
            }

            if (maxScore > confidenceThreshold) {
                var cx: Float
                var cy: Float
                var w: Float
                var h: Float

                if (isTransposed) {
                    cx = output[0 * numBoxes + i]
                    cy = output[1 * numBoxes + i]
                    w = output[2 * numBoxes + i]
                    h = output[3 * numBoxes + i]
                } else {
                    val base = i * numFeatures
                    cx = output[base + 0]
                    cy = output[base + 1]
                    w = output[base + 2]
                    h = output[base + 3]
                }

                // --- CRITICAL FIX: CHECK FOR NORMALIZED COORDINATES ---
                // If coordinates are small (0.0 to 1.0), multiply by 640 to get pixels
                if (w <= 1.0f && h <= 1.0f && cx <= 1.0f && cy <= 1.0f) {
                    cx *= INPUT_SIZE
                    cy *= INPUT_SIZE
                    w *= INPUT_SIZE
                    h *= INPUT_SIZE
                }

                val x1 = cx - w / 2f
                val y1 = cy - h / 2f
                val x2 = cx + w / 2f
                val y2 = cy + h / 2f

                val rect = RectF(x1, y1, x2, y2)
                detections.add(Detection(rect, maxScore, classIdx))
            }
        }
        return detections
    }

    private fun nonMaxSuppression(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        val sortedDetections = detections.sortedByDescending { it.score }
        val selectedDetections = mutableListOf<Detection>()

        for (det in sortedDetections) {
            var shouldAdd = true
            for (selected in selectedDetections) {
                if (iou(det.rect, selected.rect) > iouThreshold) {
                    shouldAdd = false
                    break
                }
            }
            if (shouldAdd) {
                selectedDetections.add(det)
            }
        }
        return selectedDetections
    }

    private fun iou(a: RectF, b: RectF): Float {
        val intersectionLeft = max(a.left, b.left)
        val intersectionTop = max(a.top, b.top)
        val intersectionRight = min(a.right, b.right)
        val intersectionBottom = min(a.bottom, b.bottom)

        val intersectionWidth = max(0f, intersectionRight - intersectionLeft)
        val intersectionHeight = max(0f, intersectionBottom - intersectionTop)
        val intersectionArea = intersectionWidth * intersectionHeight

        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - a.top)
        val unionArea = areaA + areaB - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }
}