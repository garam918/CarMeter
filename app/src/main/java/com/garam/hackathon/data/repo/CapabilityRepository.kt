package com.garam.hackathon.data.repo

import com.garam.hackathon.data.db.dao.CapabilityDao
import com.garam.hackathon.data.db.entity.CapabilitySnapshotEntity
import com.garam.hackathon.vehicle.CapabilityResult
import com.garam.hackathon.vehicle.FeatureKey
import com.garam.hackathon.vehicle.ZoneRange

/**
 * Which features this vehicle supports, saved on first run and after each app update.
 * Reports build their rows from it, so unsupported features never appear.
 */
interface CapabilityRepository {
    /** The app version that wrote the snapshot, or null if there is none. */
    suspend fun storedVersion(): Long?

    suspend fun replace(results: Map<FeatureKey, CapabilityResult>, appVersionCode: Long, checkedAt: Long)

    suspend fun load(): Map<FeatureKey, CapabilityResult>
}

class RoomCapabilityRepository(private val dao: CapabilityDao) : CapabilityRepository {
    override suspend fun storedVersion(): Long? = dao.storedVersion()

    override suspend fun replace(results: Map<FeatureKey, CapabilityResult>, appVersionCode: Long, checkedAt: Long) {
        val rows = results.flatMap { (key, result) ->
            if (result.supported) {
                result.zones.map { zone ->
                    CapabilitySnapshotEntity(key.name, zone.zone, true, zone.min, zone.max, null, checkedAt, appVersionCode)
                }
            } else {
                listOf(
                    CapabilitySnapshotEntity(
                        key.name, CapabilitySnapshotEntity.UNSUPPORTED_ZONE, false, null, null, result.error, checkedAt, appVersionCode,
                    ),
                )
            }
        }
        dao.replaceAll(rows)
    }

    override suspend fun load(): Map<FeatureKey, CapabilityResult> =
        dao.all().groupBy { it.featureKey }.mapNotNull { (name, rows) ->
            val key = featureKeyOf(name) ?: return@mapNotNull null
            val supportedRows = rows.filter { it.supported }
            key to CapabilityResult(
                supported = supportedRows.isNotEmpty(),
                zones = supportedRows.map { ZoneRange(it.zone, it.minLevel, it.maxLevel) },
                error = rows.firstNotNullOfOrNull { it.error },
            )
        }.toMap()
}
