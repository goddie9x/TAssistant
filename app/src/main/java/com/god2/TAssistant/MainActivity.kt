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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.god2.TAssistant.core.AssistantService
import com.god2.TAssistant.core.SharedPrefsHelper

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPrefsHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = SharedPrefsHelper(this)
        setupUI()
    }

    private fun setupUI() {
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val etModel = findViewById<EditText>(R.id.etModel)
        val etWakeWord = findViewById<EditText>(R.id.etWakeWord)
        val swOpenApp = findViewById<Switch>(R.id.swOpenApp)
        val swSetAlarm = findViewById<Switch>(R.id.swSetAlarm)
        val swMusic = findViewById<Switch>(R.id.swMusic)
        val swVolume = findViewById<Switch>(R.id.swVolume)
        val swSearch = findViewById<Switch>(R.id.swSearch)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnTestVoice = findViewById<Button>(R.id.btnTestVoice)

        etApiKey.setText(prefs.apiKey)
        etModel.setText(prefs.aiModel)
        etWakeWord.setText(prefs.wakeWord)
        swOpenApp.isChecked = prefs.allowOpenApp
        swSetAlarm.isChecked = prefs.allowSetAlarm
        swMusic.isChecked = prefs.allowMusic
        swVolume.isChecked = prefs.allowVolume
        swSearch.isChecked = prefs.allowSearch

        findViewById<Button>(R.id.btnPermMic).setOnClickListener { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        findViewById<Button>(R.id.btnPermOverlay).setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        findViewById<Button>(R.id.btnPermAccessibility).setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        findViewById<Button>(R.id.btnPermWriteSettings).setOnClickListener { 
            val i = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
            startActivity(i)
        }

        btnTestVoice.setOnClickListener { AssistantService.instance?.forceListen() }

        btnSave.setOnClickListener {
            prefs.apiKey = etApiKey.text.toString()
            prefs.aiModel = etModel.text.toString()
            prefs.wakeWord = etWakeWord.text.toString()
            prefs.allowOpenApp = swOpenApp.isChecked
            prefs.allowSetAlarm = swSetAlarm.isChecked
            prefs.allowMusic = swMusic.isChecked
            prefs.allowVolume = swVolume.isChecked
            prefs.allowSearch = swSearch.isChecked
            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateButtons()
    }

    private fun updateButtons() {
        val btnMic = findViewById<Button>(R.id.btnPermMic)
        val btnOverlay = findViewById<Button>(R.id.btnPermOverlay)
        val btnAcc = findViewById<Button>(R.id.btnPermAccessibility)
        val btnWrite = findViewById<Button>(R.id.btnPermWriteSettings)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            btnMic.text = "Mic: OK"; btnMic.isEnabled = false; btnMic.setBackgroundColor(Color.parseColor("#4CAF50"))
        }
        if (Settings.canDrawOverlays(this)) {
            btnOverlay.text = "Overlay: OK"; btnOverlay.isEnabled = false; btnOverlay.setBackgroundColor(Color.parseColor("#4CAF50"))
        }
        val prefString = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (prefString?.contains(packageName) == true) {
            btnAcc.text = "Accessibility: OK"; btnAcc.isEnabled = false; btnAcc.setBackgroundColor(Color.parseColor("#4CAF50"))
        }
        if (Settings.System.canWrite(this)) {
            btnWrite.text = "Write Settings: OK"; btnWrite.isEnabled = false; btnWrite.setBackgroundColor(Color.parseColor("#4CAF50"))
        }
    }
}