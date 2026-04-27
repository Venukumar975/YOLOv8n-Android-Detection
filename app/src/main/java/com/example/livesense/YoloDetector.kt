package com.example.livesense

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
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
            options.setNumThreads(4)
            interpreter = Interpreter(modelFile, options)

            val outShape = interpreter!!.getOutputTensor(0).shape()
            outputBuffer = TensorBuffer.createFixedSize(outShape, DataType.FLOAT32)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class Detection(val rect: RectF, val score: Float, val classIdx: Int)

    fun detect(sourceBitmap: Bitmap): Pair<List<BoxOverlay.Box>, Float> {
        if (interpreter == null) return Pair(emptyList(), 0f)

        val srcW = sourceBitmap.width.toFloat()
        val srcH = sourceBitmap.height.toFloat()

        // 1. LETTERBOX (Standard)
        val scale = min(INPUT_SIZE / srcW, INPUT_SIZE / srcH)
        val newW = srcW * scale
        val newH = srcH * scale
        val padX = (INPUT_SIZE - newW) / 2f
        val padY = (INPUT_SIZE - newH) / 2f

        val inputBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(inputBitmap)
        canvas.drawColor(Color.BLACK)

        val matrix = Matrix()
        matrix.postScale(scale, scale)
        matrix.postTranslate(padX, padY)
        canvas.drawBitmap(sourceBitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))

        // 2. INFERENCE
        val processor = ImageProcessor.Builder()
            .add(NormalizeOp(0f, 255f))
            .add(CastOp(DataType.FLOAT32))
            .build()

        inputImageBuffer.load(inputBitmap)
        val processedImage = processor.process(inputImageBuffer)

        interpreter!!.run(processedImage.buffer, outputBuffer!!.buffer.rewind())

        val outArr = outputBuffer!!.floatArray
        val outShape = interpreter!!.getOutputTensor(0).shape()
        val detections = processYoloOutput(outArr, outShape)
        val nmsDetections = nonMaxSuppression(detections, NMS_THRESHOLD)

        // 3. REVERSE LETTERBOX
        val boxes = nmsDetections.map { det ->
            var left = (det.rect.left - padX) / scale
            var top = (det.rect.top - padY) / scale
            var right = (det.rect.right - padX) / scale
            var bottom = (det.rect.bottom - padY) / scale

            left = max(0f, left)
            top = max(0f, top)
            right = min(srcW, right)
            bottom = min(srcH, bottom)

            BoxOverlay.Box(RectF(left, top, right, bottom), Constants.LABELS.getOrElse(det.classIdx) { "Unknown" })
        }

        val bestScore = nmsDetections.maxOfOrNull { it.score } ?: 0f
        return Pair(boxes, bestScore)
    }

    private fun processYoloOutput(output: FloatArray, shape: IntArray): List<Detection> {
        val detections = mutableListOf<Detection>()

        val isTransposed = shape[2] > shape[1]
        val numBoxes = if (isTransposed) shape[2] else shape[1]
        val numFeatures = if (isTransposed) shape[1] else shape[2]
        val numClasses = numFeatures - 4

        for (i in 0 until numBoxes) {
            var maxScore = 0f
            var classIdx = -1

            for (c in 0 until numClasses) {
                val score = if (isTransposed) output[(c + 4) * numBoxes + i] else output[i * numFeatures + 4 + c]
                if (score > maxScore) { maxScore = score; classIdx = c }
            }

            if (maxScore > confidenceThreshold) {
                var cx: Float; var cy: Float; var w: Float; var h: Float

                if (isTransposed) {
                    cx = output[0 * numBoxes + i]; cy = output[1 * numBoxes + i]
                    w = output[2 * numBoxes + i]; h = output[3 * numBoxes + i]
                } else {
                    val base = i * numFeatures
                    cx = output[base]; cy = output[base+1]; w = output[base+2]; h = output[base+3]
                }

                if (w < 1.0f || h < 1.0f || cx < 1.0f) {
                    cx *= INPUT_SIZE; cy *= INPUT_SIZE; w *= INPUT_SIZE; h *= INPUT_SIZE
                }

                val left = cx - w / 2f
                val top = cy - h / 2f
                val right = cx + w / 2f
                val bottom = cy + h / 2f

                detections.add(Detection(RectF(left, top, right, bottom), maxScore, classIdx))
            }
        }
        return detections
    }

    private fun nonMaxSuppression(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        val sorted = detections.sortedByDescending { it.score }
        val selected = mutableListOf<Detection>()
        for (det in sorted) {
            var overlapped = false
            for (sel in selected) {
                if (iou(det.rect, sel.rect) > iouThreshold) { overlapped = true; break }
            }
            if (!overlapped) selected.add(det)
        }
        return selected
    }

    private fun iou(a: RectF, b: RectF): Float {
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        if (areaA <= 0 || areaB <= 0) return 0f

        val intersectionLeft = max(a.left, b.left)
        val intersectionTop = max(a.top, b.top)
        val intersectionRight = min(a.right, b.right)
        val intersectionBottom = min(a.bottom, b.bottom)

        val intersectionArea = max(0f, intersectionRight - intersectionLeft) * max(0f, intersectionBottom - intersectionTop)
        return intersectionArea / (areaA + areaB - intersectionArea)
    }
}