package com.fahad.easypeasy.engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** Real, opt-in MediaPipe adapter. Not configured in the demo: see ModelCatalog and MOCKS.md.
 * Native generation is not cancellable on every backend; gate reuse until its future completes. */
class MediaPipeLanguageModel private constructor(private val inference: LlmInference) : LanguageModel, AutoCloseable {
    private val lifecycle = Any()
    private val nativeGate = Mutex()
    @Volatile private var closed = false
    private var busy = false
    override val ready get() = !closed

    override fun stream(prompt: String): Flow<String> = callbackFlow {
        nativeGate.lock() // Suspend, rather than lose a barge-in turn with a spurious "busy" error.
        synchronized(lifecycle) {
            if (closed) { nativeGate.unlock(); error("Inference has been closed") }
            busy = true
        }
        val future = try {
            inference.generateResponseAsync(prompt) { partial, _ ->
                if (partial.isNotEmpty()) {
                    val result = trySend(partial)
                    if (result.isFailure && !result.isClosed) channel.close(IllegalStateException("Inference output buffer overflow"))
                }
            }
        } catch (error: Exception) {
            synchronized(lifecycle) { busy = false }
            nativeGate.unlock()
            throw error
        }
        future.addListener({
            try { future.get(); channel.close() } catch (error: Exception) { channel.close(error) }
            val release = synchronized(lifecycle) { busy = false; closed }
            if (release) inference.close()
            nativeGate.unlock()
        }, Executor { it.run() })
        // Closing the consumer discards late tokens, but must NOT cancel the native future or
        // free a handle still used by native code. Completion above owns that cleanup.
        awaitClose { }
    }.buffer(256).flowOn(Dispatchers.Default)

    /** Call off Main. If a native turn is active, its completion releases the handle. Idempotent. */
    override fun close() {
        val release = synchronized(lifecycle) {
            if (closed) return
            closed = true
            !busy
        }
        if (release) inference.close()
    }
    companion object {
        suspend fun load(context: Context, model: File): MediaPipeLanguageModel = withContext(Dispatchers.IO) {
            require(model.isFile && !model.name.endsWith(".part")) { "Verified model missing" }
            MediaPipeLanguageModel(LlmInference.createFromOptions(context,
                LlmInference.LlmInferenceOptions.builder().setModelPath(model.absolutePath).setMaxTokens(1024).build()))
        }
    }
}
