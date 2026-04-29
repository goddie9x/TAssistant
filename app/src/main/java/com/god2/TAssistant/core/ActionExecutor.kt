package com.god2.TAssistant.core
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent

class ActionExecutor(private val context: Context, private val prefs: SharedPrefsHelper) {
    fun execute(action: String, target: String?, message: String) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        when (action) {
            "SET_ALARM" -> {
                val parts = target?.split(":")
                val h = parts?.get(0)?.toIntOrNull() ?: 7
                val m = parts?.get(1)?.toIntOrNull() ?: 0
                val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, h)
                    putExtra(AlarmClock.EXTRA_MINUTES, m)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(flags)
                }
                context.startActivity(i)
            }
            "CANCEL_ALARM" -> {
                val i = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, "android.all")
                    addFlags(flags)
                }
                context.startActivity(i)
            }
            "SET_TIMER" -> {
                val sec = target?.toIntOrNull() ?: 60
                val i = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, sec)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(flags)
                }
                context.startActivity(i)
            }
            "CANCEL_TIMER" -> {
                val i = Intent(AlarmClock.ACTION_DISMISS_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, "android.all")
                    addFlags(flags)
                }
                context.startActivity(i)
            }
            "TOGGLE_WIFI" -> {
                if (Build.VERSION.SDK_INT >= 29) {
                    context.startActivity(Intent(Settings.Panel.ACTION_WIFI).addFlags(flags))
                } else {
                    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                    wm.isWifiEnabled = (target == "on")
                }
            }
            "TOGGLE_BLUETOOTH" -> {
                val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
                if (target == "on") bm.adapter?.enable() else bm.adapter?.disable()
            }
            "SEARCH_WEB" -> { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$target")).addFlags(flags)) }
            "GO_HOME" -> AssistantService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
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