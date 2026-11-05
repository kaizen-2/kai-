package com.kai.assistant

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.util.Locale

enum class KaiState(val label: String) {
    IDLE("IDLE"),
    LISTENING("LISTENING"),
    THINKING("THINKING"),
    SPEAKING("SPEAKING"),
    ERROR("ERROR")
}

class KaiViewModel(app: Application) : AndroidViewModel(app) {

    var state by mutableStateOf(KaiState.IDLE)
        private set

    /** What Kai is currently saying / showing. */
    var caption by mutableStateOf("")
        private set

    /** What the user said last. */
    var userText by mutableStateOf("")
        private set

    /** Live microphone loudness, 0f..1f. */
    var level by mutableStateOf(0f)
        private set

    private val main = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    private var recognizer: SpeechRecognizer? = null

    private var greeted = false
    private var lastBackgrounded = 0L

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            main.post { state = KaiState.SPEAKING }
        }

        override fun onDone(utteranceId: String?) {
            main.post { if (state == KaiState.SPEAKING) state = KaiState.IDLE }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            main.post { fail("My voice engine hit a problem.") }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            main.post { fail("My voice engine hit a problem (code $errorCode).") }
        }
    }

    init {
        tts = TextToSpeech(app) { status ->
            main.post { onTtsInit(status) }
        }
    }

    private fun onTtsInit(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            fail("No text-to-speech engine found. Install Google Speech Services from the Play Store.")
            return
        }
        val result = engine.setLanguage(Locale.US)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            fail("English voice data is missing. Open Settings > Text-to-speech and install English.")
            return
        }
        engine.setPitch(0.9f)
        engine.setSpeechRate(0.97f)
        engine.setOnUtteranceProgressListener(utteranceListener)
        ttsReady = true
        pendingSpeech?.let {
            pendingSpeech = null
            speak(it)
        }
    }

    // ---- App lifecycle -------------------------------------------------

    fun onForeground() {
        val now = SystemClock.elapsedRealtime()
        if (!greeted || now - lastBackgrounded > IDLE_GREETING_MS) {
            greeted = true
            speak(GREETING)
        }
    }

    fun onBackground() {
        lastBackgrounded = SystemClock.elapsedRealtime()
        stopAll()
    }

    // ---- Speaking ------------------------------------------------------

    fun speak(text: String) {
        caption = text
        val engine = tts
        if (!ttsReady || engine == null) {
            pendingSpeech = text
            return
        }
        state = KaiState.SPEAKING
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kai-utterance")
    }

    // ---- Listening -----------------------------------------------------

    fun startListening() {
        val ctx = getApplication<Application>()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            fail("Speech recognition isn't available on this phone.")
            return
        }
        stopAll()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        r.setRecognitionListener(recognitionListener)
        recognizer = r
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        userText = ""
        caption = "I'm listening."
        level = 0f
        state = KaiState.LISTENING
        r.startListening(intent)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            // rmsdB is roughly -2..10; map to 0..1
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onEndOfSpeech() {
            level = 0f
            if (state == KaiState.LISTENING) state = KaiState.THINKING
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) userText = text
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            level = 0f
            if (text.isNullOrBlank()) {
                caption = "I didn't catch that. Tap the mic and try again."
                state = KaiState.IDLE
            } else {
                userText = text
                handleUserText(text)
            }
        }

        override fun onError(error: Int) {
            level = 0f
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    caption = "I didn't catch that. Tap the mic and try again."
                    state = KaiState.IDLE
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    fail("I need microphone permission to hear you.")
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    fail("Speech recognition needs an internet connection right now.")
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    fail("The speech recogniser is busy. Try again in a moment.")
                else -> fail("Speech recognition failed (code $error).")
            }
        }
    }

    /**
     * Milestone 3 has no AI brain yet, so Kai says so honestly instead of faking a reply.
     * The AI provider gets plugged in here in the next milestone.
     */
    private fun handleUserText(text: String) {
        speak("I heard you say: $text. My brain isn't connected yet. That's the next milestone.")
    }

    // ---- Control -------------------------------------------------------

    fun stopAll() {
        tts?.stop()
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        pendingSpeech = null
        level = 0f
        if (state != KaiState.ERROR) state = KaiState.IDLE
    }

    fun showMessage(message: String) {
        caption = message
        state = KaiState.IDLE
    }

    private fun fail(message: String) {
        caption = message
        level = 0f
        state = KaiState.ERROR
    }

    override fun onCleared() {
        tts?.stop()
        tts?.shutdown()
        recognizer?.destroy()
        super.onCleared()
    }

    companion object {
        const val GREETING = "Hello, sir. Ready to get to work?"
        private const val IDLE_GREETING_MS = 5 * 60 * 1000L
    }
}
