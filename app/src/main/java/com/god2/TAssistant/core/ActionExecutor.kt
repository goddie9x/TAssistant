package com.god2.TAssistant.core
import android.accessibilityservice.AccessibilityService
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import java.util.Calendar

class ActionExecutor(private val context: Context, private val prefs: SharedPrefsHelper) {
    fun execute(action: String, target: String?, message: String) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        when (action) {
            "OPEN_APP" -> if (prefs.allowOpenApp) {
                target?.let { pkg ->
                    context.packageManager.getLaunchIntentForPackage(pkg)?.also {
                        it.addFlags(flags)
                        context.startActivity(it)
                    }
                }
            }
            "SET_ALARM" -> if (prefs.allowSetAlarm) {
                target?.let {
                    val time = it.filter { c -> c.isDigit() || c == ':' }.split(":")
                    if (time.size >= 2) {
                        val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                            putExtra(AlarmClock.EXTRA_HOUR, time[0].toInt())
                            putExtra(AlarmClock.EXTRA_MINUTES, time[1].toInt())
                            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            addFlags(flags)
                        }
                        context.startActivity(i)
                    }
                }
            }
            "SET_TIMER" -> if (prefs.allowTimer) {
                val sec = target?.filter { it.isDigit() }?.toIntOrNull() ?: 60
                val i = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, sec)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(flags)
                }
                context.startActivity(i)
            }
            "PLAY_MUSIC" -> if (prefs.allowMusic) {
                val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    putExtra(SearchManager.QUERY, target ?: "music")
                    addFlags(flags)
                }
                context.startActivity(i)
            }
            "SEARCH" -> if (prefs.allowSearch) {
                val i = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, target)
                    addFlags(flags)
                }
                context.startActivity(i)
            }
            "SET_VOLUME" -> if (prefs.allowVolume) {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val level = when {
                    target?.contains("max") == true -> max
                    target?.contains("mute") == true -> 0
                    else -> target?.filter { it.isDigit() }?.toIntOrNull() ?: (max / 2)
                }
                am.setStreamVolume(AudioManager.STREAM_MUSIC, level.coerceIn(0, max), AudioManager.FLAG_SHOW_UI)
            }
            "SET_BRIGHTNESS" -> if (Settings.System.canWrite(context)) {
                val b = when {
                    target?.contains("max") == true -> 255
                    target?.contains("min") == true -> 10
                    else -> target?.filter { it.isDigit() }?.toIntOrNull() ?: 128
                }
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, b.coerceIn(0, 255))
            }
            "TOGGLE_FLASHLIGHT" -> {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                try { cm.setTorchMode(cm.cameraIdList[0], target == "on") } catch (e: Exception) {}
            }
            "GO_HOME" -> AssistantService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
            "GO_BACK" -> AssistantService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_BACK)
            "OPEN_RECENTS" -> AssistantService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_RECENTS)
        }
    }
}