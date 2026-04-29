package com.god2.TAssistant.core
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
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
    private var idleRunnable = Runnable { closeOverlay() }

    init {
        StorageService.unpack(context, "model-en", "model",
            { model: Model -> this.model = model; startVosk() },
            { e -> Log.e("VOSK", "Unpack err: ${e.message}") }
        )
    }

    private fun startVosk() {
        try {
            speechService?.stop()
            speechService?.shutdown()
            model?.let {
                val rec = Recognizer(it, 16000.0f)
                speechService = SpeechService(rec, 16000.0f)
                speechService?.startListening(this)
            }
        } catch (e: Exception) {
            Log.e("VOSK", "Mic lock: ${e.message}")
            mainHandler.postDelayed({ startVosk() }, 2000)
        }
    }

    fun forceTrigger() {
        try {
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {}
        isActive = true
        isProcessing = false
        mainHandler.post { overlay.showOverlay("TAssistant", "Listening...") }
        resetIdleTimer()
    }

    override fun onPartialResult(hypothesis: String) {
        val partial = JSONObject(hypothesis).optString("partial")
        if (partial.isEmpty() || isProcessing) return
        if (isActive) {
            resetIdleTimer()
            mainHandler.post { overlay.updateContent(partial) }
        }
    }

    override fun onResult(hypothesis: String) {
        val text = JSONObject(hypothesis).optString("text").lowercase()
        if (text.isEmpty()) return
        val wakeWord = prefs.wakeWord.lowercase()

        if (!isActive && text.contains(wakeWord)) {
            forceTrigger()
        } else if (isActive && !isProcessing) {
            val command = text.replace(wakeWord, "").trim()
            if (command.isEmpty()) return
            isProcessing = true
            mainHandler.post { overlay.showOverlay("Thinking...", command, true) }
            
            CoroutineScope(Dispatchers.IO).launch {
                engine.processCommand(command) { responseMsg ->
                    mainHandler.post {
                        overlay.showOverlay("Assistant", responseMsg, false)
                        AssistantService.instance?.speak(responseMsg) {
                            mainHandler.postDelayed({ closeOverlay() }, 2000)
                        }
                    }
                }
            }
        }
    }

    private fun resetIdleTimer() {
        mainHandler.removeCallbacks(idleRunnable)
        mainHandler.postDelayed(idleRunnable, 8000)
    }

    private fun closeOverlay() {
        isActive = false
        isProcessing = false
        mainHandler.post { overlay.hide() }
    }

    override fun onFinalResult(hypothesis: String) {}
    
    override fun onError(e: Exception) { 
        Log.e("VOSK", "Record err: ${e.message}")
        closeOverlay()
        startVosk() 
    }
    
    override fun onTimeout() { 
        closeOverlay()
        startVosk() 
    }
    
    fun destroy() {
        mainHandler.removeCallbacks(idleRunnable)
        speechService?.stop()
        speechService?.shutdown()
        mainHandler.post { overlay.hide() }
        toneGen.release()
    }
}