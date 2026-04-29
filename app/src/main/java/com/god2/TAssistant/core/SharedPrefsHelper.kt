package com.god2.TAssistant.core
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class SharedPrefsHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("TAssistantPrefs", Context.MODE_PRIVATE)

    var apiKey: String get() = prefs.getString("api_key", "") ?: ""; set(v) = prefs.edit().putString("api_key", v).apply()
    var wakeWord: String get() = prefs.getString("wake_word", "hey bro") ?: "hey bro"; set(v) = prefs.edit().putString("wake_word", v).apply()
    var cmdConfig: String get() = prefs.getString("cmd_config", "{}") ?: "{}"; set(v) = prefs.edit().putString("cmd_config", v).apply()

    fun isEnabled(key: String): Boolean = try { JSONObject(cmdConfig).getJSONObject(key).getBoolean("en") } catch(e: Exception) { true }
    fun getKeyword(key: String, default: String): String = try { JSONObject(cmdConfig).getJSONObject(key).getString("cmd") } catch(e: Exception) { default }
    
    fun saveCmd(key: String, en: Boolean, cmd: String) {
        val json = JSONObject(cmdConfig)
        val obj = JSONObject().put("en", en).put("cmd", cmd)
        json.put(key, obj)
        cmdConfig = json.toString()
    }
}