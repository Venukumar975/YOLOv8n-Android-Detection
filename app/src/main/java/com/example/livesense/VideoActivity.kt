package com.example.livesense

import android.Manifest
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.livesense.databinding.ActivityVideoBinding
import java.io.File

class VideoActivity : ComponentActivity() {

    private lateinit var binding: ActivityVideoBinding
    private var videoProcessingService: VideoProcessingService? = null
    private var isBound = false
    private var videoUriForRecording: Uri? = null

    // 1. Permission Launchers
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startRecording()
        } else {
            Toast.makeText(this, "Camera permission needed to record", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if all requested permissions are granted
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            pickVideo.launch("video/*")
        } else {
            Toast.makeText(this, "Storage permission needed to upload", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. Activity Results
    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handleVideoUri(uri)
    }

    private val recordVideo = registerForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success && videoUriForRecording != null) {
            handleVideoUri(videoUriForRecording!!)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as VideoProcessingService.LocalBinder
            videoProcessingService = binder.getService()
            isBound = true
            binding.uploadButton.isEnabled = true
            binding.recordButton.isEnabled = true
            binding.uploadButton.text = "Upload"
            binding.recordButton.text = "Record"
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            binding.uploadButton.isEnabled = false
            binding.recordButton.isEnabled = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.uploadButton.isEnabled = false
        binding.recordButton.isEnabled = false
        binding.uploadButton.text = "Initializing..."
        binding.recordButton.text = "Initializing..."

        // 3. Button Listeners with Permission Checks
        binding.uploadButton.setOnClickListener {
            checkStorageAndPickVideo()
        }

        binding.recordButton.setOnClickListener {
            checkCameraAndRecord()
        }

        Intent(this, VideoProcessingService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun checkCameraAndRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkStorageAndPickVideo() {
        // Determine which permission to ask for based on Android version
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            pickVideo.launch("video/*")
        } else {
            requestStoragePermissionLauncher.launch(permissions)
        }
    }

    private fun startRecording() {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "LiveSense_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/LiveSense")
            }
        }
        val newVideoUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

        if (newVideoUri != null) {
            videoUriForRecording = newVideoUri
            recordVideo.launch(videoUriForRecording)
        } else {
            Toast.makeText(this, "Failed to create video file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleVideoUri(uri: Uri) {
        binding.placeholderLayout.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
        binding.uploadButton.isEnabled = false
        binding.recordButton.isEnabled = false

        Toast.makeText(this, "Processing video...", Toast.LENGTH_SHORT).show()

        videoProcessingService?.processVideo(uri) { outputFile ->
            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                binding.uploadButton.isEnabled = true
                binding.recordButton.isEnabled = true

                if (outputFile != null && outputFile.exists()) {
                    binding.videoView.setVideoURI(Uri.fromFile(outputFile))
                    binding.videoView.start()
                } else {
                    Toast.makeText(this, "Error processing video. Try a different file.", Toast.LENGTH_LONG).show()
                    binding.placeholderLayout.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}