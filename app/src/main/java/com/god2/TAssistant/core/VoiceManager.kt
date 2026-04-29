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

class VoiceManager(private val context: Context) : org.vosk.android.RecognitionListener {
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private val engine = AssistantEngine(context)
    private val overlay = OverlayManager(context) { releaseAndStop() }
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = SharedPrefsHelper(context)
    
    @Volatile private var isVoiceActive = false
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
        } catch (e: Exception) {
            mainHandler.postDelayed({ startVosk() }, 2000)
        }
    }

    fun forceTrigger() {
        if (isVoiceActive) return
        isVoiceActive = true
        audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        AssistantService.instance?.stopSpeak() // Ngắt tiếng AI đang nói dở
        mainHandler.post { overlay.showOverlay("TAssistant", "Listening...") }
    }

    override fun onResult(hypothesis: String) {
        val text = JSONObject(hypothesis).optString("text").lowercase().trim()
        if (text.isEmpty()) return

        if (!isVoiceActive && text.contains(prefs.wakeWord.lowercase())) {
            forceTrigger()
        } else if (isVoiceActive) {
            stopVoskInternal()
            mainHandler.post { overlay.showOverlay("Processing...", text, true) }
            
            CoroutineScope(Dispatchers.IO).launch {
                engine.processCommand(text) { resp, correction ->
                    mainHandler.post {
                        if (!isVoiceActive) return@post // Nếu người dùng đã Cancel thì hủy việc đọc
                        val msg = if (correction != null) "$correction\n\n$resp" else resp
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
        try { speechService?.stop(); speechService?.shutdown() } catch (e: Exception) {}
        speechService = null
    }

    private fun releaseAndStop() {
        if (!isVoiceActive) return // Đã đóng rồi thì không xử lý nữa
        isVoiceActive = false
        AssistantService.instance?.stopSpeak() // NGẮT HỌNG AI NGAY LẬP TỨC
        mainHandler.post { overlay.hide() }
        audioManager.abandonAudioFocus(null)
        stopVoskInternal()
        mainHandler.postDelayed({ startVosk() }, 800)
    }

    override fun onPartialResult(h: String) {
        val p = JSONObject(h).optString("partial")
        if (p.isNotEmpty() && isVoiceActive) mainHandler.post { overlay.updateContent(p) }
    }

    override fun onError(e: Exception) { releaseAndStop() }
    override fun onTimeout() { releaseAndStop() }
    override fun onFinalResult(h: String) {}

    fun destroy() {
        stopVoskInternal()
        AssistantService.instance?.stopSpeak()
        audioManager.abandonAudioFocus(null)
    }
}