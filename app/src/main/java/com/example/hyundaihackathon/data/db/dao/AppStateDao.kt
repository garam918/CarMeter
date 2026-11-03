package com.example.hyundaihackathon.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.hyundaihackathon.data.db.entity.AppStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppStateDao {
    @Query("SELECT value FROM app_state WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM app_state WHERE `key` IN (:keys)")
    fun observe(keys: List<String>): Flow<List<AppStateEntity>>

    @Upsert
    suspend fun put(entry: AppStateEntity)
}
