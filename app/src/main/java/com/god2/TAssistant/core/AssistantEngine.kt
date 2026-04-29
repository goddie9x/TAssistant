package com.god2.TAssistant.core
import android.content.Context
import android.util.Log

class AssistantEngine(private val context: Context) {
    private val prefs = SharedPrefsHelper(context)
    private val executor = ActionExecutor(context, prefs)

    private val commonFixes = mapOf(
        "you tube" to "youtube", "face book" to "facebook", "tik tok" to "tiktok",
        "a lam" to "alarm", "ti mer" to "timer", "flash light" to "flashlight",
        "o pen" to "open", "sea rch" to "search", "mu sic" to "music"
    )

    suspend fun processCommand(text: String, onResponse: (String, String?) -> Unit) {
        var raw = text.lowercase().trim()
        var correction: String? = null

        commonFixes.forEach { (wrong, right) ->
            if (raw.contains(wrong)) {
                raw = raw.replace(wrong, right)
                correction = "Fuzzy match: $raw"
            }
        }

        val cmdKeys = listOf("home", "back", "recent", "flashlight", "volume", "brightness", "play", "stop", "next", "prev", "call", "sms", "open", "alarm", "timer", "search", "map")
        var bestKey = "UNKNOWN"
        
        for (key in cmdKeys) {
            val userKeyword = prefs.getKeyword(key, key)
            if (raw.contains(userKeyword) && prefs.isEnabled(key)) {
                bestKey = key
                break
            }
        }

        var action = "UNKNOWN"; var target: String? = null; var msg = "Executing..."

        when (bestKey) {
            "play" -> { action = "PLAY_MUSIC"; target = raw.substringAfter(prefs.getKeyword("play", "play")).trim(); msg = "Playing music" }
            "open" -> { action = "OPEN_APP"; target = raw.substringAfter(prefs.getKeyword("open", "open")).trim(); msg = "Opening app" }
            "flashlight" -> { action = "TOGGLE_FLASHLIGHT"; target = if(raw.contains("off")) "off" else "on"; msg = "Flashlight updated" }
            "alarm" -> { action = "SET_ALARM"; target = raw; msg = "Alarm set" }
            "timer" -> { action = "SET_TIMER"; target = raw; msg = "Timer started" }
            "search" -> { action = "SEARCH"; target = raw.substringAfter(prefs.getKeyword("search", "search")).trim(); msg = "Searching" }
            "home" -> { action = "GO_HOME"; msg = "Going home" }
            "back" -> { action = "GO_BACK"; msg = "Going back" }
            "recent" -> { action = "OPEN_RECENTS"; msg = "Showing recents" }
            "call" -> { action = "CALL"; target = raw.substringAfter(prefs.getKeyword("call", "call")).trim(); msg = "Calling" }
            else -> { action = "CHAT"; msg = "I'm not sure, but I heard: $raw" }
        }

        executor.execute(action, target, msg)
        onResponse(msg, correction)
    }
}