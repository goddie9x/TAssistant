package com.god2.TAssistant.core
import android.content.Context
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
    private val overlay = OverlayManager(context)
    private val prefs = SharedPrefsHelper(context)
    private var isActive = false

    init {
        StorageService.unpack(context, "model-vn", "model",
            { model: Model ->
                this.model = model
                startVosk()
            },
            { e -> overlay.showText("Lỗi: ") }
        )
    }

    private fun startVosk() {
        model?.let {
            val rec = Recognizer(it, 16000.0f)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
        }
    }

    override fun onResult(hypothesis: String) {
        val text = JSONObject(hypothesis).optString("text").lowercase()
        val wakeWord = prefs.wakeWord.lowercase()
        if (text.isEmpty()) return

        if (!isActive && text.contains(wakeWord)) {
            isActive = true
            overlay.showText("Tôi đây, bro nói đi...")
        } else if (isActive) {
            overlay.showText("Đang xử lý: ")
            CoroutineScope(Dispatchers.IO).launch {
                engine.processCommand(text)
                Handler(Looper.getMainLooper()).postDelayed({
                    overlay.hide()
                    isActive = false
                }, 3000)
            }
        }
    }

    override fun onPartialResult(hypothesis: String) {
        val partial = JSONObject(hypothesis).optString("partial")
        if (partial.isNotEmpty()) {
            overlay.showText(partial)
        }
    }

    override fun onFinalResult(hypothesis: String) {}
    override fun onError(e: Exception) { overlay.showText("Lỗi: ") }
    override fun onTimeout() {}

    fun destroy() {
        speechService?.stop()
        speechService?.shutdown()
    }
}
