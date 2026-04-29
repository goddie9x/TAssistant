package com.god2.TAssistant.core
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityButtonController
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.os.Build
import android.speech.tts.TextToSpeech
import java.util.Locale

class AssistantService : AccessibilityService(), TextToSpeech.OnInitListener {
    private var voiceManager: VoiceManager? = null
    private var tts: TextToSpeech? = null
    companion object { var instance: AssistantService? = null }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        tts = TextToSpeech(this, this)
        voiceManager = VoiceManager(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            accessibilityButtonController.registerAccessibilityButtonCallback(
                object : AccessibilityButtonController.AccessibilityButtonCallback() {
                    override fun onClicked(controller: AccessibilityButtonController) { forceListen() }
                }
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) { tts?.language = Locale.US }
    }

    fun speak(text: String, onDone: () -> Unit) {
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { onDone() }
            override fun onError(utteranceId: String?) { onDone() }
        })
        val params = android.os.Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "done")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "done")
    }

    fun stopSpeak() { tts?.stop() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        voiceManager?.destroy()
        tts?.stop()
        tts?.shutdown()
        return super.onUnbind(intent)
    }
    fun performGlobal(action: Int) { performGlobalAction(action) }
    fun forceListen() { voiceManager?.forceTrigger() }
}