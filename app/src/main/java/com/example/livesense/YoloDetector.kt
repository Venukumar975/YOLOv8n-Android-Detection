package com.example.livesense

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import kotlin.math.max
import kotlin.math.min

class YoloDetector(context: Context) {

    private var interpreter: Interpreter? = null
    private val INPUT_SIZE = 640
    var confidenceThreshold: Float = 0.3f // Default threshold
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

    fun detect(bitmap: Bitmap): Pair<List<BoxOverlay.Box>, Float> {
        if (interpreter == null) return Pair(emptyList(), 0f)

        val processor = ImageProcessor.Builder()
            .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .add(CastOp(DataType.FLOAT32))
            .build()

        inputImageBuffer.load(bitmap)
        val processedImage = processor.process(inputImageBuffer)

        interpreter!!.run(processedImage.buffer, outputBuffer!!.buffer.rewind())

        val outArr = outputBuffer!!.floatArray
        val outShape = interpreter!!.getOutputTensor(0).shape()

        val detections = processYoloOutput(outArr, outShape)

        val nmsDetections = nonMaxSuppression(detections, NMS_THRESHOLD)

        val boxes = nmsDetections.map {
            BoxOverlay.Box(it.rect, Constants.LABELS.getOrElse(it.classIdx) { "Unknown" })
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
            numBoxes = shape[1]
            numFeatures = shape[2]
            isTransposed = false
        } else {
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
                val cx: Float
                val cy: Float
                val w: Float
                val h: Float

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