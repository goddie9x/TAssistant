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
        "twenty one" to "21", "twenty two" to "22", "twenty three" to "23", "twenty four" to "24", "twenty five" to "25", "twenty six" to "26", "twenty seven" to "27", "twenty eight" to "28", "twenty nine" to "29",
        "thirty one" to "31", "thirty two" to "32", "thirty three" to "33", "thirty four" to "34", "thirty five" to "35", "thirty six" to "36", "thirty seven" to "37", "thirty eight" to "38", "thirty nine" to "39",
        "forty one" to "41", "forty two" to "42", "forty three" to "43", "forty four" to "44", "forty five" to "45", "forty six" to "46", "forty seven" to "47", "forty eight" to "48", "forty nine" to "49",
        "fifty one" to "51", "fifty two" to "52", "fifty three" to "53", "fifty four" to "54", "fifty five" to "55", "fifty six" to "56", "fifty seven" to "57", "fifty eight" to "58", "fifty nine" to "59",
        "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
        "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10",
        "eleven" to "11", "twelve" to "12", "thirteen" to "13", "fourteen" to "14", "fifteen" to "15",
        "sixteen" to "16", "seventeen" to "17", "eighteen" to "18", "nineteen" to "19",
        "twenty" to "20", "thirty" to "30", "forty" to "40", "fifty" to "50", "sixty" to "60"
    )

    data class CmdTarget(val isCustom: Boolean, val key: String, val kw: String)

    suspend fun processCommand(text: String, onResponse: (String, String?) -> Unit) {
        var raw = text.lowercase().trim()
        var correction: String? = null

        // 1. Chuyển đổi số chữ thành số nguyên (Hỗ trợ Timer/Alarm cực mạnh)
        numMap.forEach { (word, digit) -> raw = raw.replace(Regex("\\b$word\\b"), digit) }
        
        // 2. Chữa lỗi chính tả các app thông dụng
        commonFixes.forEach { (wrong, right) -> 
            if (raw.contains(wrong)) { 
                raw = raw.replace(wrong, right)
            } 
        }

        // 3. ƯU TIÊN ÉP CÁC LỆNH HỦY (Chặn đụng độ giữa Cancel và Set)
        val cancelAlarmKw = prefs.getKeyword("cancel_alarm", "cancel alarm").lowercase()
        val alarmRegex = Regex("\\b(cancel|stop|turn off|clear|delete|tắt|huỷ|hủy|xoá|xóa)\\s+(the\\s+)?(alarm|báo thức)(s)?\\b")
        if (alarmRegex.containsMatchIn(raw) && !raw.contains(cancelAlarmKw)) {
            raw = raw.replace(alarmRegex, cancelAlarmKw)
        }

        val cancelTimerKw = prefs.getKeyword("cancel_timer", "cancel timer").lowercase()
        val timerRegex = Regex("\\b(cancel|stop|turn off|clear|delete|tắt|huỷ|hủy|xoá|xóa)\\s+(the\\s+)?(timer|hẹn giờ)(s)?\\b")
        if (timerRegex.containsMatchIn(raw) && !raw.contains(cancelTimerKw)) {
            raw = raw.replace(timerRegex, cancelTimerKw)
        }

        // 4. GOM TOÀN BỘ LỆNH (System + Custom) VÀO 1 RỔ ĐỂ SO SÁNH
        val allTargets = mutableListOf<CmdTarget>()
        val cmdKeys = listOf(
            "cancel_timer", "cancel_alarm", "home", "back", "recent", "lock", "battery", 
            "volume", "brightness", "random", "play", "stop", "next", "prev", 
            "call", "sms", "open", "camera", "alarm", "timer", "search", "map", 
            "wifi_on", "wifi_off", "bluetooth_on", "bluetooth_off", "flashlight_on", "flashlight_off", "data_on", "data_off"
        )
        for (key in cmdKeys) {
            if (prefs.isEnabled(key)) {
                allTargets.add(CmdTarget(false, key, prefs.getKeyword(key, key.replace("_", " ")).lowercase().trim()))
            }
        }
        val customApps = JSONObject(prefs.customAppConfig)
        val customKeys = customApps.keys()
        while (customKeys.hasNext()) {
            val ck = customKeys.next()
            allTargets.add(CmdTarget(true, ck, ck.lowercase().trim()))
        }

        // 5. THUẬT TOÁN TÌM KIẾM (Chính xác trước, Fuzzy sau)
        var bestTarget: CmdTarget? = null

        // Bước 5.1: Tìm chuỗi khớp chính xác hoàn toàn (Ưu tiên từ khóa dài nhất)
        for (t in allTargets) {
            if (raw.contains(t.kw)) {
                if (bestTarget == null || t.kw.length > bestTarget.kw.length) {
                    bestTarget = t
                }
            }
        }

        // Bước 5.2: Tìm kiếm mờ (Fuzzy Search) - Nếu không khớp chính xác
        if (bestTarget == null) {
            for (t in allTargets) {
                if (isFuzzyMatch(raw, t.kw)) {
                    if (bestTarget == null || t.kw.length > bestTarget.kw.length) {
                        bestTarget = t
                        correction = "Fuzzy mapped to: ${t.kw}"
                    }
                }
            }
        }

        // 6. THỰC THI LỆNH
        if (bestTarget != null) {
            if (bestTarget.isCustom) {
                executor.execute("OPEN_APP", customApps.getString(bestTarget.key), "")
                onResponse("Opening custom app", correction)
            } else {
                when (bestTarget.key) {
                    "cancel_timer" -> { executor.execute("CANCEL_TIMER", null, ""); onResponse("All timers cleared", correction) }
                    "cancel_alarm" -> { executor.execute("CANCEL_ALARM", null, ""); onResponse("All alarms dismissed", correction) }
                    "alarm" -> {
                        val time = extractTime(raw)
                        executor.execute("SET_ALARM", "${time.first}:${time.second}", "")
                        onResponse("Alarm set for ${String.format("%02d:%02d", time.first, time.second)}", correction)
                    }
                    "timer" -> { 
                        val totalSec = extractTimerSeconds(raw)
                        executor.execute("SET_TIMER", totalSec.toString(), "")
                        val res = if (totalSec >= 3600) "${totalSec/3600}h ${(totalSec%3600)/60}m" else if (totalSec >= 60) "${totalSec/60} minutes" else "$totalSec seconds"
                        onResponse("Timer set for $res", correction) 
                    }
                    "search" -> { val query = raw.substringAfter(prefs.getKeyword("search", "search")).trim(); executor.execute("SEARCH_WEB", query, ""); onResponse("Searching Google", correction) }
                    "play" -> { executor.execute("PLAY_MUSIC", raw.substringAfter(prefs.getKeyword("play", "play")).trim(), ""); onResponse("Playing music", correction) }
                    "random" -> { executor.execute("PLAY_RANDOM", null, ""); onResponse("Playing random music", correction) }
                    "open" -> { executor.execute("OPEN_APP", raw.substringAfter(prefs.getKeyword("open", "open")).trim(), ""); onResponse("Opening app", correction) }
                    "flashlight_on" -> { executor.execute("TOGGLE_FLASHLIGHT", "on", ""); onResponse("Flashlight turned on", correction) }
                    "flashlight_off" -> { executor.execute("TOGGLE_FLASHLIGHT", "off", ""); onResponse("Flashlight turned off", correction) }
                    "wifi_on" -> { executor.execute("WIFI", "on", ""); onResponse("Opening WiFi dialog", correction) }
                    "wifi_off" -> { executor.execute("WIFI", "off", ""); onResponse("Opening WiFi dialog", correction) }
                    "bluetooth_on" -> { executor.execute("BLUETOOTH", "on", ""); onResponse("Opening Bluetooth dialog", correction) }
                    "bluetooth_off" -> { executor.execute("BLUETOOTH", "off", ""); onResponse("Opening Bluetooth dialog", correction) }
                    "data_on" -> { executor.execute("DATA", "on", ""); onResponse("Opening Data dialog", correction) }
                    "data_off" -> { executor.execute("DATA", "off", ""); onResponse("Opening Data dialog", correction) }
                    "lock" -> { executor.execute("LOCK_SCREEN", null, ""); onResponse("Screen locked", correction) }
                    "battery" -> { onResponse(executor.checkBattery(), correction) }
                    "call" -> { executor.execute("CALL", raw.substringAfter(prefs.getKeyword("call", "call")).trim(), ""); onResponse("Calling", correction) }
                    "home" -> { executor.execute("GO_HOME", null, ""); onResponse("Going home", correction) }
                    "back" -> { executor.execute("GO_BACK", null, ""); onResponse("Going back", correction) }
                    "recent" -> { executor.execute("RECENT_APPS", null, ""); onResponse("Opening recents", correction) }
                }
            }
        } else {
            // KHÔNG TÌM THẤY LỆNH NÀO -> GỌI AI
            if (prefs.apiKey.isNotEmpty()) { chatWithAI(raw) { onResponse(it, correction) } } 
            else { onResponse("I heard: $raw", correction) }
        }
    }

    // --- HÀM TÍNH KHOẢNG CÁCH LEVENSHTEIN ---
    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }

    // --- HÀM FUZZY MATCH THÔNG MINH ---
    private fun isFuzzyMatch(raw: String, keyword: String): Boolean {
        val rawWords = raw.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val keyWords = keyword.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        
        if (keyWords.isEmpty() || rawWords.size < keyWords.size) return false
        
        // Quét từng cụm từ trong câu nói để xem có khớp với lệnh hay không
        for (i in 0 .. rawWords.size - keyWords.size) {
            var match = true
            for (j in keyWords.indices) {
                val rw = rawWords[i + j]
                val kw = keyWords[j]
                if (kw != rw) {
                    val dist = levenshtein(rw, kw)
                    // RULE: Dưới 3 chữ cái => Phải giống 100% (chống nhầm on/off). Dưới 6 chữ => Cho sai 1.
                    val maxTypos = if (kw.length <= 3) 0 else if (kw.length <= 6) 1 else 2
                    if (dist > maxTypos) { match = false; break }
                }
            }
            if (match) return true
        }
        return false
    }

    // --- HÀM BÓC TÁCH THỜI GIAN ALARM CHUẨN ---
    private fun extractTime(text: String): Pair<Int, Int> {
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

    // --- HÀM BÓC TÁCH THỜI GIAN TIMER SIÊU MẠNH (Giờ, Phút, Giây) ---
    private fun extractTimerSeconds(text: String): Int {
        var total = 0
        val hrMatcher = Pattern.compile("(\\d+)\\s*(hour|h|giờ)").matcher(text)
        while (hrMatcher.find()) total += hrMatcher.group(1).toInt() * 3600
        
        val minMatcher = Pattern.compile("(\\d+)\\s*(minute|min|m|phút)").matcher(text)
        while (minMatcher.find()) total += minMatcher.group(1).toInt() * 60
        
        val secMatcher = Pattern.compile("(\\d+)\\s*(second|sec|s|giây)").matcher(text)
        while (secMatcher.find()) total += secMatcher.group(1).toInt()

        if (total == 0) {
            val digits = text.filter { it.isDigit() }
            if (digits.isNotEmpty()) {
                val value = digits.toInt()
                total = if (text.contains("sec") || text.contains("giây")) value else value * 60
            } else {
                total = 60 // Mặc định 1 phút nếu không nghe rõ số
            }
        }
        return total
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