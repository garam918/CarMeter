package com.garam.hackathon.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.garam.hackathon.data.db.entity.AppStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppStateDao {
    @Query("SELECT value FROM app_state WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM app_state WHERE `key` IN (:keys)")
    fun observe(keys: List<String>): Flow<List<AppStateEntity>>

    @Upsert
    suspend fun put(entry: AppStateEntity)

    @Query("DELETE FROM app_state WHERE `key` IN (:keys)")
    suspend fun delete(keys: List<String>)
}
