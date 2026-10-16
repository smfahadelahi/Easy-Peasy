package com.fahad.easypeasy.engine

import kotlinx.coroutines.flow.flowOf

/** Explicit demo only. Never use for microphone transcription or an AI readiness check. */
class FakeEngine : SpeechRecognizer, LanguageModel {
    override val ready = false
    override suspend fun transcribe(pcm16: ShortArray, sampleRate: Int): String = error("DEMO: speech recognition is not installed")
    override fun stream(prompt: String) = flowOf("""{"reply_ko":"다시 말해 주세요.","hint_bn":"এটি ডেমো, AI নয়।","correction":"","new_word":"","step_done":false}""")
}
