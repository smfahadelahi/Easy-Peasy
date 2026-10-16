package com.fahad.easypeasy.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationManagerTest {
    private class Stt : SpeechRecognizer {
        override val ready = true
        override suspend fun transcribe(pcm16: ShortArray, sampleRate: Int) = "안녕하세요."
    }
    private class Llm : LanguageModel {
        override val ready = true
        override fun stream(prompt: String) = flowOf("""{"reply_ko":"안녕하세요.","hint_bn":"হ্যালো","correction":"","new_word":"","step_done":true}""")
    }
    private class Speaker : SpeechOutput {
        var stops = 0
        var spoken = 0
        override suspend fun speak(text: String) { spoken++; delay(1000) }
        override fun stop() { stops++ }
    }
    @Test fun fullLoopReturnsToListening() = runTest {
        val manager = ConversationManager(this, Stt(), Llm(), Speaker()) { ConversationContext(listOf("안녕하세요."), "", "hello") }
        assertEquals(ConversationState.IDLE, manager.state.value)
        assertTrue(manager.start()); assertEquals(ConversationState.LISTENING, manager.state.value)
        manager.utterance(shortArrayOf(1)); assertEquals(ConversationState.THINKING, manager.state.value)
        runCurrent(); assertEquals(ConversationState.SPEAKING, manager.state.value)
        advanceUntilIdle(); assertEquals(ConversationState.LISTENING, manager.state.value)
        manager.stop(); assertEquals(ConversationState.IDLE, manager.state.value)
    }
    @Test fun bargeInCancelsTtsAndStaleCompletionNeverRestartsIt() = runTest {
        val speaker = Speaker()
        val manager = ConversationManager(this, Stt(), Llm(), speaker) { ConversationContext(emptyList(), "", "") }
        manager.start(); manager.utterance(shortArrayOf(1)); runCurrent()
        assertEquals(ConversationState.SPEAKING, manager.state.value)
        manager.speechStarted()
        assertEquals(1, speaker.stops); assertEquals(ConversationState.LISTENING, manager.state.value)
        advanceUntilIdle()
        assertEquals(1, speaker.spoken); assertEquals(ConversationState.LISTENING, manager.state.value)
    }
    @Test fun missingModelNeverEntersListening() = runTest {
        val fake = FakeEngine()
        val manager = ConversationManager(this, fake, fake, Speaker()) { ConversationContext(emptyList(), "", "") }
        assertFalse(manager.start()); assertEquals(ConversationState.IDLE, manager.state.value)
    }
    @Test fun stopDuringThinkingDiscardsLateReply() = runTest {
        val slow = object : LanguageModel {
            override val ready = true
            override fun stream(prompt: String) = flow { delay(1000); emit("{}") }
        }
        val manager = ConversationManager(this, Stt(), slow, Speaker()) { ConversationContext(emptyList(), "", "") }
        manager.start(); manager.utterance(shortArrayOf(1)); runCurrent(); manager.stop(); advanceUntilIdle()
        assertEquals(ConversationState.IDLE, manager.state.value)
    }
}
