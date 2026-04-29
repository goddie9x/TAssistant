package com.god2.TAssistant.core
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.MediaStore
import android.view.KeyEvent

class ActionExecutor(private val context: Context, private val prefs: SharedPrefsHelper) {
    fun execute(action: String, target: String?, message: String) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        when (action) {
            "SEARCH_WEB" -> {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$target")).apply { addFlags(flags) }
                context.startActivity(i)
            }
            "NAVIGATE" -> {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$target")).apply { addFlags(flags) }
                context.startActivity(i)
            }
            "PLAY_MUSIC" -> {
                val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    target?.let { putExtra(android.app.SearchManager.QUERY, it) }
                    addFlags(flags)
                }
                context.startActivity(i)
            }
            "PLAY_RANDOM" -> {
                val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    putExtra(android.app.SearchManager.QUERY, "music")
                    addFlags(flags)
                }
                context.startActivity(i)
                Handler(Looper.getMainLooper()).postDelayed({
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
                }, 3500)
            }
            "CALL" -> {
                val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$target")).apply { addFlags(flags) }
                context.startActivity(i)
            }
            "TOGGLE_FLASHLIGHT" -> {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                try { cm.setTorchMode(cm.cameraIdList[0], target == "on") } catch (e: Exception) {}
            }
            "SET_ALARM" -> {
                val i = Intent(AlarmClock.ACTION_SET_ALARM).apply { putExtra(AlarmClock.EXTRA_SKIP_UI, true); addFlags(flags) }
                context.startActivity(i)
            }
            "SET_TIMER" -> {
                val sec = target?.toIntOrNull() ?: 60
                val i = Intent(AlarmClock.ACTION_SET_TIMER).apply { putExtra(AlarmClock.EXTRA_LENGTH, sec); putExtra(AlarmClock.EXTRA_SKIP_UI, true); addFlags(flags) }
                context.startActivity(i)
            }
            "GO_HOME" -> AssistantService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
            "GO_BACK" -> AssistantService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_BACK)
            "OPEN_RECENTS" -> AssistantService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_RECENTS)
            "LOCK_SCREEN" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    AssistantService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                }
            }
            "OPEN_CAMERA" -> {
                val i = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply { addFlags(flags) }
                context.startActivity(i)
            }
            "OPEN_APP" -> {
                val pkg = context.packageManager.getInstalledApplications(0)
                    .find { it.loadLabel(context.packageManager).toString().lowercase().replace(" ", "").contains(target?.replace(" ", "") ?: "") }?.packageName
                pkg?.let { p -> context.packageManager.getLaunchIntentForPackage(p)?.also { it.addFlags(flags); context.startActivity(it) } }
            }
        }
    }
    fun checkBattery(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return "Battery is at $pct percent"
    }
}