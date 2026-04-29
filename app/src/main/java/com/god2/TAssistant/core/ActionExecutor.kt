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
import android.provider.Settings
import android.view.KeyEvent

class ActionExecutor(private val context: Context, private val prefs: SharedPrefsHelper) {
    fun execute(action: String, target: String?, message: String) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        when (action) {
            "SEARCH_WEB" -> { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$target")).apply { addFlags(flags) }) }
            "NAVIGATE" -> { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$target")).apply { addFlags(flags) }) }
            "PLAY_MUSIC" -> {
                val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply { putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*"); target?.let { putExtra(android.app.SearchManager.QUERY, it) }; addFlags(flags) }
                context.startActivity(i)
            }
            "PLAY_RANDOM" -> {
                val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply { putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*"); putExtra(android.app.SearchManager.QUERY, "music"); addFlags(flags) }
                context.startActivity(i)
                Handler(Looper.getMainLooper()).postDelayed({
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
                }, 3500)
            }
            "CALL" -> { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$target")).apply { addFlags(flags) }) }
            "TOGGLE_FLASHLIGHT" -> {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                try { cm.setTorchMode(cm.cameraIdList[0], target == "on") } catch (e: Exception) {}
            }
            "SET_ALARM" -> { context.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply { putExtra(AlarmClock.EXTRA_SKIP_UI, true); addFlags(flags) }) }
            "SET_TIMER" -> {
                val sec = target?.toIntOrNull() ?: 60
                context.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).apply { putExtra(AlarmClock.EXTRA_LENGTH, sec); putExtra(AlarmClock.EXTRA_SKIP_UI, true); addFlags(flags) })
            }
            "GO_HOME" -> AssistantService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
            "GO_BACK" -> AssistantService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_BACK)
            "OPEN_RECENTS" -> AssistantService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_RECENTS)
            "LOCK_SCREEN" -> { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) AssistantService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }
            "OPEN_CAMERA" -> { context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply { addFlags(flags) }) }
            "SET_VOLUME" -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (target?.toIntOrNull() ?: (max / 2)).coerceIn(0, max), AudioManager.FLAG_SHOW_UI)
            }
            "SET_BRIGHTNESS" -> {
                if (Settings.System.canWrite(context)) Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (target?.toIntOrNull() ?: 128).coerceIn(0, 255))
            }
            "OPEN_APP" -> {
                val pm = context.packageManager
                val cleanTarget = target?.replace(" ", "")?.lowercase() ?: ""
                
                // 1. Phím tắt mở thẳng Settings hệ thống nếu gọi
                if (cleanTarget == "settings" || cleanTarget == "setting" || cleanTarget == "càiđặt") {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { addFlags(flags) })
                    return
                }

                // 2. Cố gắng lấy Launch Intent thẳng
                var intent = pm.getLaunchIntentForPackage(target ?: "")
                
                // 3. Quét Fuzzy Logic với CATEGORY_LAUNCHER (Bao gồm cả app hệ thống)
                if (intent == null) {
                    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                    val apps = pm.queryIntentActivities(mainIntent, 0)
                    val found = apps.find { it.loadLabel(pm).toString().lowercase().replace(" ", "").contains(cleanTarget) }
                    if (found != null) {
                        intent = pm.getLaunchIntentForPackage(found.activityInfo.packageName)
                    }
                }
                intent?.let { it.addFlags(flags); context.startActivity(it) }
            }
        }
    }
    fun checkBattery(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return "Battery is at ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} percent"
    }
}