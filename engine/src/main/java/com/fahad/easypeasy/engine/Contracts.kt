package com.fahad.easypeasy.engine

import kotlinx.coroutines.flow.Flow

interface SpeechRecognizer {
    val ready: Boolean
    suspend fun transcribe(pcm16: ShortArray, sampleRate: Int = 16000): String
}
interface LanguageModel {
    val ready: Boolean
    fun stream(prompt: String): Flow<String>
}
interface SpeechOutput {
    suspend fun speak(text: String)
    fun stop()
}
interface VoiceActivityDetector : AutoCloseable {
    fun probability(samples: FloatArray): Float
    fun reset()
}
enum class ConversationState { IDLE, LISTENING, THINKING, SPEAKING }
data class ConversationContext(val knownWords: List<String>, val previousSummary: String, val objective: String)
