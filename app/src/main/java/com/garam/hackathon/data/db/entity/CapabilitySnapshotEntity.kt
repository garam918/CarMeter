package com.garam.hackathon.data.db.entity

import androidx.room.Entity

/** One row per supported zone, or one row with zone [UNSUPPORTED_ZONE] for an unsupported feature. */
@Entity(tableName = "capability_snapshot", primaryKeys = ["featureKey", "zone"])
data class CapabilitySnapshotEntity(
    val featureKey: String,
    val zone: Int,
    val supported: Boolean,
    val minLevel: Int?,
    val maxLevel: Int?,
    val error: String?,
    val checkedAt: Long,
    val appVersionCode: Long,
) {
    companion object {
        const val UNSUPPORTED_ZONE = -1
    }
}
