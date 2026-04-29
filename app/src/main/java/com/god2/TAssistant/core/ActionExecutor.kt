package com.god2.TAssistant.core
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.hardware.camera2.CameraManager

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
            "GO_HOME" -> AssistantService.instance?.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
            "OPEN_APP" -> {
                val pkg = context.packageManager.getInstalledApplications(0)
                    .find { it.loadLabel(context.packageManager).toString().lowercase().replace(" ", "").contains(target?.replace(" ", "") ?: "") }?.packageName
                pkg?.let { p -> context.packageManager.getLaunchIntentForPackage(p)?.also { it.addFlags(flags); context.startActivity(it) } }
            }
        }
    }
}