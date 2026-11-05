package com.kai.assistant

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kaiDataStore: DataStore<Preferences> by preferencesDataStore(name = "kai_settings")

enum class Personality(val label: String) {
    PROFESSIONAL("Professional"),
    BALANCED("Balanced"),
    SARCASTIC("Sarcastic")
}

object Providers {
    const val GROQ_URL = "https://api.groq.com/openai/v1"
    const val GROQ_MODEL = "openai/gpt-oss-120b"
    const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/openai"
    const val GEMINI_MODEL = "gemini-flash-latest"
}

data class KaiSettings(
    val personality: Personality = Personality.BALANCED,
    val voiceName: String = "",
    val speakReplies: Boolean = true,
    val memoryEnabled: Boolean = true,
    val baseUrl: String = Providers.GROQ_URL,
    val model: String = Providers.GROQ_MODEL
)

class SettingsStore(context: Context) {
    private val appContext = context.applicationContext

    private object Keys {
        val PERSONALITY = stringPreferencesKey("personality")
        val VOICE = stringPreferencesKey("voice")
        val SPEAK = booleanPreferencesKey("speak_replies")
        val MEMORY = booleanPreferencesKey("memory_enabled")
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL = stringPreferencesKey("model")
    }

    val flow: Flow<KaiSettings> = appContext.kaiDataStore.data.map { p ->
        KaiSettings(
            personality = p[Keys.PERSONALITY]
                ?.let { runCatching { Personality.valueOf(it) }.getOrNull() }
                ?: Personality.BALANCED,
            voiceName = p[Keys.VOICE] ?: "",
            speakReplies = p[Keys.SPEAK] ?: true,
            memoryEnabled = p[Keys.MEMORY] ?: true,
            baseUrl = p[Keys.BASE_URL] ?: Providers.GROQ_URL,
            model = p[Keys.MODEL] ?: Providers.GROQ_MODEL
        )
    }

    suspend fun setPersonality(value: Personality) {
        appContext.kaiDataStore.edit { it[Keys.PERSONALITY] = value.name }
    }

    suspend fun setVoice(name: String) {
        appContext.kaiDataStore.edit { it[Keys.VOICE] = name }
    }

    suspend fun setSpeakReplies(value: Boolean) {
        appContext.kaiDataStore.edit { it[Keys.SPEAK] = value }
    }

    suspend fun setMemoryEnabled(value: Boolean) {
        appContext.kaiDataStore.edit { it[Keys.MEMORY] = value }
    }

    suspend fun setProvider(baseUrl: String, model: String) {
        appContext.kaiDataStore.edit {
            it[Keys.BASE_URL] = baseUrl
            it[Keys.MODEL] = model
        }
    }

    // The API key never lives in the APK. The user types it in, and it is stored
    // encrypted (Android Keystore) in this app's private storage only.
    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "kai_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun apiKey(): String =
        runCatching { securePrefs.getString("api_key", "") ?: "" }.getOrDefault("")

    fun hasApiKey(): Boolean = apiKey().isNotBlank()

    fun saveApiKey(key: String): Boolean =
        runCatching { securePrefs.edit().putString("api_key", key.trim()).commit() }
            .getOrDefault(false)

    fun clearApiKey(): Boolean =
        runCatching { securePrefs.edit().remove("api_key").commit() }.getOrDefault(false)
}
