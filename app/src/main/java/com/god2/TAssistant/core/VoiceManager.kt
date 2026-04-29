package com.god2.TAssistant.core
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VoiceManager(private val context: Context) : org.vosk.android.RecognitionListener {
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private val engine = AssistantEngine(context)
    private val overlay = OverlayManager(context) { closeOverlay() }
    private val prefs = SharedPrefsHelper(context)
    private val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    private var isActive = false
    private var isProcessing = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val watchdog = Runnable { closeOverlay() }

    init {
        StorageService.unpack(context, "model-en", "model", { m: Model -> model = m; startVosk() }, {})
    }

    private fun startVosk() {
        try {
            speechService?.stop(); speechService?.shutdown()
            model?.let {
                val rec = Recognizer(it, 16000.0f)
                speechService = SpeechService(rec, 16000.0f)
                speechService?.startListening(this)
            }
        } catch (e: Exception) { mainHandler.postDelayed({ startVosk() }, 2000) }
    }

    fun forceTrigger() {
        try { toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150) } catch (e: Exception) {}
        isActive = true; isProcessing = false
        mainHandler.post { overlay.showOverlay("TAssistant", "Listening...") }
        resetWatchdog(10000)
    }

    override fun onResult(hypothesis: String) {
        val text = JSONObject(hypothesis).optString("text").lowercase().trim()
        if (text.isEmpty()) return
        val wake = prefs.wakeWord.lowercase()

        if (!isActive && text.contains(wake)) { forceTrigger() }
        else if (isActive && !isProcessing) {
            isProcessing = true
            resetWatchdog(15000)
            mainHandler.post { overlay.showOverlay("Working...", text, true) }
            CoroutineScope(Dispatchers.IO).launch {
                engine.processCommand(text) { resp ->
                    mainHandler.post {
                        overlay.showOverlay("Assistant", resp, false)
                        AssistantService.instance?.speak(resp) {
                            mainHandler.postDelayed({ closeOverlay() }, 1500)
                        }
                    }
                }
            }
        }
    }

    override fun onPartialResult(h: String) {
        val p = JSONObject(h).optString("partial")
        if (p.isNotEmpty() && isActive && !isProcessing) {
            resetWatchdog(10000)
            mainHandler.post { overlay.updateContent(p) }
        }
    }

    private fun resetWatchdog(ms: Long) {
        mainHandler.removeCallbacks(watchdog)
        mainHandler.postDelayed(watchdog, ms)
    }

    private fun closeOverlay() {
        isActive = false; isProcessing = false
        mainHandler.post { overlay.hide() }
        startVosk()
    }

    override fun onFinalResult(h: String) {}
    override fun onError(e: Exception) { closeOverlay() }
    override fun onTimeout() { closeOverlay() }
    fun destroy() { speechService?.shutdown(); toneGen.release() }
}