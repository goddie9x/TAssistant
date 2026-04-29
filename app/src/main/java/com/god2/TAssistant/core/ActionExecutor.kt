package com.god2.TAssistant.core
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast

class ActionExecutor(private val context: Context, private val prefs: SharedPrefsHelper) {
    fun execute(action: String, target: String?, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        when (action) {
            "OPEN_APP" -> if (prefs.allowOpenApp) {
                target?.let {
                    val intent = context.packageManager.getLaunchIntentForPackage(it)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
            "SET_ALARM" -> if (prefs.allowSetAlarm) {
                target?.let {
                    val timeParts = it.split(":")
                    if (timeParts.size == 2) {
                        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                            putExtra(AlarmClock.EXTRA_HOUR, timeParts[0].toIntOrNull() ?: 0)
                            putExtra(AlarmClock.EXTRA_MINUTES, timeParts[1].toIntOrNull() ?: 0)
                            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
            }
            "SET_CALENDAR" -> if (prefs.allowCalendar) {
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, target)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            "OPEN_FILE" -> {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
}
