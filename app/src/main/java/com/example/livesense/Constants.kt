package com.example.livesense

import android.content.Context

object Constants {
    // 1. Initialize an empty list (It will be filled when the app starts)
    var LABELS = emptyList<String>()

    // 2. Function to load the correct language labels
    fun updateLabels(context: Context) {
        val resources = context.resources
        // This reads the <string-array name="yolo_labels"> from the active language file
        LABELS = resources.getStringArray(R.array.yolo_labels).toList()
    }
}