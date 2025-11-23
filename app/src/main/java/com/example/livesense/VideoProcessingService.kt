package com.example.livesense

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VideoProcessingService : Service() {

    private val binder = LocalBinder()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    inner class LocalBinder : Binder() {
        fun getService(): VideoProcessingService = this@VideoProcessingService
    }

    override fun onBind(intent: Intent): IBinder = binder

    fun processVideo(videoUri: Uri, callback: (File?) -> Unit) {
        scope.launch {
            val result = VideoProcessor.processVideo(applicationContext, videoUri)
            withContext(Dispatchers.Main) {
                callback(result)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}