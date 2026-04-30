package com.god2.TAssistant
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.ViewGroup
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
    private val installedApps = mutableListOf<AppInfo>()
    private var tts: TextToSpeech? = null

    data class CommandUI(val key: String, val sw: Switch, val et: EditText)
    data class AppInfo(val name: String, val pkg: String) { override fun toString() = name }
    data class VoiceOption(val id: String, val label: String) { override fun toString() = label }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = SharedPrefsHelper(this)

        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        installedApps.addAll(resolveInfos.map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }.distinctBy { it.pkg }.sortedBy { it.name })

        findViewById<EditText>(R.id.etApiKey).setText(prefs.apiKey)
        findViewById<EditText>(R.id.etWakeWord).setText(prefs.wakeWord)

        val container = findViewById<LinearLayout>(R.id.categoryContainer)
        val sp = getSharedPreferences("TAssistantVoicePrefs", Context.MODE_PRIVATE)
        
        val voiceContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, 20) }
        val tvVoice = TextView(this).apply { text = "VOICE: "; setTextColor(Color.parseColor("#00E676")); layoutParams = LinearLayout.LayoutParams(0, -2, 1f); textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD) }
        val spinnerVoice = Spinner(this).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 2.5f) }
        voiceContainer.addView(tvVoice)
        voiceContainer.addView(spinnerVoice)
        container.addView(voiceContainer, 0) 

        val speedContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, 40); gravity = android.view.Gravity.CENTER_VERTICAL }
        val tvSpeed = TextView(this).apply { setTextColor(Color.parseColor("#00E676")); layoutParams = LinearLayout.LayoutParams(0, -2, 1f); textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD) }
        val sbSpeed = SeekBar(this).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 2.5f); max = 20; progress = (sp.getFloat("tts_speed", 1.0f) * 10).toInt() }
        tvSpeed.text = "SPEED: ${sbSpeed.progress / 10f}x"

        sbSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val p = if (progress < 1) 1 else progress
                val rate = p / 10f
                tvSpeed.text = "SPEED: ${rate}x"
                sp.edit().putFloat("tts_speed", rate).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        speedContainer.addView(tvSpeed)
        speedContainer.addView(sbSpeed)
        container.addView(speedContainer, 1)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val voices = tts?.voices?.toList() ?: emptyList()
                val voiceOptions = voices.filter { 
                    it.locale.language == "en" && !it.isNetworkConnectionRequired 
                }.sortedBy { it.locale.displayName }.map {
                    val localeName = it.locale.displayName
                    val suffix = it.name.substringAfterLast("-").uppercase()
                    VoiceOption(it.name, "$localeName [$suffix]")
                }
                
                runOnUiThread {
                    val adapter = object : ArrayAdapter<VoiceOption>(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, voiceOptions) {
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                            return (super.getView(position, convertView, parent) as TextView).apply { setTextColor(Color.WHITE) }
                        }
                        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                            return (super.getDropDownView(position, convertView, parent) as TextView).apply { 
                                setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#333333")) 
                            }
                        }
                    }
                    spinnerVoice.adapter = adapter
                    
                    val savedVoiceId = sp.getString("tts_voice", "")
                    val idx = voiceOptions.indexOfFirst { it.id == savedVoiceId }
                    if (idx >= 0) spinnerVoice.setSelection(idx)
                    
                    spinnerVoice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            sp.edit().putString("tts_voice", voiceOptions[position].id).apply()
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                }
            }
        }

        val systemCmds = listOf("home" to "go home", "back" to "go back", "recent" to "show recents", "lock" to "lock screen", "flashlight_on" to "turn on flashlight", "flashlight_off" to "turn off flashlight", "battery" to "battery", "volume" to "volume", "brightness" to "brightness", "wifi_on" to "turn on wifi", "wifi_off" to "turn off wifi", "bluetooth_on" to "turn on bluetooth", "bluetooth_off" to "turn off bluetooth", "data_on" to "turn on data", "data_off" to "turn off data")
        val mediaCmds = listOf("play" to "play", "random" to "play random", "stop" to "stop music", "next" to "next song", "prev" to "previous song")
        val commCmds = listOf("call" to "call", "sms" to "send message", "open" to "open app")
        val utilCmds = listOf("alarm" to "set alarm", "cancel_alarm" to "cancel alarm", "timer" to "set timer", "cancel_timer" to "cancel timer", "search" to "search", "map" to "navigate to", "camera" to "open camera")

        addCategory(container, "SYSTEM CONTROL", systemCmds)
        addCategory(container, "MEDIA", mediaCmds)
        addCategory(container, "COMMUNICATION", commCmds)
        addCategory(container, "UTILITIES", utilCmds)

        customAppContainer = findViewById(R.id.customAppContainer)
        val customApps = JSONObject(prefs.customAppConfig)
        customApps.keys().forEach { key -> addCustomAppRow(key, customApps.getString(key)) }

        findViewById<Button>(R.id.btnAddCustomApp).setOnClickListener { addCustomAppRow("", "") }
        
        findViewById<Button>(R.id.btnPermMic).setOnClickListener {
            val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { permissions.add(Manifest.permission.BLUETOOTH_CONNECT) }
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
        }
        findViewById<Button>(R.id.btnPermOverlay).setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        findViewById<Button>(R.id.btnPermAccessibility).setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        findViewById<Button>(R.id.btnPermWriteSettings).setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))) }
        findViewById<Button>(R.id.btnTestVoice).setOnClickListener { AssistantService.instance?.forceListen() }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val usedKeywords = mutableSetOf<String>()
            var hasDuplicate = false
            prefs.apiKey = findViewById<EditText>(R.id.etApiKey).text.toString()
            prefs.wakeWord = findViewById<EditText>(R.id.etWakeWord).text.toString().trim().lowercase()

            commandUIs.forEach {
                val kw = it.et.text.toString().trim().lowercase()
                if (kw.isNotEmpty() && !usedKeywords.add(kw)) hasDuplicate = true
                prefs.saveCmd(it.key, it.sw.isChecked, kw)
            }

            val newCustomApps = JSONObject()
            for (i in 0 until customAppContainer.childCount) {
                val row = customAppContainer.getChildAt(i) as LinearLayout
                val kw = (row.getChildAt(0) as EditText).text.toString().trim().lowercase()
                val pkgInfo = (row.getChildAt(1) as Spinner).selectedItem as? AppInfo
                if (kw.isNotEmpty() && pkgInfo != null) {
                    if (!usedKeywords.add(kw)) hasDuplicate = true
                    else newCustomApps.put(kw, pkgInfo.pkg)
                }
            }

            if (hasDuplicate) { Toast.makeText(this, "Duplicate keyword!", Toast.LENGTH_LONG).show() } 
            else { prefs.customAppConfig = newCustomApps.toString(); Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun addCustomAppRow(keyword: String, pkg: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, 10) }
        val etKw = EditText(this).apply { setText(keyword); hint = "Keyword"; setTextColor(Color.parseColor("#00E676")); setHintTextColor(Color.GRAY); layoutParams = LinearLayout.LayoutParams(0, -2, 1f); textSize = 14f }
        val spinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1.5f)
            adapter = object : ArrayAdapter<AppInfo>(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, installedApps) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    return (super.getView(position, convertView, parent) as TextView).apply { setTextColor(Color.WHITE) }
                }
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    return (super.getDropDownView(position, convertView, parent) as TextView).apply { 
                        setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#333333")) 
                    }
                }
            }
            val idx = installedApps.indexOfFirst { it.pkg == pkg }
            if (idx >= 0) setSelection(idx)
        }
        val btnDel = Button(this).apply { text = "X"; setTextColor(Color.RED); setBackgroundColor(Color.TRANSPARENT); layoutParams = LinearLayout.LayoutParams(-2, -2) }
        btnDel.setOnClickListener { customAppContainer.removeView(row) }
        row.addView(etKw); row.addView(spinner); row.addView(btnDel)
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
        val btConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED } else { true }
        val overlay = Settings.canDrawOverlays(this)
        val acc = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(packageName) == true
        val write = Settings.System.canWrite(this)
        updateBtn(findViewById(R.id.btnPermMic), mic && btConnect, "MIC & BT")
        updateBtn(findViewById(R.id.btnPermOverlay), overlay, "OVERLAY")
        updateBtn(findViewById(R.id.btnPermAccessibility), acc, "ACCESSIBILITY")
        updateBtn(findViewById(R.id.btnPermWriteSettings), write, "SYSTEM WRITE")
    }

    private fun updateBtn(b: Button, ok: Boolean, txt: String) {
        b.text = "$txt: ${if(ok) "OK" else "PENDING"}"
        b.setBackgroundColor(if(ok) Color.parseColor("#3300E676") else Color.parseColor("#222222"))
    }

    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}