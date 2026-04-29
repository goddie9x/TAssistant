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
        AssistantService.instance?.stopSpeak()
        mainHandler.post { overlay.showOverlay("TAssistant", "Listening...") }
    }

    override fun onPartialResult(h: String) {
        val p = JSONObject(h).optString("partial").lowercase().trim()
        if (p.isEmpty()) return

        val wake = prefs.wakeWord.lowercase()

        // Bắt Wake Word thời gian thực
        if (!isVoiceActive && p.contains(wake)) {
            isVoiceActive = true
            AssistantService.instance?.stopSpeak()
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            mainHandler.post { overlay.showOverlay("TAssistant", "Listening...") }
        }

        if (isVoiceActive) {
            // Vừa hiện vừa lắng nghe
            val displayStr = if (p.contains(wake)) p.substringAfter(wake).trim() else p
            if (displayStr.isNotEmpty()) {
                mainHandler.post { overlay.updateContent(displayStr) }
            }
        }
    }

    override fun onResult(hypothesis: String) {
        val text = JSONObject(hypothesis).optString("text").lowercase().trim()
        if (text.isEmpty()) return

        val wake = prefs.wakeWord.lowercase()

        if (!isVoiceActive && text.contains(wake)) {
            isVoiceActive = true
            AssistantService.instance?.stopSpeak()
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }

        if (isVoiceActive) {
            val cmd = if (text.contains(wake)) text.substringAfter(wake).trim() else text
            
            if (cmd.isNotEmpty()) {
                processCommandWithEdit(cmd)
            } else {
                mainHandler.post { overlay.showOverlay("TAssistant", "Listening...") }
            }
        }
    }

    private fun processCommandWithEdit(cmd: String) {
        stopVoskInternal() // Ngắt Mic để trả tài nguyên
        mainHandler.post {
            overlay.showEditableCommand(cmd) { finalCmd ->
                CoroutineScope(Dispatchers.IO).launch {
                    engine.processCommand(finalCmd) { resp, correction ->
                        mainHandler.post {
                            if (!isVoiceActive) return@post
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
    }

    private fun stopVoskInternal() {
        try { speechService?.stop(); speechService?.shutdown() } catch (e: Exception) {}
        speechService = null
    }

    private fun releaseAndStop() {
        if (!isVoiceActive) return
        isVoiceActive = false
        AssistantService.instance?.stopSpeak()
        mainHandler.post { overlay.hide() }
        audioManager.abandonAudioFocus(null)
        stopVoskInternal()
        mainHandler.postDelayed({ startVosk() }, 800)
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