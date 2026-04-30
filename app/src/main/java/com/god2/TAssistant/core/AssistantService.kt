package com.god2.TAssistant.core
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class AssistantService : AccessibilityService(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var voiceManager: VoiceManager? = null

    companion object {
        var instance: AssistantService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        tts = TextToSpeech(this, this)
        voiceManager = VoiceManager(this)
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        serviceInfo = info
    }

    override fun onInit(status: Int) {}

    fun forceListen() { voiceManager?.forceTrigger() }
    
    fun performGlobal(actionId: Int) { performGlobalAction(actionId) }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        try {
            val sp = getSharedPreferences("TAssistantVoicePrefs", Context.MODE_PRIVATE)
            val rate = sp.getFloat("tts_speed", 1.0f)
            tts?.setSpeechRate(rate)

            val vName = sp.getString("tts_voice", "")
            if (!vName.isNullOrEmpty()) {
                val targetVoice = tts?.voices?.firstOrNull { it.name == vName }
                if (targetVoice != null) { 
                    tts?.voice = targetVoice
                    tts?.language = targetVoice.locale 
                }
            }
        } catch (e: Exception) {}

        val uid = System.currentTimeMillis().toString()
        if (onDone != null) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { if (utteranceId == uid) onDone() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { onDone() }
            })
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, uid)
    }

    fun stopSpeak() { tts?.stop() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            val root = rootInActiveWindow ?: return
            val btNodes = root.findAccessibilityNodeInfosByText("Bluetooth")
            val wifiNodes = root.findAccessibilityNodeInfosByText("Wi-Fi")
            
            if (btNodes.isNotEmpty() || wifiNodes.isNotEmpty()) {
                val allows = ArrayList<AccessibilityNodeInfo>()
                allows.addAll(root.findAccessibilityNodeInfosByText("Allow"))
                allows.addAll(root.findAccessibilityNodeInfosByText("Turn on"))
                allows.addAll(root.findAccessibilityNodeInfosByText("Cho phép"))
                allows.addAll(root.findAccessibilityNodeInfosByText("Bật"))
                
                for (node in allows) {
                    if (node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        break
                    } else if (node.parent?.isClickable == true) {
                        node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        break
                    }
                }
            }
        } catch (e: Exception) {}
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        voiceManager?.destroy()
        tts?.stop()
        tts?.shutdown()
        instance = null
        super.onDestroy()
    }
}