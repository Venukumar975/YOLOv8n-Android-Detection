package com.example.livesense

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.livesense.databinding.ActivityLiveBinding
import java.util.concurrent.Executors

class LiveActivity : ComponentActivity() {

    private lateinit var binding: ActivityLiveBinding
    private lateinit var yolo: YoloDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        }

        yolo = YoloDetector(this)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // FIX: Use Ratio 16:9 to prevent "Big Box" stretching on tall phones
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build()

            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)

            val analyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analyzer.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                val startTime = System.currentTimeMillis()
                try {
                    val bitmap = imageProxy.toBitmap()
                    if (bitmap != null) {
                        val (boxes, _) = yolo.detect(bitmap)
                        val inferenceTime = System.currentTimeMillis() - startTime
                        val w = bitmap.width
                        val h = bitmap.height

                        runOnUiThread {
                            binding.objectCounter.text = "Objects: ${boxes.size}"
                            binding.inferenceTime.text = "Time: ${inferenceTime}ms"
                            // Live Mode: Zoom to fill (fitCenter = false)
                            binding.boxOverlay.setBoxes(boxes, w, h, fitCenter = false)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LIVE", "Analyzer error", e)
                } finally {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
            } catch (e: Exception) {
                Log.e("LIVE", "Bind error", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && !allPermissionsGranted()) {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            startCamera()
        }
    }
}