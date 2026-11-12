package com.garam.hackathon.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garam.hackathon.collector.CollectorController
import com.garam.hackathon.collector.CollectorStatus
import com.garam.hackathon.data.repo.CollectorSettings
import com.garam.hackathon.data.repo.SettingsRepository
import com.garam.hackathon.vehicle.FeatureCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: CollectorSettings = CollectorSettings(),
    val status: CollectorStatus = CollectorStatus.Stopped,
    val erasing: Boolean = false,
    /** A one-time confirmation, such as after deleting data. */
    val message: String? = null,
)

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    collectorStatus: StateFlow<CollectorStatus>,
) : AndroidViewModel(application) {
    private val erasing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> =
        combine(settingsRepository.settings, collectorStatus, erasing, message) { settings, status, erasing, message ->
            SettingsUiState(settings, status, erasing, message)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun setRecordingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRecordingEnabled(enabled)
            if (enabled) CollectorController.start(getApplication()) else CollectorController.stop(getApplication())
        }
    }

    /** The engine follows the stored setting by itself while it runs. */
    fun setCategoryEnabled(category: FeatureCategory, enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCategoryEnabled(category, enabled) }
    }

    fun eraseAllData(onErased: () -> Unit) {
        if (erasing.value) return
        viewModelScope.launch {
            erasing.value = true
            try {
                CollectorController.eraseAllData(getApplication())
                onErased()
                message.value = "모든 기록을 지웠어요."
            } finally {
                erasing.value = false
            }
        }
    }

    /** Stops recording, deletes every record and forgets the consent, then calls [onDone]. */
    fun withdrawConsent(onDone: () -> Unit) {
        if (erasing.value) return
        viewModelScope.launch {
            erasing.value = true
            try {
                CollectorController.withdrawConsent(getApplication())
            } finally {
                erasing.value = false
            }
            onDone()
        }
    }

    fun messageShown() {
        message.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
