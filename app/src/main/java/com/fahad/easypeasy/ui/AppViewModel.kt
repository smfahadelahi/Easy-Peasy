package com.fahad.easypeasy.ui

import android.app.ActivityManager
import android.app.Application
import android.os.StatFs
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
class AppViewModel @Inject constructor(private val app: Application) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    init { reload() }
    fun reload() {
        viewModelScope.launch {
            runCatching { Lessons.load(app) }.onSuccess { chapters ->
                val memory = ActivityManager.MemoryInfo()
                app.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
                mutable.update { it.copy(loading = false, error = null, chapters = chapters, memoryGb = ((memory.totalMem + (1L shl 29)) / (1L shl 30)).toInt(), storageGb = StatFs(app.filesDir.path).availableBytes / (1L shl 30)) }
            }.onFailure { e -> mutable.update { it.copy(loading = false, error = e.message) } }
        }
    }
    fun preferences(p: Preferences) { mutable.update { it.copy(preferences = p) } }
    fun selectMission(id: String) { mutable.update { it.copy(selectedMission = id, bubbles = emptyList()) } }
    fun demoReply() {
        val mission = state.value.mission ?: return
        mutable.update { it.copy(bubbles = it.bubbles + Bubble(mission.ko, true) + Bubble(mission.ko), hint = mission.bn) }
    }
    fun replay(text: String) { /* Real offline Android TTS is connected in stage 4. */ }
    fun mute() { mutable.update { it.copy(muted = !it.muted) } }
    fun finishSession() { }
    fun gradeWord(ko: String, remembered: Boolean) { }
    fun download() { }
    fun pauseDownload() { }
    fun startLive() { }
    fun reset() { mutable.value = AppState(loading = false, chapters = state.value.chapters) }
    fun exportData() { }
}
