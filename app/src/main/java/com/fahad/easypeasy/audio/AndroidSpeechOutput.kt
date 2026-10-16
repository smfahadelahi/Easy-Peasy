package com.fahad.easypeasy.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.fahad.easypeasy.engine.SpeechOutput
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*

@Singleton
class AndroidSpeechOutput @Inject constructor(@ApplicationContext context: Context) : SpeechOutput {
    private val initialized = CompletableDeferred<Boolean>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val tts = TextToSpeech(context) { status -> initialized.complete(status == TextToSpeech.SUCCESS) }
    var speed = 0.85f
    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { pending.remove(utteranceId)?.complete(Unit) }
            @Deprecated("Android callback")
            override fun onError(utteranceId: String?) { pending.remove(utteranceId)?.completeExceptionally(IllegalStateException("Korean speech unavailable. Install an offline Korean voice in Android settings.")) }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { pending.remove(utteranceId)?.cancel() }
        })
    }
    override suspend fun speak(text: String) = withContext(Dispatchers.Main.immediate) {
        withTimeout(30_000) {
            check(initialized.await()) { "No Android speech engine. Install an offline Korean TTS voice." }
            val voice = tts.voices?.firstOrNull { it.locale.language == Locale.KOREAN.language && !it.isNetworkConnectionRequired }
            checkNotNull(voice) { "Install an offline Korean voice in Android TTS settings. Network voices are never used." }
            tts.voice = voice
            tts.setSpeechRate(speed.coerceIn(0.5f, 1.2f))
            val id = UUID.randomUUID().toString()
            val completion = CompletableDeferred<Unit>()
            pending[id] = completion
            try {
                check(tts.speak(text.take(240), TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.SUCCESS)
                completion.await()
            } finally { pending.remove(id) }
        }
    }
    override fun stop() { tts.stop(); pending.values.forEach { it.cancel() }; pending.clear() }
    fun shutdown() { stop(); tts.shutdown() }
}
