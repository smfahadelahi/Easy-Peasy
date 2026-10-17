package com.fahad.easypeasy.ui

import android.app.ActivityManager
import android.app.Application
import android.net.Uri
import android.os.StatFs
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fahad.easypeasy.R
import com.fahad.easypeasy.audio.AndroidSpeechOutput
import com.fahad.easypeasy.data.*
import com.fahad.easypeasy.engine.ModelCatalog
import com.fahad.easypeasy.engine.SpacedRepetition
import com.fahad.easypeasy.models.ModelDownloads
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

@HiltViewModel
class AppViewModel @Inject constructor(
    private val app: Application,
    private val dao: LearningDao,
    private val store: PreferenceStore,
    private val savedState: SavedStateHandle,
    private val output: AndroidSpeechOutput,
    private val downloads: ModelDownloads,
) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private var sessionStarted = SystemClock.elapsedRealtime()
    private var practiced = false
    private var replayJob: Job? = null
    private var loadJob: Job? = null
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        // Serialize preference writes and reset; old slider writes must never resurrect a reset profile.
        viewModelScope.launch {
            for (command in commands) guarded { command() }
        }
        viewModelScope.launch {
            downloads.status.catch { notifyFailure(it) }.collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                mutable.update {
                    it.copy(
                        downloadState = info.state.name,
                        downloadedBytes = if (info.state == androidx.work.WorkInfo.State.SUCCEEDED) ModelCatalog.silero.bytes else info.progress.getLong("bytes", 0),
                        downloadError = info.outputData.getString("error").orEmpty(),
                    )
                }
            }
        }
        viewModelScope.launch {
            val clock = flow {
                while (currentCoroutineContext().isActive) { emit(System.currentTimeMillis()); delay(30_000) }
            }
            combine(dao.sessions(), dao.words(), dao.streaks(), clock) { sessions, words, streaks, now ->
                AppState(
                    sessions = sessions.map { SessionView(it.id, it.missionId, (it.durationSeconds / 60).toInt(), it.summary, it.day) },
                    words = words.map { WordView(it.ko, it.bn, it.romanization, it.dueAt <= now) },
                    streak = SpacedRepetition.streak(streaks.map { it.day }.toSet(), LocalDate.now()),
                )
            }.catch { notifyFailure(it) }.collect { learning ->
                mutable.update { it.copy(sessions = learning.sessions, words = learning.words, streak = learning.streak) }
            }
        }
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            try {
                val chapters = Lessons.load(app)
                val profile = withContext(Dispatchers.IO) { store.read() }
                val memory = ActivityManager.MemoryInfo()
                app.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
                val freeBytes = withContext(Dispatchers.IO) { StatFs(app.filesDir.path).availableBytes }
                mutable.update {
                    it.copy(
                        loading = false, error = null, chapters = chapters, preferences = profile,
                        selectedMission = savedState["mission"] ?: "m01",
                        memoryGb = ((memory.totalMem + (1L shl 29)) / (1L shl 30)).toInt(),
                        storageGb = freeBytes / (1L shl 30),
                        recommendedModel = ModelCatalog.recommended(memory.totalMem).label,
                    )
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) { mutable.update { it.copy(loading = false, error = error.message ?: "Unable to load lessons") } }
        }
    }

    fun preferences(profile: Preferences) {
        val validated = profile.copy(
            silence = profile.silence.coerceIn(1f, 6f), speed = profile.speed.coerceIn(0.5f, 1.2f),
            dailyMinutes = profile.dailyMinutes.coerceIn(5, 30), reminderHour = profile.reminderHour.coerceIn(0, 23),
        )
        mutable.update { it.copy(preferences = validated) }
        commands.trySend {
            store.write(validated)
            ReminderWorker.schedule(app, validated.reminderHour, validated.reminderEnabled)
        }
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try { block() } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) { notifyFailure(error) }
    }
    private fun notifyFailure(error: Throwable) {
        if (error is CancellationException) throw error
        mutable.update { it.copy(notice = error.message ?: "Local operation failed") }
    }
    fun dismissNotice() { mutable.update { it.copy(notice = null) } }

    fun selectMission(id: String) {
        if (state.value.missions.none { it.id == id }) return
        stopReplay()
        savedState["mission"] = id
        sessionStarted = SystemClock.elapsedRealtime()
        practiced = false
        mutable.update { it.copy(selectedMission = id, bubbles = emptyList(), hint = "", phase = "IDLE") }
        meetWord()
    }
    private fun meetWord() {
        val mission = state.value.mission ?: return
        commands.trySend {
            val now = System.currentTimeMillis()
            dao.showWord(WordEntity(mission.ko, mission.bn, mission.romanization, now, now))
        }
    }
    fun demoReply() {
        val mission = state.value.mission ?: return
        meetWord()
        practiced = true
        mutable.update { it.copy(bubbles = (it.bubbles + Bubble(mission.ko, true) + Bubble(mission.ko)).takeLast(100), hint = mission.bn) }
    }

    fun replay(text: String) {
        if (text.isBlank()) return
        stopReplay()
        replayJob = viewModelScope.launch {
            try {
                output.speed = state.value.preferences.speed
                mutable.update { it.copy(phase = "SPEAKING") }
                output.speak(text)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) { mutable.update { it.copy(notice = app.getString(R.string.tts_error)) }
            } finally { mutable.update { it.copy(phase = "IDLE") } }
        }
    }
    private fun stopReplay() { replayJob?.cancel(); replayJob = null; output.stop() }
    fun mute() {
        // Demo never opens a microphone. This switch stops replay, not a fictitious live session.
        stopReplay()
        mutable.update { it.copy(muted = !it.muted) }
    }

    fun finishSession() {
        stopReplay()
        if (!practiced) return
        practiced = false
        val mission = state.value.mission ?: return
        val duration = (SystemClock.elapsedRealtime() - sessionStarted).coerceAtLeast(0) / 1000
        commands.trySend {
            dao.saveSession(SessionEntity(
                missionId = mission.id, startedAt = System.currentTimeMillis() - duration * 1000,
                durationSeconds = duration, day = LocalDate.now().toString(),
                summary = "Demo practice: ${mission.ko}", demo = true,
            ))
        }
    }
    fun gradeWord(ko: String, remembered: Boolean) {
        commands.trySend { dao.reviewWord(ko, remembered, System.currentTimeMillis()) }
    }
    fun download() { downloads.start(state.value.preferences.wifiOnly) }
    fun pauseDownload() { downloads.pause() }
    fun startLive() { mutable.update { it.copy(notice = app.getString(R.string.missing_models)) } }

    fun reset() {
        stopReplay()
        practiced = false
        commands.trySend {
            dao.reset()
            store.write(Preferences())
            ReminderWorker.schedule(app, 20, false)
            savedState["mission"] = "m01"
            mutable.update {
                it.copy(preferences = Preferences(), selectedMission = "m01", sessions = emptyList(), words = emptyList(), bubbles = emptyList(), hint = "", streak = 0)
            }
        }
    }

    fun exportData(uri: Uri) {
        // Queue behind pending writes. Snapshot is transactional and the file is written off Main.
        commands.trySend {
            val snapshot = dao.exportSnapshot()
            val profile = state.value.preferences
            withContext(Dispatchers.IO) {
                val json = buildJsonObject {
                    put("formatVersion", 1)
                    put("sessions", buildJsonArray { snapshot.sessions.forEach { row -> add(buildJsonObject {
                        put("id", row.id); put("mission", row.missionId); put("day", row.day); put("startedAt", row.startedAt)
                        put("durationSeconds", row.durationSeconds); put("summary", row.summary); put("demo", row.demo)
                    }) } })
                    put("vocabulary", buildJsonArray { snapshot.words.forEach { row -> add(buildJsonObject {
                        put("ko", row.ko); put("bn", row.bn); put("romanization", row.romanization); put("shownAt", row.shownAt)
                        put("dueAt", row.dueAt); put("intervalDays", row.intervalDays); put("repetitions", row.repetitions); put("ease", row.ease)
                    }) } })
                    put("mistakes", buildJsonArray { snapshot.mistakes.forEach { row -> add(buildJsonObject {
                        put("sessionId", row.sessionId); put("original", row.original); put("correction", row.correction)
                    }) } })
                    put("preferences", Json.encodeToJsonElement(Preferences.serializer(), profile))
                }
                checkNotNull(app.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(json.toString()) }
            }
            mutable.update { it.copy(notice = app.getString(R.string.saved)) }
        }
    }
    override fun onCleared() { stopReplay(); commands.close(); super.onCleared() }
}
