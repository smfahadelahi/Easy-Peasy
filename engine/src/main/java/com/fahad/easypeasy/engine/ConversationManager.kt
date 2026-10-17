package com.fahad.easypeasy.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Called on one owner dispatcher (Main on Android). Audio producer posts events to that dispatcher.
 * Each utterance owns an epoch. A stopped/barred-in turn cannot resurrect TTS or state. */
class ConversationManager(
    private val scope: CoroutineScope,
    private val recognizer: SpeechRecognizer,
    private val model: LanguageModel,
    private val output: SpeechOutput,
    private val context: suspend () -> ConversationContext,
) {
    private val mutable = MutableStateFlow(ConversationState.IDLE)
    val state = mutable.asStateFlow()
    private val repliesMutable = MutableSharedFlow<TutorReply>(extraBufferCapacity = 8)
    val replies = repliesMutable.asSharedFlow()
    private val errorsMutable = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors = errorsMutable.asSharedFlow()
    private var turn: Job? = null
    private var epoch = 0L
    fun start(): Boolean {
        if (!recognizer.ready || !model.ready) {
            errorsMutable.tryEmit("Live voice unavailable: install all verified engines first.")
            return false
        }
        if (mutable.value == ConversationState.IDLE) mutable.value = ConversationState.LISTENING
        return true
    }
    fun speechStarted() {
        if (mutable.value == ConversationState.IDLE) return
        // VAD remains alive while speaking. Cancel BEFORE stop: late completions are stale.
        epoch++; turn?.cancel(); turn = null; output.stop()
        mutable.value = ConversationState.LISTENING
    }
    fun utterance(pcm: ShortArray) {
        if (mutable.value != ConversationState.LISTENING || pcm.isEmpty()) { pcm.fill(0); return }
        val token = ++epoch
        mutable.value = ConversationState.THINKING
        turn = scope.launch {
            try {
                val transcript = recognizer.transcribe(pcm)
                if (transcript.isBlank()) return@launch
                val memory = context()
                val prompt = PromptBuilder.build(memory, transcript)
                var raw = ""
                var first: String? = null
                var firstPlayback: Job? = null
                withTimeout(90_000) {
                    model.stream(prompt).collect { chunk ->
                        ensureActive()
                        require(raw.length + chunk.length <= 8192) { "Response too long" }
                        raw += chunk
                        if (first == null) ReplyParser.firstSentence(raw)?.let { sentence ->
                            first = sentence
                            firstPlayback = launch {
                                if (epoch == token) { mutable.value = ConversationState.SPEAKING; output.speak(sentence) }
                            }
                        }
                    }
                }
                var parsed = ReplyParser.parse(raw, memory.knownWords.toSet())
                if (parsed == null) {
                    var retry = ""
                    try {
                        withTimeout(45_000) { model.stream(prompt + "\nReturn valid JSON with all five fields; no markdown.").collect { require(retry.length + it.length <= 8192); retry += it } }
                        parsed = ReplyParser.parse(retry, memory.knownWords.toSet())
                    } catch (timeout: TimeoutCancellationException) {
                        // A malformed response still gets a bounded safe fallback.
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (error: Exception) { /* Safe fallback below. */ }
                }
                val reply = parsed ?: ReplyParser.fallback()
                firstPlayback?.join()
                if (token != epoch) return@launch
                repliesMutable.emit(reply)
                val remaining = if (first != null && reply.reply_ko.startsWith(first!!)) reply.reply_ko.removePrefix(first!!).trim() else if (first != null) ReplyParser.firstCompleteSentence(reply.reply_ko) else reply.reply_ko
                if (remaining.isNotBlank()) { mutable.value = ConversationState.SPEAKING; output.speak(remaining) }
            } catch (timeout: TimeoutCancellationException) {
                if (epoch == token) { output.stop(); errorsMutable.emit("The model took too long. Please try a shorter phrase.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (epoch == token) errorsMutable.emit(error.message ?: "Voice session interrupted")
            } finally {
                pcm.fill(0)
                if (epoch == token && mutable.value != ConversationState.IDLE) mutable.value = ConversationState.LISTENING
            }
        }
        turn?.invokeOnCompletion { pcm.fill(0) }
    }
    fun stop() {
        epoch++; turn?.cancel(); turn = null; output.stop(); mutable.value = ConversationState.IDLE
    }
}
