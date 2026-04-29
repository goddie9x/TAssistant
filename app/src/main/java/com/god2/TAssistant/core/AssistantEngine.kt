package com.god2.TAssistant.core
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.god2.TAssistant.api.AssistantAction
import com.god2.TAssistant.api.Message
import com.god2.TAssistant.api.MistralRequest
import com.god2.TAssistant.api.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssistantEngine(private val context: Context) {
    private val prefs = SharedPrefsHelper(context)
    private val executor = ActionExecutor(context, prefs)

    suspend fun processCommand(text: String, onResponse: (String) -> Unit) {
        if (isOnline() && prefs.apiKey.isNotEmpty()) processOnline(text, onResponse)
        else processOffline(text.lowercase(), onResponse)
    }

    private fun processOffline(text: String, onResponse: (String) -> Unit) {
        var action = "UNKNOWN"; var target: String? = null; var msg = "Executing your request"
        val cleanText = text.trim()

        when {
            cleanText.contains("play") -> {
                action = "PLAY_MUSIC"
                target = cleanText.substringAfter("play").trim()
                msg = "Playing $target"
            }
            cleanText.contains("volume") -> {
                action = "SET_VOLUME"
                target = cleanText.substringAfter("volume").trim()
                msg = "Adjusting volume"
            }
            cleanText.contains("brightness") -> {
                action = "SET_BRIGHTNESS"
                target = cleanText.substringAfter("brightness").trim()
                msg = "Changing brightness"
            }
            cleanText.contains("alarm") -> {
                action = "SET_ALARM"
                target = cleanText.substringAfter("at").trim().ifEmpty { "07:00" }
                msg = "Alarm set for $target"
            }
            cleanText.contains("timer") -> {
                action = "SET_TIMER"
                target = cleanText.substringAfter("for").trim()
                msg = "Timer started"
            }
            cleanText.contains("search") -> {
                action = "SEARCH"
                target = cleanText.substringAfter("search").trim()
                msg = "Searching Google for $target"
            }
            cleanText.contains("home") -> { action = "GO_HOME"; msg = "Going home" }
            cleanText.contains("back") -> { action = "GO_BACK"; msg = "Going back" }
            cleanText.contains("flashlight on") || cleanText.contains("torch on") -> {
                action = "TOGGLE_FLASHLIGHT"; target = "on"; msg = "Flashlight on"
            }
            cleanText.contains("flashlight off") || cleanText.contains("torch off") -> {
                action = "TOGGLE_FLASHLIGHT"; target = "off"; msg = "Flashlight off"
            }
            cleanText.contains("open") -> {
                action = "OPEN_APP"
                val app = cleanText.substringAfter("open").trim()
                target = when {
                    app.contains("youtube") -> "com.google.android.youtube"
                    app.contains("facebook") -> "com.facebook.katana"
                    app.contains("setting") -> "com.android.settings"
                    else -> "com.google.android.googlequicksearchbox"
                }
                msg = "Opening $app"
            }
        }
        executor.execute(action, target, msg)
        onResponse(msg)
    }

    private suspend fun processOnline(text: String, onResponse: (String) -> Unit) {
        try {
            val prompt = "You are a mobile assistant. Response JSON ONLY: {\"action\": \"ACT\", \"target\": \"TGT\", \"message\": \"MSG\"}. Acts: OPEN_APP, SET_ALARM, SET_TIMER, PLAY_MUSIC, SEARCH, GO_HOME, GO_BACK, TOGGLE_FLASHLIGHT, SET_VOLUME, SET_BRIGHTNESS."
            val req = MistralRequest(prefs.aiModel, listOf(Message("system", prompt), Message("user", text)))
            val res = RetrofitClient.instance.getCompletion("Bearer ${prefs.apiKey}", req)
            if (res.isSuccessful) {
                val obj = Gson().fromJson(res.body()?.string(), AssistantAction::class.java)
                withContext(Dispatchers.Main) {
                    executor.execute(obj.action, obj.target, obj.message)
                    onResponse(obj.message)
                }
            } else { withContext(Dispatchers.Main) { processOffline(text, onResponse) } }
        } catch (e: Exception) { withContext(Dispatchers.Main) { processOffline(text, onResponse) } }
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
    }
}