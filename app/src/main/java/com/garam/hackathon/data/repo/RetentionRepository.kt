package com.garam.hackathon.data.repo

import com.garam.hackathon.data.db.dao.AppStateDao
import com.garam.hackathon.data.db.dao.EraseDao
import com.garam.hackathon.data.db.entity.AppStateEntity
import java.time.LocalDate

/** Deletes usage records older than the retention period, and remembers when it last did. */
interface RetentionRepository {
    suspend fun lastPurgedOn(): LocalDate?

    suspend fun setLastPurgedOn(date: LocalDate)

    /** Deletes records that ended before [cutoffTs]; daily summaries before [cutoffDate]. */
    suspend fun deleteBefore(cutoffTs: Long, cutoffDate: LocalDate)
}

class RoomRetentionRepository(private val eraseDao: EraseDao, private val appStateDao: AppStateDao) : RetentionRepository {
    override suspend fun lastPurgedOn(): LocalDate? =
        appStateDao.get(KEY_PURGED_ON)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    override suspend fun setLastPurgedOn(date: LocalDate) = appStateDao.put(AppStateEntity(KEY_PURGED_ON, date.toString()))

    override suspend fun deleteBefore(cutoffTs: Long, cutoffDate: LocalDate) = eraseDao.deleteUsageBefore(cutoffTs, cutoffDate.toString())

    private companion object {
        const val KEY_PURGED_ON = "retentionPurgedOn"
    }
}
