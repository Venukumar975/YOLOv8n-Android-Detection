package com.example.livesense

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Link Live Button
        findViewById<Button>(R.id.btnLive).setOnClickListener {
            startActivity(Intent(this, LiveActivity::class.java))
        }

        // 2. Link Image Button
        findViewById<Button>(R.id.btnImage).setOnClickListener {
            startActivity(Intent(this, ImageActivity::class.java))
        }

        // 3. Link Video Button
        findViewById<Button>(R.id.btnVideo).setOnClickListener {
            startActivity(Intent(this, VideoActivity::class.java))
        }
    }
}