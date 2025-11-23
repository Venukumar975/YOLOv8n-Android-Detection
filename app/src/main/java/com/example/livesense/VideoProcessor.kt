package com.example.livesense

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoProcessor {

    private const val TIMEOUT_US = 10000L
    private const val TARGET_WIDTH = 640
    private const val TARGET_HEIGHT = 480
    private const val YOLO_INPUT_SIZE = 640f // YOLO output reference size

    suspend fun processVideo(context: Context, videoUri: Uri): File? = withContext(Dispatchers.IO) {
        val yolo = YoloDetector(context).apply { confidenceThreshold = 0.5f }
        val outputFile = File.createTempFile("livesense_output", ".mp4", context.cacheDir)

        var retriever: MediaMetadataRetriever? = null
        var muxer: MediaMuxer? = null
        var codec: MediaCodec? = null
        var fileDescriptor: android.content.res.AssetFileDescriptor? = null
        var surface: Surface? = null // Declare Surface here

        try {
            retriever = MediaMetadataRetriever()
            try {
                fileDescriptor = context.contentResolver.openAssetFileDescriptor(videoUri, "r")
                if (fileDescriptor != null) {
                    retriever.setDataSource(fileDescriptor.fileDescriptor)
                } else {
                    retriever.setDataSource(context, videoUri)
                }
            } catch (e: Exception) {
                retriever.setDataSource(context, videoUri)
            }

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

            Log.d("VideoProcessor", "Starting processing: ${TARGET_WIDTH}x${TARGET_HEIGHT} for ${durationMs}ms")

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, TARGET_WIDTH, TARGET_HEIGHT).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 25)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            surface = codec.createInputSurface()
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var trackIndex = -1
            var isMuxerStarted = false
            val frameIntervalUs = 1_000_000L / 25

            // Paint Setup
            val boxPaint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 3f }
            val textPaint = Paint().apply { color = Color.WHITE; textSize = 24f }
            val bgPaint = Paint().apply { color = Color.BLACK; alpha = 150; style = Paint.Style.FILL }

            // Processing Loop
            for (timeUs in 0 until durationMs * 1000 step frameIntervalUs) {
                val originalBitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                val rotated = rotateBitmap(originalBitmap, rotation.toFloat())
                val scaled = Bitmap.createScaledBitmap(rotated, TARGET_WIDTH, TARGET_HEIGHT, true)
                val processed = processFrame(scaled, yolo, boxPaint, textPaint, bgPaint)

                // 5. Render to Encoder Surface (STABLE METHOD)
                // Use a canvas, but drawing is handled internally by the Surface class
                try {
                    val canvas = surface!!.lockCanvas(null)
                    canvas.drawBitmap(processed, 0f, 0f, null)
                    surface!!.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    // This catch block handles the known instability of lockCanvas
                    Log.e("VideoProcessor", "Surface lock failed (Skipping frame): ", e)
                    continue
                }


                // 6. Drain Encoder Output
                // ... (Output logic remains unchanged) ...
                while (true) {
                    val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        trackIndex = muxer!!.addTrack(codec!!.outputFormat)
                        muxer!!.start()
                        isMuxerStarted = true
                    } else if (outputBufferIndex >= 0) {
                        if (isMuxerStarted && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            val encodedData = codec!!.getOutputBuffer(outputBufferIndex)
                            if (encodedData != null && bufferInfo.size > 0) {
                                muxer!!.writeSampleData(trackIndex, encodedData, bufferInfo)
                            }
                        }
                        codec!!.releaseOutputBuffer(outputBufferIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }

                // Clean up bitmaps to save RAM
                if (originalBitmap != scaled) originalBitmap.recycle()
                if (rotated != scaled && rotated != originalBitmap) rotated.recycle()
            }

            // End of Stream
            codec!!.signalEndOfInputStream()
            // Final drain
            while (true) {
                val outputBufferIndex = codec!!.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                if (outputBufferIndex >= 0) {
                    codec!!.releaseOutputBuffer(outputBufferIndex, false)
                }
            }

            Log.d("VideoProcessor", "Video Processing Complete")
            outputFile

        } catch (e: Exception) {
            Log.e("VideoProcessor", "Fatal Error", e)
            outputFile.delete()
            null
        } finally {
            try { fileDescriptor?.close() } catch (e: Exception) {}
            try { retriever?.release() } catch (e: Exception) {}
            try { codec?.stop(); codec?.release() } catch (e: Exception) {}
            try { muxer?.stop(); muxer?.release() } catch (e: Exception) {}
            try { surface?.release() } catch (e: Exception) {}
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, rotation: Float): Bitmap {
        if (rotation == 0f) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    // Isolate logic here to interpret YOLO's non-normalized output
    private fun processFrame(bitmap: Bitmap, yolo: YoloDetector, boxP: Paint, textP: Paint, bgP: Paint): Bitmap {
        val mutable = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)

        val (boxes, _) = yolo.detect(mutable)

        // Scaling factor is 1 because the bitmap is already 640x480, and YOLO output is relative to 640.
        val scaleX = mutable.width / YOLO_INPUT_SIZE
        val scaleY = mutable.height / YOLO_INPUT_SIZE

        boxes.forEach { box ->
            // Use YOLO's absolute coordinates (0-640) and scale them to the 640x480 frame
            val l = box.rect.left * scaleX
            val t = box.rect.top * scaleY
            val r = box.rect.right * scaleX
            val b = box.rect.bottom * scaleY

            canvas.drawRect(RectF(l, t, r, b), boxP)

            val tag = box.label
            val textBounds = android.graphics.Rect()
            textP.getTextBounds(tag, 0, tag.length, textBounds)
            canvas.drawRect(RectF(l, t - textBounds.height() - 10, l + textP.measureText(tag) + 20, t), bgP)
            canvas.drawText(tag, l + 10, t - 5, textP)
        }
        return mutable
    }
}