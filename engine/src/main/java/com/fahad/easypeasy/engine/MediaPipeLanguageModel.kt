package com.fahad.easypeasy.engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

/** Genuine MediaPipe adapter, not enabled in the demo. Load on Dispatchers.IO after SHA verification.
 * Model format/quality and Bangla support must be device-qualified before adding a catalog URL. */
class MediaPipeLanguageModel private constructor(private val inference: LlmInference) : LanguageModel, AutoCloseable {
    @Volatile private var closed = false
    @Volatile private var busy = false
    override val ready get() = !closed
    override fun stream(prompt: String): Flow<String> = callbackFlow {
        synchronized(this@MediaPipeLanguageModel) {
            check(!closed && !busy) { "Inference is still finishing the previous turn" }
            busy = true
        }
        val future = try {
            inference.generateResponseAsync(prompt) { partial, done ->
                if (partial.isNotEmpty()) trySend(partial)
                if (done) channel.close()
            }
        } catch (error: Exception) {
            busy = false; throw error
        }
        future.addListener({
            busy = false
            try { future.get(); channel.close() } catch (error: Exception) { channel.close(error) }
        }, Executor { it.run() })
        // Native generation may continue after UI cancellation. Gate future calls until it finishes.
        // Do not destroy an inference handle while its native callback is still running.
        awaitClose { }
    }.buffer(256)
    override fun close() {
        check(!busy) { "Wait for native inference to finish before releasing it" }
        closed = true; inference.close()
    }
    companion object {
        suspend fun load(context: Context, model: File): MediaPipeLanguageModel = withContext(Dispatchers.IO) {
            require(model.isFile && !model.name.endsWith(".part")) { "Verified model missing" }
            MediaPipeLanguageModel(LlmInference.createFromOptions(context,
                LlmInference.LlmInferenceOptions.builder().setModelPath(model.absolutePath).setMaxTokens(1024).build()))
        }
    }
}
