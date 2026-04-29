package com.god2.TAssistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.god2.TAssistant.core.SharedPrefsHelper
import com.god2.TAssistant.core.VoiceManager

class MainActivity : AppCompatActivity() {
    private var voiceManager: VoiceManager? = null
    private lateinit var prefs: SharedPrefsHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = SharedPrefsHelper(this)
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButtons()
    }

    private fun setupUI() {
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val etModel = findViewById<EditText>(R.id.etModel)
        val etWakeWord = findViewById<EditText>(R.id.etWakeWord)
        val swOpenApp = findViewById<Switch>(R.id.swOpenApp)
        val swSetAlarm = findViewById<Switch>(R.id.swSetAlarm)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnTestVoice = findViewById<Button>(R.id.btnTestVoice)
        val tvVoiceResult = findViewById<TextView>(R.id.tvVoiceResult)

        etApiKey.setText(prefs.apiKey)
        etModel.setText(prefs.aiModel)
        etWakeWord.setText(prefs.wakeWord)
        swOpenApp.isChecked = prefs.allowOpenApp
        swSetAlarm.isChecked = prefs.allowSetAlarm

        findViewById<Button>(R.id.btnPermMic).setOnClickListener {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        findViewById<Button>(R.id.btnPermOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btnPermAccessibility).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(this, "Vào App Info -> Allow restricted settings nếu không thấy app!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {}
        }

        btnTestVoice.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                tvVoiceResult.text = "Đang nghe... Nói thử đi bro!"
                tvVoiceResult.setTextColor(Color.parseColor("#FF9800"))
                if (voiceManager == null) voiceManager = VoiceManager(this)
            } else {
                Toast.makeText(this, "Chưa cấp quyền Micro", Toast.LENGTH_SHORT).show()
            }
        }

        btnSave.setOnClickListener {
            prefs.apiKey = etApiKey.text.toString()
            prefs.aiModel = etModel.text.toString()
            prefs.wakeWord = etWakeWord.text.toString()
            prefs.allowOpenApp = swOpenApp.isChecked
            prefs.allowSetAlarm = swSetAlarm.isChecked
            
            if (checkAllPermissions()) {
                if (voiceManager == null) voiceManager = VoiceManager(this)
                Toast.makeText(this, "Đã lưu! Trợ lý đang hoạt động", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Vui lòng cấp đủ 3 quyền", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkAllPermissions(): Boolean {
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val overlay = Settings.canDrawOverlays(this)
        val accessibility = isAccessibilityServiceEnabled()
        return mic && overlay && accessibility
    }

    private fun updatePermissionButtons() {
        val btnMic = findViewById<Button>(R.id.btnPermMic)
        val btnOverlay = findViewById<Button>(R.id.btnPermOverlay)
        val btnAcc = findViewById<Button>(R.id.btnPermAccessibility)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            btnMic.text = "Micro: OK"
            btnMic.isEnabled = false
            btnMic.setBackgroundColor(Color.parseColor("#4CAF50"))
        }
        if (Settings.canDrawOverlays(this)) {
            btnOverlay.text = "Vẽ đè: OK"
            btnOverlay.isEnabled = false
            btnOverlay.setBackgroundColor(Color.parseColor("#4CAF50"))
        }
        if (isAccessibilityServiceEnabled()) {
            btnAcc.text = "Trợ năng: OK"
            btnAcc.isEnabled = false
            btnAcc.setBackgroundColor(Color.parseColor("#4CAF50"))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val prefString = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return prefString?.contains(packageName) == true
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager?.destroy()
    }
}