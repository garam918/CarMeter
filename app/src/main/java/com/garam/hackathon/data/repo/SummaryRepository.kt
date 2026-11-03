package com.garam.hackathon.data.repo

import com.garam.hackathon.aggregate.DailySummary
import com.garam.hackathon.aggregate.ReportItem
import com.garam.hackathon.data.db.dao.SummaryDao
import com.garam.hackathon.data.db.entity.DailySummaryEntity
import java.time.LocalDate

/** Cached daily summaries of finished days. Raw intervals stay the source of truth. */
interface SummaryRepository {
    suspend fun between(from: LocalDate, to: LocalDate): List<DailySummary>

    /** Saves the summaries of [dates], including days with no use so they are not recomputed. */
    suspend fun save(dates: Collection<LocalDate>, summaries: List<DailySummary>)

    /** Dates in [from, to] already summarized. */
    suspend fun summarizedDates(from: LocalDate, to: LocalDate): Set<LocalDate>
}

class RoomSummaryRepository(private val dao: SummaryDao) : SummaryRepository {
    override suspend fun between(from: LocalDate, to: LocalDate): List<DailySummary> =
        dao.between(from.toString(), to.toString()).mapNotNull { row ->
            if (row.itemKey == NO_USE) return@mapNotNull null
            val item = ReportItem.entries.firstOrNull { it.name == row.itemKey } ?: return@mapNotNull null
            DailySummary(LocalDate.parse(row.date), item, row.onSec, row.count, row.avgLevel)
        }

    override suspend fun save(dates: Collection<LocalDate>, summaries: List<DailySummary>) {
        val rows = summaries.map { DailySummaryEntity(it.date.toString(), it.item.name, it.onSec, it.count, it.avgLevel) }
        val emptyDays = dates - summaries.mapTo(mutableSetOf()) { it.date }
        dao.upsertAll(rows + emptyDays.map { DailySummaryEntity(it.toString(), NO_USE, 0, 0, null) })
    }

    override suspend fun summarizedDates(from: LocalDate, to: LocalDate): Set<LocalDate> =
        dao.between(from.toString(), to.toString()).mapTo(mutableSetOf()) { LocalDate.parse(it.date) }

    private companion object {
        /** Marks a summarized day that had no use, so it is not computed again. */
        const val NO_USE = "_NONE"
    }
}
