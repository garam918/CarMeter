package com.garam.hackathon.data.repo

import com.garam.hackathon.data.db.dao.AppStateDao
import com.garam.hackathon.data.db.entity.AppStateEntity
import com.garam.hackathon.vehicle.FeatureCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** What the driver agreed to and switched on. */
data class CollectorSettings(
    /** When the driver agreed to recording, or null before that. */
    val consentAt: Long? = null,
    /** The master switch. On by default once the driver has agreed. */
    val recordingEnabled: Boolean = true,
    val disabledCategories: Set<FeatureCategory> = emptySet(),
) {
    val hasConsent: Boolean get() = consentAt != null

    /** Collection may run only after consent and while the master switch is on. */
    val canRecord: Boolean get() = hasConsent && recordingEnabled

    fun isEnabled(category: FeatureCategory): Boolean = category == FeatureCategory.SYSTEM || category !in disabledCategories
}

interface SettingsRepository {
    val settings: Flow<CollectorSettings>

    suspend fun current(): CollectorSettings = settings.first()

    suspend fun giveConsent(at: Long)

    /** Forgets the consent and every choice made after it, back to the first-run state. */
    suspend fun withdrawConsent()

    suspend fun setRecordingEnabled(enabled: Boolean)

    suspend fun setCategoryEnabled(category: FeatureCategory, enabled: Boolean)
}

/** Stores settings as rows of app_state, next to lastAlive. */
class RoomSettingsRepository(private val dao: AppStateDao) : SettingsRepository {
    override val settings: Flow<CollectorSettings> = dao.observe(KEYS)
        .map { rows ->
            val values = rows.associate { it.key to it.value }
            CollectorSettings(
                consentAt = values[KEY_CONSENT_AT]?.toLongOrNull(),
                recordingEnabled = values[KEY_RECORDING_ENABLED]?.toBooleanStrictOrNull() ?: true,
                disabledCategories = values[KEY_DISABLED_CATEGORIES].orEmpty()
                    .split(",")
                    .mapNotNullTo(mutableSetOf()) { name -> FeatureCategory.entries.firstOrNull { it.name == name } },
            )
        }
        .distinctUntilChanged()

    override suspend fun giveConsent(at: Long) = dao.put(AppStateEntity(KEY_CONSENT_AT, at.toString()))

    override suspend fun withdrawConsent() = dao.delete(KEYS)

    override suspend fun setRecordingEnabled(enabled: Boolean) = dao.put(AppStateEntity(KEY_RECORDING_ENABLED, enabled.toString()))

    override suspend fun setCategoryEnabled(category: FeatureCategory, enabled: Boolean) {
        if (category == FeatureCategory.SYSTEM) return
        val disabled = current().disabledCategories.let { if (enabled) it - category else it + category }
        dao.put(AppStateEntity(KEY_DISABLED_CATEGORIES, disabled.sortedBy { it.ordinal }.joinToString(",") { it.name }))
    }

    private companion object {
        const val KEY_CONSENT_AT = "consentAt"
        const val KEY_RECORDING_ENABLED = "recordingEnabled"
        const val KEY_DISABLED_CATEGORIES = "disabledCategories"
        val KEYS = listOf(KEY_CONSENT_AT, KEY_RECORDING_ENABLED, KEY_DISABLED_CATEGORIES)
    }
}
