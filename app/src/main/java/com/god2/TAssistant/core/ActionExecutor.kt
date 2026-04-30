package com.god2.TAssistant.core
import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat

class ActionExecutor(private val context: Context, private val prefs: SharedPrefsHelper) {
    fun execute(action: String, target: String?, message: String) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        when (action) {
            "TOGGLE_FLASHLIGHT" -> {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                try {
                    for (id in cm.cameraIdList) {
                        val chars = cm.getCameraCharacteristics(id)
                        val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                        if (hasFlash) { cm.setTorchMode(id, target == "on"); break }
                    }
                } catch (e: Exception) {}
            }
            "SET_ALARM" -> {
                val parts = target?.split(":")
                val h = parts?.get(0)?.toIntOrNull() ?: 7
                val m = parts?.get(1)?.toIntOrNull() ?: 0
                context.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply { putExtra(AlarmClock.EXTRA_HOUR, h); putExtra(AlarmClock.EXTRA_MINUTES, m); putExtra(AlarmClock.EXTRA_SKIP_UI, true); addFlags(flags) })
            }
            "CANCEL_ALARM" -> { context.startActivity(Intent(AlarmClock.ACTION_DISMISS_ALARM).apply { putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, "android.all"); addFlags(flags) }) }
            "SET_TIMER" -> {
                val sec = target?.toIntOrNull() ?: 60
                context.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).apply { putExtra(AlarmClock.EXTRA_LENGTH, sec); putExtra(AlarmClock.EXTRA_SKIP_UI, true); addFlags(flags) })
            }
            "CANCEL_TIMER" -> { context.startActivity(Intent(AlarmClock.ACTION_DISMISS_TIMER).apply { putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, "android.all"); addFlags(flags) } ) }
            "PLAY_MUSIC", "PLAY_RANDOM" -> {
                val query = if (action == "PLAY_RANDOM") "" else (target ?: "")
                val intent = if (query.isEmpty()) { Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.EMPTY, "audio/*") } } else { Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply { putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*"); putExtra(android.app.SearchManager.QUERY, query) } }
                intent.addFlags(flags)
                val pm = context.packageManager
                val resolveInfos = pm.queryIntentActivities(intent, 0)
                if (resolveInfos.isNotEmpty()) {
                    val musicApp = resolveInfos.find { !it.activityInfo.packageName.contains("spotify") } ?: resolveInfos[0]
                    intent.setPackage(musicApp.activityInfo.packageName)
                }
                try { context.startActivity(intent) } catch (e: Exception) { intent.setPackage(null); context.startActivity(intent) }
                Handler(Looper.getMainLooper()).postDelayed({
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
                }, 3500)
            }
            "TOGGLE_WIFI" -> {
                try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        context.startActivity(Intent(Settings.Panel.ACTION_WIFI).addFlags(flags))
                    } else {
                        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                        wm.isWifiEnabled = (target == "on")
                    }
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(flags))
                }
            }
            "TOGGLE_BLUETOOTH" -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(flags))
                        return
                    }
                    val reqAction = if (target == "on") android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE else "android.bluetooth.adapter.action.REQUEST_DISABLE"
                    context.startActivity(Intent(reqAction).addFlags(flags))
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(flags))
                }
            }
            "SEARCH_WEB" -> { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$target")).addFlags(flags)) }
            "GO_HOME" -> AssistantService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
            "LOCK_SCREEN" -> { if (Build.VERSION.SDK_INT >= 28) AssistantService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }
            "OPEN_APP" -> {
                val pm = context.packageManager
                val clean = target?.replace(" ", "")?.lowercase() ?: ""
                var itnt = pm.getLaunchIntentForPackage(target ?: "")
                if (itnt == null) {
                    val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    val f = apps.find { it.loadLabel(pm).toString().lowercase().replace(" ", "").contains(clean) }
                    if (f != null) itnt = pm.getLaunchIntentForPackage(f.activityInfo.packageName)
                }
                itnt?.let { it.addFlags(flags); context.startActivity(it) }
            }
        }
    }
    fun checkBattery() = "Battery is at ${(context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"
}