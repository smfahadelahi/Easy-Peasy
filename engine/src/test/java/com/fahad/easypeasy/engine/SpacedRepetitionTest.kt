package com.fahad.easypeasy.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SpacedRepetitionTest {
    @Test fun remembersAtOneThenSixDays() {
        val first = SpacedRepetition.review(SpacedRepetition.Schedule(), true, 0)
        assertEquals(1, first.intervalDays)
        assertEquals(6, SpacedRepetition.review(first, true, 0).intervalDays)
    }
    @Test fun lapseReturnsInTenMinutesAndEaseIsBounded() {
        val next = SpacedRepetition.review(SpacedRepetition.Schedule(ease = 1.3), false, 100)
        assertEquals(600100L, next.dueAt); assertEquals(1.3, next.ease, 0.001)
    }
    @Test fun streakAllowsUnfinishedTodayButNotGaps() {
        assertEquals(2, SpacedRepetition.streak(setOf("2026-10-14", "2026-10-15"), LocalDate.parse("2026-10-16")))
        assertEquals(0, SpacedRepetition.streak(setOf("2026-10-14"), LocalDate.parse("2026-10-16")))
    }
}
