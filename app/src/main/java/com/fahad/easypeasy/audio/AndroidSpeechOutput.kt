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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AndroidSpeechOutput @Inject constructor(@ApplicationContext private val context: Context) : SpeechOutput {
    private var engine: TextToSpeech? = null
    private var initialized: CompletableDeferred<Boolean>? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val speaking = Mutex()
    private var epoch = 0L // All public methods are owned by Dispatchers.Main.
    var speed = 0.85f

    private fun initialize(): CompletableDeferred<Boolean> {
        initialized?.let { return it }
        val ready = CompletableDeferred<Boolean>()
        initialized = ready
        engine = TextToSpeech(context) { status -> ready.complete(status == TextToSpeech.SUCCESS) }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { utteranceId?.let { pending.remove(it)?.complete(Unit) } }
                @Deprecated("Android callback")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { pending.remove(it)?.completeExceptionally(IllegalStateException("Offline Korean speech failed")) }
                }
                override fun onError(utteranceId: String?, errorCode: Int) { onError(utteranceId) }
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    utteranceId?.let { pending.remove(it)?.cancel() }
                }
            })
        }
        return ready
    }

    override suspend fun speak(text: String): Unit = withContext(Dispatchers.Main.immediate) {
        val token = epoch
        speaking.withLock {
            withTimeout(30_000) {
                check(initialize().await()) { "Install an offline Korean TTS engine" }
                ensureActive()
                if (token != epoch) throw CancellationException("Speech stopped while initializing")
                val tts = checkNotNull(engine)
                val voice = tts.voices?.firstOrNull {
                    it.locale.language == Locale.KOREAN.language && !it.isNetworkConnectionRequired && "notInstalled" !in it.features.orEmpty()
                }
                checkNotNull(voice) { "Install a downloaded Korean voice in Android TTS settings" }
                check(tts.setVoice(voice) == TextToSpeech.SUCCESS)
                check(tts.setSpeechRate(speed.coerceIn(0.5f, 1.2f)) == TextToSpeech.SUCCESS)
                val id = UUID.randomUUID().toString()
                val completion = CompletableDeferred<Unit>()
                pending[id] = completion
                try {
                    check(tts.speak(text.take(240), TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS)
                    completion.await()
                } finally {
                    pending.remove(id)
                    // Timeout/cancellation must stop audio as well as the waiting coroutine.
                    if (!completion.isCompleted && token == epoch) tts.stop()
                }
            }
        }
    }
    override fun stop() {
        epoch++
        engine?.stop()
        pending.values.forEach { it.cancel() }
        pending.clear()
    }
    fun shutdown() {
        stop(); engine?.shutdown(); engine = null; initialized = null
    }
}
