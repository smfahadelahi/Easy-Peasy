package com.fahad.easypeasy.ui

import com.fahad.easypeasy.data.Chapter

data class WordView(val ko: String, val bn: String, val romanization: String, val due: Boolean = true)
data class SessionView(val id: Long, val missionId: String, val minutes: Int, val summary: String, val day: String)
data class Bubble(val text: String, val learner: Boolean = false)
@kotlinx.serialization.Serializable
data class Preferences(
    val onboarded: Boolean = false, val bangla: Boolean = false, val silence: Float = 3.5f,
    val speed: Float = 0.85f, val dailyMinutes: Int = 10, val romanization: Boolean = true,
    val hints: Boolean = true, val dark: Boolean = false, val reduceMotion: Boolean = false,
    val wifiOnly: Boolean = true, val reminderHour: Int = 20,
)
data class AppState(
    val loading: Boolean = true, val error: String? = null,
    val chapters: List<Chapter> = emptyList(), val preferences: Preferences = Preferences(),
    val sessions: List<SessionView> = emptyList(), val words: List<WordView> = emptyList(),
    val bubbles: List<Bubble> = emptyList(), val selectedMission: String = "m01",
    val hint: String = "", val phase: String = "IDLE", val muted: Boolean = false,
    val downloadState: String = "NOT_DOWNLOADED", val downloadedBytes: Long = 0, val downloadError: String = "",
    val memoryGb: Int = 0, val storageGb: Long = 0, val streak: Int = 0,
) {
    val missions get() = chapters.flatMap { it.missions }
    val mission get() = missions.firstOrNull { it.id == selectedMission } ?: missions.firstOrNull()
    val completed get() = sessions.map { it.missionId }.toSet()
}
