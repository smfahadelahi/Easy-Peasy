package com.fahad.easypeasy.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions")
data class SessionEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val missionId: String, val startedAt: Long, val durationSeconds: Long, val day: String, val summary: String, val demo: Boolean)
@Entity(tableName = "words")
data class WordEntity(@PrimaryKey val ko: String, val bn: String, val romanization: String, val shownAt: Long, val dueAt: Long, val intervalDays: Int = 0, val repetitions: Int = 0, val ease: Double = 2.5)
@Entity(tableName = "mistakes", indices = [Index("sessionId")], foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)])
data class MistakeEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val sessionId: Long, val original: String, val correction: String)
@Entity(tableName = "streaks")
data class StreakEntity(@PrimaryKey val day: String, val sessionCount: Int)

@Dao
interface LearningDao {
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC") fun sessions(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM words ORDER BY dueAt ASC") fun words(): Flow<List<WordEntity>>
    @Query("SELECT * FROM streaks ORDER BY day DESC") fun streaks(): Flow<List<StreakEntity>>
    @Query("SELECT * FROM words WHERE ko = :ko") suspend fun word(ko: String): WordEntity?
    @Query("SELECT * FROM words ORDER BY shownAt DESC LIMIT 40") suspend fun knownWords(): List<WordEntity>
    @Query("SELECT summary FROM sessions ORDER BY startedAt DESC LIMIT 1") suspend fun lastSummary(): String?
    @Query("SELECT * FROM mistakes WHERE sessionId = :sessionId") suspend fun mistakes(sessionId: Long): List<MistakeEntity>
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC") suspend fun allSessions(): List<SessionEntity>
    @Query("SELECT * FROM words ORDER BY ko") suspend fun allWords(): List<WordEntity>
    @Query("SELECT * FROM mistakes") suspend fun allMistakes(): List<MistakeEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun showWord(word: WordEntity)
    @Update suspend fun updateWord(word: WordEntity)
    @Insert suspend fun insertSession(session: SessionEntity): Long
    @Insert suspend fun insertMistakes(mistakes: List<MistakeEntity>)
    @Query("INSERT INTO streaks(day, sessionCount) VALUES(:day, 1) ON CONFLICT(day) DO UPDATE SET sessionCount = sessionCount + 1") suspend fun incrementStreak(day: String)
    @Query("DELETE FROM mistakes") suspend fun clearMistakes()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM words") suspend fun clearWords()
    @Query("DELETE FROM streaks") suspend fun clearStreaks()
    @Transaction suspend fun saveSession(session: SessionEntity, corrections: List<Pair<String, String>> = emptyList()): Long {
        val id = insertSession(session)
        insertMistakes(corrections.map { MistakeEntity(sessionId = id, original = it.first, correction = it.second) })
        incrementStreak(session.day)
        return id
    }
    @Transaction suspend fun reset() { clearMistakes(); clearSessions(); clearWords(); clearStreaks() }
}
@Database(entities = [SessionEntity::class, WordEntity::class, MistakeEntity::class, StreakEntity::class], version = 1, exportSchema = true)
abstract class LearningDatabase : RoomDatabase() { abstract fun learningDao(): LearningDao }
