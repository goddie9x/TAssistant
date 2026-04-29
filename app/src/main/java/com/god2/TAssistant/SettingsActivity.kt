package com.god2.TAssistant
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.god2.TAssistant.core.SharedPrefsHelper

class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPrefsHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = SharedPrefsHelper(this)

        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val etModel = findViewById<EditText>(R.id.etModel)
        val etWakeWord = findViewById<EditText>(R.id.etWakeWord)
        val swOpenApp = findViewById<Switch>(R.id.swOpenApp)
        val swSetAlarm = findViewById<Switch>(R.id.swSetAlarm)
        val swCalendar = findViewById<Switch>(R.id.swCalendar)
        val btnSave = findViewById<Button>(R.id.btnSave)

        etApiKey.setText(prefs.apiKey)
        etModel.setText(prefs.aiModel)
        etWakeWord.setText(prefs.wakeWord)
        swOpenApp.isChecked = prefs.allowOpenApp
        swSetAlarm.isChecked = prefs.allowSetAlarm
        swCalendar.isChecked = prefs.allowCalendar

        btnSave.setOnClickListener {
            prefs.apiKey = etApiKey.text.toString()
            prefs.aiModel = etModel.text.toString()
            prefs.wakeWord = etWakeWord.text.toString()
            prefs.allowOpenApp = swOpenApp.isChecked
            prefs.allowSetAlarm = swSetAlarm.isChecked
            prefs.allowCalendar = swCalendar.isChecked
            Toast.makeText(this, "Đã lưu cài đặt", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
