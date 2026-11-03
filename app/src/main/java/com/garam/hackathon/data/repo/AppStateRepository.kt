package com.garam.hackathon.data.repo

import com.garam.hackathon.data.db.dao.AppStateDao
import com.garam.hackathon.data.db.entity.AppStateEntity

interface AppStateRepository {
    /** When the collector last proved it was running, in epoch milliseconds. */
    suspend fun lastAlive(): Long?

    suspend fun setLastAlive(timestamp: Long)
}

class RoomAppStateRepository(private val dao: AppStateDao) : AppStateRepository {
    override suspend fun lastAlive(): Long? = dao.get(KEY_LAST_ALIVE)?.toLongOrNull()

    override suspend fun setLastAlive(timestamp: Long) = dao.put(AppStateEntity(KEY_LAST_ALIVE, timestamp.toString()))

    private companion object {
        const val KEY_LAST_ALIVE = "lastAlive"
    }
}
