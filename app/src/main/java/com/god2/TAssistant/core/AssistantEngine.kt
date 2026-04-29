package com.god2.TAssistant.core
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class AssistantEngine(private val context: Context) {
    private val prefs = SharedPrefsHelper(context)
    private val executor = ActionExecutor(context, prefs)

    suspend fun processCommand(text: String, onResponse: (String) -> Unit) {
        val cmd = text.lowercase().trim()
        var action = "UNKNOWN"; var target: String? = null; var msg = "Executing..."

        when {
            cmd.contains("play") -> {
                action = "PLAY_MUSIC"; target = cmd.substringAfter("play").trim()
                msg = "Playing $target"
            }
            cmd.contains("timer") -> {
                action = "SET_TIMER"
                val num = cmd.filter { it.isDigit() }.toIntOrNull() ?: 60
                target = if (cmd.contains("minute")) (num * 60).toString() else num.toString()
                msg = "Timer set for $num"
            }
            cmd.contains("open") -> {
                action = "OPEN_APP"; target = cmd.substringAfter("open").trim()
                msg = "Opening $target"
            }
            cmd.contains("search") -> {
                action = "SEARCH"; target = cmd.substringAfter("search").trim()
                msg = "Searching $target"
            }
            cmd.contains("flashlight on") -> { action = "TOGGLE_FLASHLIGHT"; target = "on"; msg = "Flashlight on" }
            cmd.contains("flashlight off") -> { action = "TOGGLE_FLASHLIGHT"; target = "off"; msg = "Flashlight off" }
            cmd.contains("home") -> { action = "GO_HOME"; msg = "Going home" }
            else -> { action = "CHAT"; msg = "I heard you said $cmd" }
        }
        executor.execute(action, target, msg)
        onResponse(msg)
    }
}