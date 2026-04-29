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

    suspend fun processCommand(text: String) {
        val lowerText = text.lowercase()
        if (isOnline() && prefs.apiKey.isNotEmpty()) {
            processOnline(text)
        } else {
            processOffline(lowerText)
        }
    }

    private fun processOffline(text: String) {
        var action = "UNKNOWN"
        var target: String? = null
        var message = "Đã xử lý offline"

        if ((text.contains("mở app") || text.contains("mở ứng dụng")) && prefs.allowOpenApp) {
            action = "OPEN_APP"
            target = "com.google.android.youtube" 
            message = "Đang mở ứng dụng"
        } else if (text.contains("báo thức") && prefs.allowSetAlarm) {
            action = "SET_ALARM"
            target = "07:00"
            message = "Đã đặt báo thức"
        } else if ((text.contains("lịch") || text.contains("sự kiện")) && prefs.allowCalendar) {
            action = "SET_CALENDAR"
            target = "Sự kiện mới"
            message = "Đang mở lịch"
        } else if (text.contains("mở file")) {
            action = "OPEN_FILE"
            message = "Đang mở trình quản lý file"
        }

        executor.execute(action, target, message)
    }

    private suspend fun processOnline(text: String) {
        try {
            val systemPrompt = "You are an Android Assistant. Return JSON format: {\"action\": \"ACTION_NAME\", \"target\": \"VALUE\", \"message\": \"REPLY\"}. Actions: OPEN_APP (target is package name), SET_ALARM (target HH:mm), SET_CALENDAR (target is event title), OPEN_FILE."
            val request = MistralRequest(
                model = prefs.aiModel,
                messages = listOf(
                    Message("system", systemPrompt),
                    Message("user", text)
                )
            )
            val response = RetrofitClient.instance.getCompletion("Bearer ", request)
            if (response.isSuccessful) {
                val jsonStr = response.body()?.string() ?: ""
                val actionObj = Gson().fromJson(jsonStr, AssistantAction::class.java)
                withContext(Dispatchers.Main) {
                    executor.execute(actionObj.action, actionObj.target, actionObj.message)
                }
            } else {
                withContext(Dispatchers.Main) { processOffline(text) }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { processOffline(text) }
        }
    }

    private fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
