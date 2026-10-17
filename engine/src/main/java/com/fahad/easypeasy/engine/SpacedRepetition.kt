package com.fahad.easypeasy.engine

import java.time.LocalDate

/** Small, deterministic SM-2 variant. Only previously shown words enter review. */
object SpacedRepetition {
    data class Schedule(val intervalDays: Int = 0, val repetitions: Int = 0, val ease: Double = 2.5, val dueAt: Long = 0)
    fun review(previous: Schedule, remembered: Boolean, now: Long): Schedule {
        require(now >= 0) { "Review time must be non-negative" }
        if (!remembered) return Schedule(0, 0, (previous.ease - 0.2).coerceAtLeast(1.3), now + 10 * 60_000)
        val interval = when (previous.repetitions) {
            0 -> 1
            1 -> 6
            else -> (previous.intervalDays.coerceAtLeast(1) * previous.ease.coerceIn(1.3, 3.0)).toInt().coerceIn(1, 365)
        }
        return Schedule(interval, previous.repetitions + 1, (previous.ease + 0.1).coerceIn(1.3, 3.0), now + interval * 86_400_000L)
    }
    fun streak(days: Set<String>, today: LocalDate): Int {
        var cursor = if (today.toString() in days) today else today.minusDays(1)
        var count = 0
        while (cursor.toString() in days) { count++; cursor = cursor.minusDays(1) }
        return count
    }
}
