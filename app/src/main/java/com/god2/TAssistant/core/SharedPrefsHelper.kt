package com.god2.TAssistant.core
import android.content.Context
import android.content.SharedPreferences

class SharedPrefsHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("TAssistantPrefs", Context.MODE_PRIVATE)
    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()
    var aiModel: String
        get() = prefs.getString("ai_model", "mistral-small-latest") ?: "mistral-small-latest"
        set(value) = prefs.edit().putString("ai_model", value).apply()
    var wakeWord: String
        get() = prefs.getString("wake_word", "hey bro") ?: "hey bro"
        set(value) = prefs.edit().putString("wake_word", value).apply()
    var allowOpenApp: Boolean
        get() = prefs.getBoolean("allow_open_app", true)
        set(value) = prefs.edit().putBoolean("allow_open_app", value).apply()
    var allowSetAlarm: Boolean
        get() = prefs.getBoolean("allow_set_alarm", true)
        set(value) = prefs.edit().putBoolean("allow_set_alarm", value).apply()
    var allowCalendar: Boolean
        get() = prefs.getBoolean("allow_calendar", true)
        set(value) = prefs.edit().putBoolean("allow_calendar", value).apply()
    var allowTimer: Boolean
        get() = prefs.getBoolean("allow_timer", true)
        set(value) = prefs.edit().putBoolean("allow_timer", value).apply()
    var allowSearch: Boolean
        get() = prefs.getBoolean("allow_search", true)
        set(value) = prefs.edit().putBoolean("allow_search", value).apply()
    var allowMusic: Boolean
        get() = prefs.getBoolean("allow_music", true)
        set(value) = prefs.edit().putBoolean("allow_music", value).apply()
    var allowVolume: Boolean
        get() = prefs.getBoolean("allow_volume", true)
        set(value) = prefs.edit().putBoolean("allow_volume", value).apply()
}