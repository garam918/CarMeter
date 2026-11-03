package com.garam.hackathon.data

import com.garam.hackathon.collector.Clock
import com.garam.hackathon.data.repo.RetentionRepository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Keeps the current month and the twelve before it, as the consent screen says. Runs when
 * the process starts, at most once a day, so it needs no timer of its own.
 */
class DataRetention(
    private val repository: RetentionRepository,
    private val clock: Clock,
    private val timeZone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    /** Deletes old records unless that already happened today. Returns whether it ran. */
    suspend fun purgeIfDue(): Boolean {
        val zone = timeZone()
        val today = Instant.ofEpochMilli(clock.now()).atZone(zone).toLocalDate()
        if (repository.lastPurgedOn() == today) return false
        val cutoff = cutoff(today)
        repository.deleteBefore(cutoff.atStartOfDay(zone).toInstant().toEpochMilli(), cutoff)
        repository.setLastPurgedOn(today)
        return true
    }

    companion object {
        const val MONTHS_KEPT = 13

        /** The first day kept: the first of the month twelve months before [today]'s. */
        fun cutoff(today: LocalDate): LocalDate = YearMonth.from(today).minusMonths(MONTHS_KEPT - 1L).atDay(1)
    }
}
