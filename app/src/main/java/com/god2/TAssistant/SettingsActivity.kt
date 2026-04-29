package com.god2.TAssistant
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.god2.TAssistant.core.SharedPrefsHelper

class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPrefsHelper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SharedPrefsHelper(this)
        Toast.makeText(this, "Use Main Screen for configuration", Toast.LENGTH_LONG).show()
        finish() 
    }
}