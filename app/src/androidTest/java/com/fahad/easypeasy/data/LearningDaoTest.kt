package com.fahad.easypeasy.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearningDaoTest {
    private lateinit var db: LearningDatabase
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LearningDatabase::class.java).build()
    }
    @After fun close() { db.close() }
    @Test fun shownWordIsNotOverwrittenAndResetIsComplete() = runTest {
        val dao = db.learningDao()
        val word = WordEntity("안녕", "হ্যালো", "annyeong", 1, 2)
        dao.showWord(word); dao.showWord(word.copy(dueAt = 100))
        assertEquals(2L, dao.word("안녕")!!.dueAt)
        val id = dao.saveSession(SessionEntity(missionId = "m01", startedAt = 1, durationSeconds = 60, day = "2026-10-16", summary = "demo", demo = true), listOf("x" to "y"))
        assertEquals("y", dao.mistakes(id).single().correction)
        assertEquals("demo", dao.lastSummary())
        dao.reset()
        assertTrue(dao.allSessions().isEmpty()); assertTrue(dao.allWords().isEmpty()); assertTrue(dao.allMistakes().isEmpty())
    }
    @Test fun unknownWordCannotBeReviewed() = runTest { assertNull(db.learningDao().word("unknown")) }
}
