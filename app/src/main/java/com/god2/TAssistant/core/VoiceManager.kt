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

    private val quickFuzzyMap = mapOf(
        "you tube" to "youtube", "face book" to "facebook", "tik tok" to "tiktok",
        "a lam" to "alarm", "ti mer" to "timer", "wi fi" to "wifi", "blue tooth" to "bluetooth"
    )

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

    private fun hardRestartVosk() {
        try { speechService?.stop(); speechService?.shutdown() } catch (e: Exception) {}
        speechService = null
        mainHandler.postDelayed({ startVosk() }, 1000)
    }

    fun forceTrigger() {
        if (isVoiceActive) return
        isVoiceActive = true
        audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        AssistantService.instance?.stopSpeak()
        speechService?.setPause(false)
        mainHandler.post { overlay.showOverlay("TAssistant", "Listening...") }
    }

    override fun onPartialResult(h: String) {
        val p = JSONObject(h).optString("partial").lowercase().trim()
        if (p.isEmpty()) return
        val wake = prefs.wakeWord.lowercase()
        
        if (!isVoiceActive && p.contains(wake)) {
            isVoiceActive = true
            AssistantService.instance?.stopSpeak()
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            mainHandler.post { overlay.showOverlay("TAssistant", "Listening...") }
        }
        
        if (isVoiceActive) {
            var displayStr = if (p.contains(wake)) p.substringAfter(wake).trim() else p
            quickFuzzyMap.forEach { (k, v) -> displayStr = displayStr.replace(k, v) }
            if (displayStr.isNotEmpty()) mainHandler.post { overlay.updateContent(displayStr) }
        }
    }

    override fun onResult(hypothesis: String) {
        val text = JSONObject(hypothesis).optString("text").lowercase().trim()
        if (text.isEmpty()) return
        val wake = prefs.wakeWord.lowercase()

        if (!isVoiceActive && text.contains(wake)) {
            isVoiceActive = true
            AssistantService.instance?.stopSpeak()
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        }

        if (isVoiceActive) {
            var cmd = if (text.contains(wake)) text.substringAfter(wake).trim() else text
            quickFuzzyMap.forEach { (k, v) -> cmd = cmd.replace(k, v) }
            
            if (cmd.isNotEmpty()) {
                speechService?.setPause(true)
                
                mainHandler.post {
                    overlay.showFlashCommand(cmd) { finalCmd ->
                        CoroutineScope(Dispatchers.IO).launch {
                            engine.processCommand(finalCmd) { resp, corr ->
                                mainHandler.post {
                                    if (!isVoiceActive) return@post
                                    overlay.showOverlay("Assistant", resp, false)
                                    AssistantService.instance?.speak(resp) { 
                                        mainHandler.postDelayed({ releaseAndStop() }, 1200) 
                                    }
                                }
                            }
                        }
                    }
                }
            } else { 
                mainHandler.post { overlay.showOverlay("TAssistant", "Listening...") } 
            }
        }
    }

    private fun releaseAndStop() {
        if (!isVoiceActive) return
        isVoiceActive = false
        AssistantService.instance?.stopSpeak()
        mainHandler.post { overlay.hide() }
        audioManager.abandonAudioFocus(null)
        
        speechService?.setPause(false)
    }

    override fun onError(e: Exception) { 
        isVoiceActive = false
        mainHandler.post { overlay.hide() }
        audioManager.abandonAudioFocus(null)
        hardRestartVosk() // Chỉ Reset Mic khi hệ thống thực sự báo lỗi cướp Mic
    }
    
    override fun onTimeout() { 
        hardRestartVosk() 
    }
    
    override fun onFinalResult(h: String) {}
    
    fun destroy() { 
        try { speechService?.stop(); speechService?.shutdown() } catch (e: Exception) {}
        AssistantService.instance?.stopSpeak()
        audioManager.abandonAudioFocus(null) 
    }
}