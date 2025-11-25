package com.example.livesense

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class LanguageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_language)

        // Setup Buttons
        findViewById<Button>(R.id.btnEn).setOnClickListener { setLang("en") }
        findViewById<Button>(R.id.btnHi).setOnClickListener { setLang("hi") }
        findViewById<Button>(R.id.btnTa).setOnClickListener { setLang("ta") }
        findViewById<Button>(R.id.btnTe).setOnClickListener { setLang("te") }
        findViewById<Button>(R.id.btnMr).setOnClickListener { setLang("mr") }
        findViewById<Button>(R.id.btnBn).setOnClickListener { setLang("bn") }
    }

    private fun setLang(code: String) {
        // 1. Set the language
        LanguageManager.setLocale(this, code)

        // 2. Go to Main Dashboard
        val intent = Intent(this, MainActivity::class.java)
        // Clear back stack so pressing back doesn't return to language selection
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}