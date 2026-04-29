package com.god2.TAssistant.core
import android.content.Context
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.regex.Pattern

class AssistantEngine(private val context: Context) {
    private val prefs = SharedPrefsHelper(context)
    private val executor = ActionExecutor(context, prefs)
    private val client = OkHttpClient()

    private val commonFixes = mapOf(
        "you tube" to "youtube", "face book" to "facebook", "tik tok" to "tiktok",
        "a lam" to "alarm", "ti mer" to "timer", "flash light" to "flashlight",
        "wi fi" to "wifi", "blue tooth" to "bluetooth", "da ta" to "data"
    )

    private val numMap = mapOf(
        "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
        "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10",
        "eleven" to "11", "twelve" to "12", "thirteen" to "13", "fourteen" to "14", "fifteen" to "15",
        "sixteen" to "16", "seventeen" to "17", "eighteen" to "18", "nineteen" to "19", "twenty" to "20",
        "thirty" to "30", "forty" to "40", "fifty" to "50", "sixty" to "60"
    )

    suspend fun processCommand(text: String, onResponse: (String, String?) -> Unit) {
        var raw = text.lowercase().trim()
        var correction: String? = null

        // 1. CHUYỂN CHỮ THÀNH SỐ TRƯỚC KHI XỬ LÝ
        numMap.forEach { (word, digit) -> raw = raw.replace(Regex("\\b$word\\b"), digit) }

        commonFixes.forEach { (wrong, right) ->
            if (raw.contains(wrong)) {
                raw = raw.replace(wrong, right)
                correction = "Fuzzy match: $raw"
            }
        }

        val cmdKeys = listOf(
            "cancel_timer", "cancel_alarm", "home", "back", "recent", "flashlight", "lock", "battery", 
            "volume", "brightness", "random", "play", "stop", "next", "prev", 
            "call", "sms", "open", "camera", "alarm", "timer", "search", "map", 
            "wifi", "bluetooth", "data"
        )
        
        var bestKey = "UNKNOWN"
        for (key in cmdKeys) {
            val userKeyword = prefs.getKeyword(key, key)
            if (Regex("\\b$userKeyword\\b").containsMatchIn(raw) && prefs.isEnabled(key)) {
                bestKey = key
                break
            }
        }

        when (bestKey) {
            "cancel_timer" -> { executor.execute("CANCEL_TIMER", null, ""); onResponse("All timers cleared", correction) }
            "cancel_alarm" -> { executor.execute("CANCEL_ALARM", null, ""); onResponse("All alarms dismissed", correction) }
            "alarm" -> {
                val time = extractTime(raw)
                executor.execute("SET_ALARM", "${time.first}:${time.second}", "")
                val displayTime = String.format("%02d:%02d", time.first, time.second)
                onResponse("Alarm set for $displayTime", correction)
            }
            "timer" -> { 
                val digits = raw.filter { it.isDigit() }
                val value = if (digits.isNotEmpty()) digits.toInt() else 1
                val sec = if (raw.contains("hour")) value * 3600 else if (raw.contains("minute") || raw.contains("min")) value * 60 else value
                executor.execute("SET_TIMER", sec.toString(), "")
                onResponse("Timer set for $value", correction) 
            }
            "search" -> { val query = raw.substringAfter(prefs.getKeyword("search", "search")).trim(); executor.execute("SEARCH_WEB", query, ""); onResponse("Searching Google", correction) }
            "play" -> { executor.execute("PLAY_MUSIC", raw.substringAfter(prefs.getKeyword("play", "play")).trim(), ""); onResponse("Playing music", correction) }
            "open" -> { executor.execute("OPEN_APP", raw.substringAfter(prefs.getKeyword("open", "open")).trim(), ""); onResponse("Opening app", correction) }
            "flashlight" -> { executor.execute("TOGGLE_FLASHLIGHT", if(raw.contains("off")) "off" else "on", ""); onResponse("Flashlight toggled", correction) }
            "call" -> { executor.execute("CALL", raw.substringAfter(prefs.getKeyword("call", "call")).trim(), ""); onResponse("Calling", correction) }
            else -> {
                if (prefs.apiKey.isNotEmpty()) { chatWithAI(raw) { onResponse(it, correction) } } 
                else { onResponse("I heard: $raw", correction) }
            }
        }
    }

    private fun extractTime(text: String): Pair<Int, Int> {
        // Regex bắt: số(1-2 chữ số) + (am/pm/h/giờ/phút) + số(2 chữ số)
        val m = Pattern.compile("(\\d{1,2})\\s*(am|pm|h|:|giờ|phút)?\\s*(\\d{1,2})?").matcher(text)
        if (m.find()) {
            var h = m.group(1).toInt()
            val marker = m.group(2) ?: ""
            val min = m.group(3)?.toInt() ?: 0
            
            if (marker.contains("pm") && h < 12) h += 12
            if (marker.contains("am") && h == 12) h = 0
            
            return Pair(h.coerceIn(0, 23), min.coerceIn(0, 59))
        }
        return Pair(7, 0)
    }

    private fun chatWithAI(prompt: String, callback: (String) -> Unit) {
        val json = JSONObject().put("model", "mistral-small-latest")
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
        json.put("messages", messages)
        val body = RequestBody.create(okhttp3.MediaType.parse("application/json; charset=utf-8"), json.toString())
        val request = Request.Builder().url("https://api.mistral.ai/v1/chat/completions").addHeader("Authorization", "Bearer ${prefs.apiKey}").post(body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback("AI Error.") }
            override fun onResponse(call: Call, response: Response) {
                val b = response.body()?.string()
                if (response.isSuccessful && b != null) {
                    try { callback(JSONObject(b).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")) } 
                    catch (e: Exception) { callback("AI busy.") }
                } else { callback("AI Fail.") }
            }
        })
    }
}