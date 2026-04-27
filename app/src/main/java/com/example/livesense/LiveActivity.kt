package com.example.livesense

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.livesense.databinding.ActivityLiveBinding
import java.util.concurrent.Executors

class LiveActivity : ComponentActivity() {

    private lateinit var binding: ActivityLiveBinding
    private lateinit var yolo: YoloDetector
    private var isAnalyzing = false
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // FIT_CENTER ensures we see the full image without cropping
        binding.viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        }

        yolo = YoloDetector(this)
        // Set confidence to 0.5 to filter out bad "Person" detections on bottles
        yolo.confidenceThreshold = 0.5f
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)

            // Request HD resolution (1280x720) for clearer details
            val analyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analyzer.setAnalyzer(executor) { imageProxy ->
                if (isAnalyzing) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                isAnalyzing = true

                try {
                    var bitmap = imageProxy.toBitmap()

                    if (bitmap != null) {
                        // --- CRITICAL ROTATION FIX ---
                        // If the phone gives us a "Landscape" image (Width > Height) but we are in Portrait,
                        // we MUST rotate it now. This syncs the AI and the Overlay.
                        if (bitmap.width > bitmap.height) {
                            val matrix = Matrix()
                            matrix.postRotate(90f) // Rotate to Portrait
                            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        }
                        // -----------------------------

                        val startTime = System.currentTimeMillis()
                        // Now we pass the UPRIGHT bitmap to the detector
                        val (boxes, _) = yolo.detect(bitmap)
                        val inferenceTime = System.currentTimeMillis() - startTime

                        // And pass the SAME UPRIGHT dimensions to the overlay
                        val w = bitmap.width
                        val h = bitmap.height

                        runOnUiThread {
                            binding.objectCounter.text = "Obj: ${boxes.size}"
                            binding.inferenceTime.text = "${inferenceTime}ms"

                            // fitCenter = true aligns the boxes with the image
                            binding.boxOverlay.setBoxes(boxes, w, h, fitCenter = true)
                            isAnalyzing = false
                        }
                    } else {
                        isAnalyzing = false
                    }
                } catch (e: Exception) {
                    Log.e("LIVE", "Error", e)
                    isAnalyzing = false
                } finally {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
            } catch (e: Exception) {
                Log.e("LIVE", "Bind failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && !allPermissionsGranted()) {
            finish()
        } else {
            startCamera()
        }
    }
}