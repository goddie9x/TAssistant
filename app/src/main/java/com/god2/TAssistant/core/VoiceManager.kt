package com.god2.TAssistant.core
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import org.json.JSONObject
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class VoiceManager(private val context: Context) : org.vosk.android.RecognitionListener {
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private val engine = AssistantEngine(context)
    private val overlay = OverlayManager(context) { releaseAndStop() }
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = SharedPrefsHelper(context)
    private val isActive = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        StorageService.unpack(context, "model-en", "model", { m: Model -> model = m; startVosk() }, { e -> Log.e("TAssistant", "Model error: ${e.message}") })
    }

    private fun startVosk() {
        if (speechService != null || model == null) return
        try {
            val rec = Recognizer(model, 16000.0f)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
            Log.d("TAssistant", "Vosk started successfully")
        } catch (e: Exception) {
            Log.e("TAssistant", "Failed to start Vosk: ${e.message}")
            mainHandler.postDelayed({ startVosk() }, 2000)
        }
    }

    fun forceTrigger() {
        if (isActive.get()) return
        isActive.set(true)
        audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        mainHandler.post { overlay.showOverlay("TAssistant", "Listening...") }
    }

    override fun onResult(hypothesis: String) {
        val text = JSONObject(hypothesis).optString("text").lowercase().trim()
        if (text.isEmpty()) return

        if (!isActive.get() && text.contains(prefs.wakeWord.lowercase())) {
            forceTrigger()
        } else if (isActive.get()) {
            stopVoskInternal() // Ngắt mic ngay để tránh lỗi buffer khi bắt đầu xử lý
            mainHandler.post { overlay.showOverlay("Processing...", text, true) }
            
            CoroutineScope(Dispatchers.IO).launch {
                engine.processCommand(text) { resp, correction ->
                    mainHandler.post {
                        val msg = if (correction != null) "$correction\n$resp" else resp
                        overlay.showOverlay("Assistant", msg, false)
                        AssistantService.instance?.speak(msg) {
                            mainHandler.postDelayed({ releaseAndStop() }, 1500)
                        }
                    }
                }
            }
        }
    }

    private fun stopVoskInternal() {
        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (e: Exception) {
            Log.e("TAssistant", "Stop error: ${e.message}")
        }
        speechService = null
    }

    private fun releaseAndStop() {
        isActive.set(false)
        mainHandler.post { overlay.hide() }
        audioManager.abandonAudioFocus(null)
        stopVoskInternal()
        mainHandler.postDelayed({ startVosk() }, 800)
    }

    override fun onPartialResult(h: String) {
        val p = JSONObject(h).optString("partial")
        if (p.isNotEmpty() && isActive.get()) mainHandler.post { overlay.updateContent(p) }
    }

    override fun onError(e: Exception) {
        Log.e("TAssistant", "Vosk Error: ${e.message}")
        releaseAndStop()
    }

    override fun onTimeout() { releaseAndStop() }
    override fun onFinalResult(h: String) {}

    fun destroy() {
        stopVoskInternal()
        audioManager.abandonAudioFocus(null)
    }
}