package com.god2.TAssistant
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.god2.TAssistant.core.*
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPrefsHelper
    private val commandUIs = mutableListOf<CommandUI>()
    private lateinit var customAppContainer: LinearLayout

    data class CommandUI(val key: String, val sw: Switch, val et: EditText)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = SharedPrefsHelper(this)
        
        findViewById<EditText>(R.id.etApiKey).setText(prefs.apiKey)
        findViewById<EditText>(R.id.etWakeWord).setText(prefs.wakeWord)

        val container = findViewById<LinearLayout>(R.id.categoryContainer)
        
        val systemCmds = listOf("home" to "go home", "back" to "go back", "recent" to "show recents", "lock" to "lock screen", "flashlight" to "flashlight", "battery" to "battery", "volume" to "volume", "brightness" to "brightness")
        val mediaCmds = listOf("play" to "play", "random" to "play random", "stop" to "stop music", "next" to "next song", "prev" to "previous song")
        val commCmds = listOf("call" to "call", "sms" to "send message", "open" to "open")
        val utilCmds = listOf("alarm" to "set alarm", "timer" to "set timer", "search" to "search", "map" to "navigate to", "camera" to "open camera")

        addCategory(container, "SYSTEM CONTROL", systemCmds)
        addCategory(container, "MEDIA", mediaCmds)
        addCategory(container, "COMMUNICATION", commCmds)
        addCategory(container, "UTILITIES", utilCmds)

        customAppContainer = findViewById(R.id.customAppContainer)
        val customApps = JSONObject(prefs.customAppConfig)
        customApps.keys().forEach { key -> addCustomAppRow(key, customApps.getString(key)) }

        findViewById<Button>(R.id.btnAddCustomApp).setOnClickListener { addCustomAppRow("", "") }
        findViewById<Button>(R.id.btnPermMic).setOnClickListener { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        findViewById<Button>(R.id.btnPermOverlay).setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        findViewById<Button>(R.id.btnPermAccessibility).setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        findViewById<Button>(R.id.btnPermWriteSettings).setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))) }
        findViewById<Button>(R.id.btnTestVoice).setOnClickListener { AssistantService.instance?.forceListen() }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            prefs.apiKey = findViewById<EditText>(R.id.etApiKey).text.toString()
            prefs.wakeWord = findViewById<EditText>(R.id.etWakeWord).text.toString()
            commandUIs.forEach { prefs.saveCmd(it.key, it.sw.isChecked, it.et.text.toString()) }

            val newCustomApps = JSONObject()
            for (i in 0 until customAppContainer.childCount) {
                val row = customAppContainer.getChildAt(i) as LinearLayout
                val kw = (row.getChildAt(0) as EditText).text.toString().trim().lowercase()
                val pkg = (row.getChildAt(1) as EditText).text.toString().trim()
                if (kw.isNotEmpty() && pkg.isNotEmpty()) newCustomApps.put(kw, pkg)
            }
            prefs.customAppConfig = newCustomApps.toString()
            Toast.makeText(this, "All Settings Saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addCustomAppRow(keyword: String, pkg: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, 10) }
        val etKw = EditText(this).apply { setText(keyword); hint = "Keyword"; setTextColor(Color.parseColor("#00E676")); setHintTextColor(Color.GRAY); layoutParams = LinearLayout.LayoutParams(0, -2, 1f); textSize = 14f }
        val etPkg = EditText(this).apply { setText(pkg); hint = "App/Package Name"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); layoutParams = LinearLayout.LayoutParams(0, -2, 1f); textSize = 14f }
        val btnDel = Button(this).apply { text = "X"; setTextColor(Color.RED); setBackgroundColor(Color.TRANSPARENT); layoutParams = LinearLayout.LayoutParams(-2, -2) }
        btnDel.setOnClickListener { customAppContainer.removeView(row) }
        row.addView(etKw); row.addView(etPkg); row.addView(btnDel)
        customAppContainer.addView(row)
    }

    private fun addCategory(parent: LinearLayout, title: String, items: List<Pair<String, String>>) {
        val catBtn = Button(this).apply { text = "+ $title"; setTextColor(Color.parseColor("#00E676")); setBackgroundColor(Color.TRANSPARENT); gravity = android.view.Gravity.START; setAllCaps(true) }
        val itemBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(20,0,0,0) }
        catBtn.setOnClickListener { 
            val visible = itemBox.visibility == View.VISIBLE
            itemBox.visibility = if(visible) View.GONE else View.VISIBLE
            catBtn.text = (if(visible) "+ " else "- ") + title
        }
        items.forEach { (key, default) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,10,0,20) }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val tv = TextView(this).apply { text = key.uppercase(); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0,-2,1f) }
            val sw = Switch(this).apply { isChecked = prefs.isEnabled(key) }
            val et = EditText(this).apply { setText(prefs.getKeyword(key, default)); setTextColor(Color.parseColor("#00E676")); setBackgroundColor(Color.TRANSPARENT); textSize = 14f }
            top.addView(tv); top.addView(sw); row.addView(top); row.addView(et)
            commandUIs.add(CommandUI(key, sw, et)); itemBox.addView(row)
        }
        parent.addView(catBtn); parent.addView(itemBox)
    }

    override fun onResume() { super.onResume(); updateStatus() }
    private fun updateStatus() {
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val overlay = Settings.canDrawOverlays(this)
        val acc = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(packageName) == true
        val write = Settings.System.canWrite(this)
        updateBtn(findViewById(R.id.btnPermMic), mic, "MIC")
        updateBtn(findViewById(R.id.btnPermOverlay), overlay, "OVERLAY")
        updateBtn(findViewById(R.id.btnPermAccessibility), acc, "ACCESSIBILITY")
        updateBtn(findViewById(R.id.btnPermWriteSettings), write, "SYSTEM WRITE")
    }
    private fun updateBtn(b: Button, ok: Boolean, txt: String) {
        b.text = "$txt: ${if(ok) "OK" else "PENDING"}"
        b.setBackgroundColor(if(ok) Color.parseColor("#3300E676") else Color.parseColor("#222222"))
    }
}