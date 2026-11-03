package com.garam.hackathon.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.garam.hackathon.data.db.entity.CapabilitySnapshotEntity

@Dao
interface CapabilityDao {
    @Query("SELECT * FROM capability_snapshot")
    suspend fun all(): List<CapabilitySnapshotEntity>

    @Query("SELECT MAX(appVersionCode) FROM capability_snapshot")
    suspend fun storedVersion(): Long?

    @Query("DELETE FROM capability_snapshot")
    suspend fun clear()

    @Insert
    suspend fun insertAll(rows: List<CapabilitySnapshotEntity>)

    @Transaction
    suspend fun replaceAll(rows: List<CapabilitySnapshotEntity>) {
        clear()
        insertAll(rows)
    }
}
