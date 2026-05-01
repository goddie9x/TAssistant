package com.god2.TAssistant.core
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
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
    private val sp = context.getSharedPreferences("TAssistantVoicePrefs", Context.MODE_PRIVATE)
    
    @Volatile private var isVoiceActive = false
    private var wasMusicPlaying = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val quickFuzzyMap = mapOf(
        "you tube" to "youtube", "face book" to "facebook", "tik tok" to "tiktok",
        "a lam" to "alarm", "ti mer" to "timer", "wi fi" to "wifi", "blue tooth" to "bluetooth"
    )

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val autoSleep = sp.getBoolean("auto_sleep", true)
            if (intent?.action == Intent.ACTION_SCREEN_OFF && autoSleep) {
                try { speechService?.stop(); speechService?.shutdown() } catch (e: Exception) {}
                speechService = null
            } else if (intent?.action == Intent.ACTION_SCREEN_ON && autoSleep) {
                startVosk()
            }
        }
    }

    init {
        StorageService.unpack(context, "model-en", "model", { m: Model -> model = m; startVosk() }, { e -> Log.e("TAssistant", "Model error: ${e.message}") })
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }
        context.registerReceiver(screenReceiver, filter)
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

    private fun playWakeSound() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            mainHandler.postDelayed({ toneGen.release() }, 200)
        } catch (e: Exception) {}
    }

    private fun pauseMedia() {
        if (audioManager.isMusicActive) {
            wasMusicPlaying = true
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        }
    }

    private fun resumeMedia() {
        if (wasMusicPlaying) {
            wasMusicPlaying = false
            mainHandler.postDelayed({
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
            }, 1000)
        }
    }

    fun forceTrigger() {
        if (isVoiceActive) return
        isVoiceActive = true
        playWakeSound()
        pauseMedia()
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
            playWakeSound()
            pauseMedia()
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
            playWakeSound()
            pauseMedia()
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
        resumeMedia()
        speechService?.setPause(false)
    }

    override fun onError(e: Exception) { 
        isVoiceActive = false
        mainHandler.post { overlay.hide() }
        audioManager.abandonAudioFocus(null)
        resumeMedia()
        hardRestartVosk()
    }
    
    override fun onTimeout() { hardRestartVosk() }
    override fun onFinalResult(h: String) {}
    
    fun destroy() { 
        try { context.unregisterReceiver(screenReceiver) } catch (e: Exception) {}
        try { speechService?.stop(); speechService?.shutdown() } catch (e: Exception) {}
        AssistantService.instance?.stopSpeak()
        audioManager.abandonAudioFocus(null) 
    }
}