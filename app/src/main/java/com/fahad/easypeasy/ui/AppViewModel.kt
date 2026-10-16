package com.fahad.easypeasy.ui

import android.app.ActivityManager
import android.app.Application
import android.os.StatFs
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import com.fahad.easypeasy.data.*
import com.fahad.easypeasy.engine.SpacedRepetition
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fahad.easypeasy.data.Lessons
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(private val app: Application, private val dao: LearningDao, private val store: PreferenceStore, private val savedState: SavedStateHandle, private val output: com.fahad.easypeasy.audio.AndroidSpeechOutput) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private var sessionStarted = SystemClock.elapsedRealtime()
    private var practiced = false
    init {
        reload()
        viewModelScope.launch {
            combine(dao.sessions(), dao.words(), dao.streaks()) { sessions, words, streaks ->
                Triple(sessions, words, streaks)
            }.collect { (sessions, words, streaks) ->
                mutable.update { old -> old.copy(
                    sessions = sessions.map { SessionView(it.id, it.missionId, (it.durationSeconds / 60).toInt(), it.summary, it.day) },
                    words = words.map { WordView(it.ko, it.bn, it.romanization, it.dueAt <= System.currentTimeMillis()) },
                    streak = SpacedRepetition.streak(streaks.map { it.day }.toSet(), LocalDate.now()),
                ) }
            }
        }
    }
    fun reload() {
        viewModelScope.launch {
            runCatching { Lessons.load(app) }.onSuccess { chapters ->
                val preferences = withContext(Dispatchers.IO) { store.read() }
                val memory = ActivityManager.MemoryInfo()
                app.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
                mutable.update { it.copy(loading = false, error = null, chapters = chapters, preferences = preferences, selectedMission = savedState["mission"] ?: "m01", memoryGb = ((memory.totalMem + (1L shl 29)) / (1L shl 30)).toInt(), storageGb = StatFs(app.filesDir.path).availableBytes / (1L shl 30)) }
            }.onFailure { e -> mutable.update { it.copy(loading = false, error = e.message) } }
        }
    }
    fun preferences(p: Preferences) {
        mutable.update { it.copy(preferences = p) }
        viewModelScope.launch { runCatching { store.write(p) }.onFailure { fail(it) } }
    }
    private fun fail(e: Throwable) { mutable.update { it.copy(error = e.message ?: "Local operation failed") } }
    fun selectMission(id: String) {
        savedState["mission"] = id
        sessionStarted = SystemClock.elapsedRealtime(); practiced = false
        mutable.update { it.copy(selectedMission = id, bubbles = emptyList(), hint = "") }
        meetWord()
    }
    private fun meetWord() {
        val m = state.value.mission ?: return
        viewModelScope.launch { runCatching { dao.showWord(WordEntity(m.ko, m.bn, m.romanization, System.currentTimeMillis(), System.currentTimeMillis())) }.onFailure { fail(it) } }
    }
    fun demoReply() {
        val mission = state.value.mission ?: return
        meetWord(); practiced = true
        mutable.update { it.copy(bubbles = it.bubbles + Bubble(mission.ko, true) + Bubble(mission.ko), hint = mission.bn) }
    }
    fun replay(text: String) {
        viewModelScope.launch {
            try { output.stop(); output.speed = state.value.preferences.speed; output.speak(text) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.update { it.copy(hint = error.message.orEmpty()) } }
        }
    }
    fun mute() { mutable.update { it.copy(muted = !it.muted) } }
    fun finishSession() {
        output.stop()
        if (!practiced) return
        practiced = false
        val mission = state.value.mission ?: return
        val duration = (SystemClock.elapsedRealtime() - sessionStarted).coerceAtLeast(0) / 1000
        viewModelScope.launch {
            runCatching {
                dao.saveSession(SessionEntity(missionId = mission.id, startedAt = System.currentTimeMillis() - duration * 1000, durationSeconds = duration,
                    day = LocalDate.now().toString(), summary = "Demo practice: ${mission.ko}", demo = true))
            }.onFailure { fail(it) }
        }
    }
    fun gradeWord(ko: String, remembered: Boolean) {
        viewModelScope.launch {
            runCatching {
                val old = dao.word(ko) ?: return@runCatching
                val next = SpacedRepetition.review(SpacedRepetition.Schedule(old.intervalDays, old.repetitions, old.ease, old.dueAt), remembered, System.currentTimeMillis())
                dao.updateWord(old.copy(intervalDays = next.intervalDays, repetitions = next.repetitions, ease = next.ease, dueAt = next.dueAt))
            }.onFailure { fail(it) }
        }
    }
    fun download() { }
    fun pauseDownload() { }
    fun startLive() { mutable.update { it.copy(hint = "Live voice unavailable: Korean STT is not installed. This is a demo build.") } }
    override fun onCleared() { output.stop(); super.onCleared() }
    fun reset() {
        output.stop()
        viewModelScope.launch {
            runCatching {
                dao.reset(); store.write(Preferences())
                savedState["mission"] = "m01"
                mutable.update { AppState(loading = false, chapters = it.chapters, memoryGb = it.memoryGb, storageGb = it.storageGb) }
            }.onFailure { fail(it) }
        }
    }
    fun exportData(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val json = buildJsonObject {
                    put("formatVersion", 1)
                    put("sessions", buildJsonArray { dao.allSessions().forEach { row -> add(buildJsonObject {
                        put("mission", row.missionId); put("startedAt", row.startedAt); put("durationSeconds", row.durationSeconds); put("summary", row.summary); put("demo", row.demo)
                    }) } })
                    put("vocabulary", buildJsonArray { dao.allWords().forEach { row -> add(buildJsonObject {
                        put("ko", row.ko); put("bn", row.bn); put("romanization", row.romanization); put("dueAt", row.dueAt); put("intervalDays", row.intervalDays); put("repetitions", row.repetitions); put("ease", row.ease)
                    }) } })
                    put("mistakes", buildJsonArray { dao.allMistakes().forEach { row -> add(buildJsonObject { put("original", row.original); put("correction", row.correction) }) } })
                    put("preferences", Json.encodeToJsonElement(Preferences.serializer(), state.value.preferences))
                }
                checkNotNull(app.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(json.toString()) }
            }.onFailure { fail(it) }
        }
    }
}
