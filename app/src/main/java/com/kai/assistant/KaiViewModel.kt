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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

enum class KaiState(val label: String) {
    IDLE("IDLE"),
    LISTENING("LISTENING"),
    THINKING("THINKING"),
    SPEAKING("SPEAKING"),
    EXECUTING("EXECUTING"),
    CONFIRMATION("CONFIRM"),
    ERROR("ERROR")
}

data class VoiceOption(val name: String, val label: String)

data class ToolConfirmation(val call: ToolCall, val plan: ToolRunner.Plan)

@OptIn(ExperimentalCoroutinesApi::class)
class KaiViewModel(app: Application) : AndroidViewModel(app) {

    private val db = KaiDatabase.get(app)
    private val store = SettingsStore(app)
    private val tools = ToolRunner(app)

    // ---- Observable UI state -------------------------------------------

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

    /** A phone action waiting for the user to allow or deny it. */
    var confirmation by mutableStateOf<ToolConfirmation?>(null)
        private set

    var voices by mutableStateOf<List<VoiceOption>>(emptyList())
        private set

    var hasKey by mutableStateOf(false)
        private set

    /** Short feedback line shown in Settings (key saved, test result...). */
    var notice by mutableStateOf("")
        private set

    val settings: StateFlow<KaiSettings> =
        store.flow.stateIn(viewModelScope, SharingStarted.Eagerly, KaiSettings())

    val conversations: StateFlow<List<ConversationEntity>> =
        db.conversations().observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val memories: StateFlow<List<MemoryEntity>> =
        db.memories().observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activity: StateFlow<List<ActivityEntity>> =
        db.activity().observeRecent()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentConversation = MutableStateFlow<Long?>(null)
    val currentConversation: StateFlow<Long?> = _currentConversation

    val messages: StateFlow<List<MessageEntity>> =
        _currentConversation
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList<MessageEntity>()) else db.messages().observe(id)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- Internals -----------------------------------------------------

    private val main = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    private var recognizer: SpeechRecognizer? = null
    private var aiJob: Job? = null

    private var greeted = false
    private var lastBackgrounded = 0L

    private fun idleState() =
        if (confirmation != null) KaiState.CONFIRMATION else KaiState.IDLE

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            main.post { state = KaiState.SPEAKING }
        }

        override fun onDone(utteranceId: String?) {
            main.post { if (state == KaiState.SPEAKING) state = idleState() }
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
        viewModelScope.launch(Dispatchers.IO) { hasKey = store.hasApiKey() }
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

        val available = (engine.voices ?: emptySet())
            .filter { it.locale.language == "en" }
            .sortedBy { it.name }
        voices = available.map {
            val region = it.locale.displayCountry.ifBlank { it.locale.language }
            VoiceOption(it.name, "$region - ${it.name}")
        }
        viewModelScope.launch {
            val saved = store.flow.first().voiceName
            if (saved.isNotBlank()) applyVoice(saved)
        }

        ttsReady = true
        pendingSpeech?.let {
            pendingSpeech = null
            speak(it)
        }
    }

    private fun applyVoice(name: String) {
        val engine = tts ?: return
        val voice = (engine.voices ?: emptySet()).firstOrNull { it.name == name } ?: return
        engine.setVoice(voice)
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
        val spoken = text
            .replace(Regex("[*_#`>~]"), "")
            .take(TextToSpeech.getMaxSpeechInputLength() - 100)
        if (spoken.isBlank()) {
            state = idleState()
            return
        }
        state = KaiState.SPEAKING
        engine.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "kai-utterance")
    }

    // ---- Listening -----------------------------------------------------

    fun startListening() {
        val ctx = getApplication<Application>()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            fail("Speech recognition isn't available on this phone. You can still type to me.")
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
                state = idleState()
            } else {
                handleUserText(text)
            }
        }

        override fun onError(error: Int) {
            level = 0f
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    caption = "I didn't catch that. Tap the mic and try again."
                    state = idleState()
                }
                SpeechRecognizer.ERROR_CLIENT -> state = idleState()
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

    // ---- Talking to the AI ---------------------------------------------

    /** Typed input from the text boxes. */
    fun sendText(text: String) {
        stopAll()
        handleUserText(text)
    }

    private fun handleUserText(raw: String) {
        val text = raw.filter { !it.isISOControl() || it == '\n' }.trim().take(MAX_INPUT)
        if (text.isBlank()) return
        userText = text
        aiJob?.cancel()
        state = KaiState.THINKING
        caption = "Thinking…"

        aiJob = viewModelScope.launch {
            val s = store.flow.first()
            val key = withContext(Dispatchers.IO) { store.apiKey() }
            hasKey = key.isNotBlank()

            val convId = ensureConversation(text)
            db.messages().insert(
                MessageEntity(conversationId = convId, role = "user", content = text, createdAt = now())
            )
            db.conversations().touch(convId, now())

            if (key.isBlank()) {
                deliver(
                    "I can't think without an AI provider yet. Open Settings, pick a provider, and paste your API key.",
                    speak = s.speakReplies
                )
                return@launch
            }

            val memoryList = if (s.memoryEnabled) db.memories().all().map { it.text } else emptyList()
            val history = db.messages().recent(convId, HISTORY_LIMIT).map { ChatMessage(it.role, it.content) }
            val prompt = listOf(ChatMessage("system", Persona.systemPrompt(s.personality, memoryList))) + history

            val host = runCatching { java.net.URI(s.baseUrl).host }.getOrNull() ?: "provider"
            val result = OpenAiCompatProvider(s.baseUrl, key, s.model).chat(prompt)

            result.onFailure {
                log("AI request", "$host / ${s.model}", "failed")
                fail(describe(it))
            }
            result.onSuccess { reply ->
                log("AI request", "$host / ${s.model}", "ok")
                val (cleaned, call) = ToolParser.parse(reply)
                val spoken = cleaned.ifBlank { if (call != null) "On it." else "..." }
                db.messages().insert(
                    MessageEntity(conversationId = convId, role = "assistant", content = spoken, createdAt = now())
                )
                deliver(spoken, speak = s.speakReplies)
                call?.let { handleToolCall(it, s) }
            }
        }
    }

    private fun deliver(text: String, speak: Boolean) {
        if (speak) {
            speak(text)
        } else {
            caption = text
            state = idleState()
        }
    }

    private suspend fun ensureConversation(firstText: String): Long =
        _currentConversation.value ?: db.conversations()
            .insert(ConversationEntity(title = firstText.take(40), updatedAt = now()))
            .also { _currentConversation.value = it }

    private fun describe(e: Throwable): String = when (e) {
        is UnknownHostException, is ConnectException ->
            "I can't reach the internet right now."
        is SocketTimeoutException ->
            "The AI took too long to answer. Try again."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong talking to the AI."
    }

    // ---- Phone tools ---------------------------------------------------

    private suspend fun handleToolCall(call: ToolCall, s: KaiSettings) {
        val plan = tools.plan(call)
        if (plan == null) {
            log("Rejected tool", call.name, "invalid")
            return
        }
        if (call.name == "remember") {
            val text = call.args.optString("text", "").trim().take(200)
            when {
                !s.memoryEnabled -> log("Remember", "Memory is turned off", "skipped")
                ToolRunner.looksSensitive(text) -> log("Remember", "Looked sensitive, not saved", "blocked")
                else -> {
                    db.memories().insert(MemoryEntity(text = text, createdAt = now()))
                    log("Remember", text, "saved")
                }
            }
            return
        }
        if (plan.sensitive) {
            confirmation = ToolConfirmation(call, plan)
            log(plan.title, plan.description, "awaiting confirmation")
        } else {
            runTool(call, plan)
        }
    }

    fun confirmTool() {
        val c = confirmation ?: return
        confirmation = null
        runTool(c.call, c.plan)
    }

    fun denyTool() {
        val c = confirmation ?: return
        confirmation = null
        log(c.plan.title, c.plan.description, "denied")
        caption = "Okay, cancelled."
        if (state != KaiState.SPEAKING) state = KaiState.IDLE
    }

    /** Used by buttons the user taps themselves (Device screen). */
    fun runToolDirect(name: String, args: JSONObject = JSONObject()) {
        val call = ToolCall(name, args)
        val plan = tools.plan(call) ?: return
        runTool(call, plan)
    }

    private fun runTool(call: ToolCall, plan: ToolRunner.Plan) {
        state = KaiState.EXECUTING
        val result = tools.execute(call)
        log(plan.title, plan.description, "done")
        if (plan.speakResult) {
            speak(result)
        } else {
            caption = result
            state = idleState()
        }
    }

    fun deviceSummary(): List<Pair<String, String>> = tools.deviceSummary()

    // ---- Control -------------------------------------------------------

    fun stopAll() {
        aiJob?.cancel()
        aiJob = null
        tts?.stop()
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        pendingSpeech = null
        level = 0f
        if (confirmation != null) {
            val c = confirmation
            confirmation = null
            if (c != null) log(c.plan.title, c.plan.description, "cancelled")
        }
        if (state != KaiState.ERROR) state = KaiState.IDLE
    }

    fun showMessage(message: String) {
        caption = message
        state = idleState()
    }

    private fun fail(message: String) {
        caption = message
        level = 0f
        state = KaiState.ERROR
    }

    // ---- Conversations -------------------------------------------------

    fun openConversation(id: Long) {
        _currentConversation.value = id
    }

    fun closeConversation() {
        _currentConversation.value = null
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            db.messages().deleteFor(id)
            db.conversations().delete(id)
            if (_currentConversation.value == id) _currentConversation.value = null
            log("Deleted conversation", "", "done")
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            db.messages().clear()
            db.conversations().clear()
            _currentConversation.value = null
            log("Cleared all conversation history", "", "done")
        }
    }

    // ---- Memory --------------------------------------------------------

    fun setMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            store.setMemoryEnabled(enabled)
            log("Memory", if (enabled) "Turned on" else "Turned off", "done")
        }
    }

    fun addMemory(text: String) {
        val clean = text.trim().take(200)
        if (clean.isBlank()) return
        viewModelScope.launch {
            db.memories().insert(MemoryEntity(text = clean, createdAt = now()))
            log("Memory added", clean, "done")
        }
    }

    fun editMemory(item: MemoryEntity, newText: String) {
        val clean = newText.trim().take(200)
        if (clean.isBlank()) return
        viewModelScope.launch {
            db.memories().update(item.copy(text = clean))
            log("Memory edited", clean, "done")
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            db.memories().delete(id)
            log("Memory deleted", "", "done")
        }
    }

    fun clearMemories() {
        viewModelScope.launch {
            db.memories().clear()
            log("All memories cleared", "", "done")
        }
    }

    fun clearActivity() {
        viewModelScope.launch { db.activity().clear() }
    }

    // ---- Settings ------------------------------------------------------

    fun setPersonality(p: Personality) {
        viewModelScope.launch { store.setPersonality(p) }
    }

    fun setSpeakReplies(value: Boolean) {
        viewModelScope.launch { store.setSpeakReplies(value) }
    }

    fun selectVoice(option: VoiceOption) {
        viewModelScope.launch { store.setVoice(option.name) }
        applyVoice(option.name)
        speak(GREETING)
    }

    fun setProvider(baseUrl: String, model: String) {
        val url = baseUrl.trim()
        if (!url.startsWith("https://")) {
            notice = "The provider URL must start with https://"
            return
        }
        if (model.isBlank()) {
            notice = "Enter a model name."
            return
        }
        viewModelScope.launch {
            store.setProvider(url, model.trim())
            notice = "Provider saved."
            log("Provider changed", "${java.net.URI(url).host} / ${model.trim()}", "done")
        }
    }

    fun saveApiKey(key: String) {
        if (key.isBlank()) {
            notice = "Paste your API key first."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = store.saveApiKey(key)
            hasKey = store.hasApiKey()
            notice = if (ok) "API key saved (encrypted on this phone)." else "I couldn't store the key securely."
            log("API key", if (ok) "Saved" else "Save failed", if (ok) "done" else "failed")
        }
    }

    fun clearApiKey() {
        viewModelScope.launch(Dispatchers.IO) {
            store.clearApiKey()
            hasKey = store.hasApiKey()
            notice = "API key removed."
            log("API key", "Removed", "done")
        }
    }

    fun testConnection() {
        notice = "Testing…"
        viewModelScope.launch {
            val s = store.flow.first()
            val key = withContext(Dispatchers.IO) { store.apiKey() }
            if (key.isBlank()) {
                notice = "Add an API key first."
                return@launch
            }
            val result = OpenAiCompatProvider(s.baseUrl, key, s.model).chat(
                listOf(
                    ChatMessage("system", "Reply with the single word OK."),
                    ChatMessage("user", "ping")
                )
            )
            notice = result.fold(
                onSuccess = { "Connection works." },
                onFailure = { describe(it) }
            )
        }
    }

    // ---- Helpers -------------------------------------------------------

    private fun now() = System.currentTimeMillis()

    private fun log(title: String, detail: String, status: String) {
        viewModelScope.launch {
            db.activity().insert(
                ActivityEntity(title = title, detail = detail, status = status, createdAt = now())
            )
        }
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
        private const val MAX_INPUT = 2000
        private const val HISTORY_LIMIT = 12
    }
}
