package com.god2.TAssistant.core
import android.accessibilityservice.AccessibilityService
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.MediaStore
import android.view.KeyEvent

class ActionExecutor(private val context: Context, private val prefs: SharedPrefsHelper) {
    fun execute(action: String, target: String?, message: String) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        when (action) {
            "PLAY_MUSIC" -> if (prefs.allowMusic) {
                val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    putExtra(SearchManager.QUERY, target)
                    addFlags(flags)
                }
                context.startActivity(i)
                Handler(Looper.getMainLooper()).postDelayed({
                    val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
                    val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.dispatchMediaKeyEvent(down); am.dispatchMediaKeyEvent(up)
                }, 3000)
            }
            "SET_TIMER" -> if (prefs.allowTimer) {
                val sec = target?.toIntOrNull() ?: 60
                val i = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, sec)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(flags)
                }
                context.startActivity(i)
            }
            "SET_ALARM" -> if (prefs.allowSetAlarm) {
                target?.let {
                    val t = it.filter { c -> c.isDigit() || c == ':' }.split(":")
                    if (t.size >= 2) {
                        val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                            putExtra(AlarmClock.EXTRA_HOUR, t[0].toInt())
                            putExtra(AlarmClock.EXTRA_MINUTES, t[1].toInt())
                            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            addFlags(flags)
                        }
                        context.startActivity(i)
                    }
                }
            }
            "OPEN_APP" -> if (prefs.allowOpenApp) {
                target?.let { name ->
                    val pkg = context.packageManager.getInstalledApplications(0)
                        .find { it.loadLabel(context.packageManager).toString().lowercase().contains(name.lowercase()) }?.packageName
                    pkg?.let { p -> context.packageManager.getLaunchIntentForPackage(p)?.also { it.addFlags(flags); context.startActivity(it) } }
                }
            }
            "SEARCH" -> {
                val i = Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra(SearchManager.QUERY, target); addFlags(flags) }
                context.startActivity(i)
            }
            "TOGGLE_FLASHLIGHT" -> {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                try { cm.setTorchMode(cm.cameraIdList[0], target == "on") } catch (e: Exception) {}
            }
            "GO_HOME" -> AssistantService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
        }
    }
}