package com.example.livesense

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.livesense.databinding.ActivityImageBinding

class ImageActivity : ComponentActivity() {

    private lateinit var binding: ActivityImageBinding
    private lateinit var yolo: YoloDetector

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { 
            val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            processImage(bitmap)
        }
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { 
            processImage(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        yolo = YoloDetector(this)

        binding.uploadButton.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.snapButton.setOnClickListener {
            if (allPermissionsGranted()) {
                takePicture.launch(null)
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    102
                )
            }
        }

        startScannerAnimation()
    }

    private fun startScannerAnimation() {
        binding.scannerLine.visibility = View.VISIBLE
        val animator = ObjectAnimator.ofFloat(binding.scannerLine, "translationY", 0f, binding.placeholderAnimation.height.toFloat())
        animator.duration = 2000
        animator.repeatCount = ObjectAnimator.INFINITE
        animator.repeatMode = ObjectAnimator.REVERSE
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.start()
    }

    private fun processImage(bitmap: Bitmap) {
        binding.placeholderAnimation.visibility = View.GONE
        binding.imageView.setImageBitmap(bitmap)
        yolo.confidenceThreshold = 0.6f
        val (boxes, _) = yolo.detect(bitmap)
        binding.boxOverlay.setBoxes(boxes)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102) {
            if (allPermissionsGranted()) {
                takePicture.launch(null)
            } else {
                Toast.makeText(this, "Camera permission is required to take a picture.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}