package com.god2.TAssistant.core
import android.content.Context
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class AssistantEngine(private val context: Context) {
    private val prefs = SharedPrefsHelper(context)
    private val executor = ActionExecutor(context, prefs)
    private val client = OkHttpClient()

    private val commonFixes = mapOf(
        "you tube" to "youtube", "face book" to "facebook", "tik tok" to "tiktok",
        "a lam" to "alarm", "ti mer" to "timer", "flash light" to "flashlight",
        "o pen" to "open", "sea rch" to "search", "mu sic" to "music",
        "ran dom" to "random", "bat te ry" to "battery", "ca me ra" to "camera"
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

        val cmdKeys = listOf("home", "back", "recent", "flashlight", "lock", "battery", "play", "random", "stop", "next", "prev", "call", "sms", "open", "camera", "alarm", "timer", "search", "map")
        var bestKey = "UNKNOWN"
        
        for (key in cmdKeys) {
            val userKeyword = prefs.getKeyword(key, key)
            if (raw.contains(userKeyword) && prefs.isEnabled(key)) {
                bestKey = key
                break
            }
        }

        when (bestKey) {
            "search" -> {
                val query = raw.substringAfter(prefs.getKeyword("search", "search")).trim()
                executor.execute("SEARCH_WEB", query, "")
                onResponse("Searching Google for $query", correction)
            }
            "play" -> { executor.execute("PLAY_MUSIC", raw.substringAfter(prefs.getKeyword("play", "play")).trim(), ""); onResponse("Playing music", correction) }
            "random" -> { executor.execute("PLAY_RANDOM", null, ""); onResponse("Playing random music", correction) }
            "open" -> { executor.execute("OPEN_APP", raw.substringAfter(prefs.getKeyword("open", "open")).trim(), ""); onResponse("Opening app", correction) }
            "camera" -> { executor.execute("OPEN_CAMERA", null, ""); onResponse("Opening camera", correction) }
            "flashlight" -> { executor.execute("TOGGLE_FLASHLIGHT", if(raw.contains("off")) "off" else "on", ""); onResponse("Flashlight toggled", correction) }
            "alarm" -> { executor.execute("SET_ALARM", raw, ""); onResponse("Setting alarm", correction) }
            "timer" -> { 
                val sec = raw.filter { it.isDigit() }.toIntOrNull() ?: 60
                executor.execute("SET_TIMER", if (raw.contains("minute")) (sec * 60).toString() else sec.toString(), "")
                onResponse("Setting timer", correction) 
            }
            "home" -> { executor.execute("GO_HOME", null, ""); onResponse("Going home", correction) }
            "back" -> { executor.execute("GO_BACK", null, ""); onResponse("Going back", correction) }
            "recent" -> { executor.execute("OPEN_RECENTS", null, ""); onResponse("Showing recents", correction) }
            "lock" -> { executor.execute("LOCK_SCREEN", null, ""); onResponse("Locking screen", correction) }
            "battery" -> { val b = executor.checkBattery(); onResponse(b, correction) }
            "call" -> { executor.execute("CALL", raw.substringAfter(prefs.getKeyword("call", "call")).trim(), ""); onResponse("Calling", correction) }
            "map" -> { executor.execute("NAVIGATE", raw.substringAfter(prefs.getKeyword("map", "navigate to")).trim(), ""); onResponse("Navigating", correction) }
            else -> {
                if (prefs.apiKey.isNotEmpty()) {
                    chatWithAI(raw) { aiResp -> onResponse(aiResp, correction) }
                } else {
                    onResponse("Command not found and AI is not configured.", correction)
                }
            }
        }
    }

    private fun chatWithAI(prompt: String, callback: (String) -> Unit) {
        val json = JSONObject()
        json.put("model", "mistral-small-latest")
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "user").put("content", prompt))
        json.put("messages", messages)

        val mediaType = MediaType.parse("application/json; charset=utf-8")
        val body = RequestBody.create(mediaType, json.toString())
        
        val request = Request.Builder()
            .url("https://api.mistral.ai/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${prefs.apiKey}")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback("I'm offline or AI connection failed.") }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body()?.string()
                if (response.isSuccessful && responseBody != null) {
                    try {
                        val aiMsg = JSONObject(responseBody).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                        callback(aiMsg)
                    } catch (e: Exception) { callback("AI busy, try again.") }
                } else { callback("AI service error.") }
            }
        })
    }
}