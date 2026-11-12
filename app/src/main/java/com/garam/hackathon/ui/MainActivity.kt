package com.garam.hackathon.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.garam.hackathon.BuildConfig
import com.garam.hackathon.aggregate.RecordingRate
import com.garam.hackathon.appContainer
import com.garam.hackathon.collector.BootStartSetting
import com.garam.hackathon.collector.CollectorController
import com.garam.hackathon.collector.CollectorStatus
import com.garam.hackathon.data.repo.CollectorSettings
import com.garam.hackathon.demo.Demo
import com.garam.hackathon.ui.onboarding.OnboardingScreen
import com.garam.hackathon.ui.report.HeaderActions
import com.garam.hackathon.ui.report.ReportExtras
import com.garam.hackathon.ui.report.ReportScreen
import com.garam.hackathon.ui.report.ReportViewModel
import com.garam.hackathon.ui.settings.SettingsScreen
import com.garam.hackathon.ui.settings.SettingsViewModel
import com.garam.hackathon.ui.theme.CarMeterTheme
import com.garam.hackathon.vehicle.driving.DrivingRestrictionMonitor
import kotlinx.coroutines.launch

private enum class Screen { REPORT, SETTINGS, GUIDE, CONSENT, DEBUG }

class MainActivity : ComponentActivity() {
    private val reportViewModel: ReportViewModel by viewModels {
        viewModelFactory { initializer { ReportViewModel(appContainer.reportLoader, createSavedStateHandle()) } }
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        viewModelFactory {
            initializer { SettingsViewModel(application, appContainer.settingsRepository, appContainer.collectorEngine.status) }
        }
    }

    private val drivingRestriction by lazy { DrivingRestrictionMonitor(this) }

    // Whatever the driver answers, collection starts; the answer only decides whether the
    // "recording" notification is visible.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        CollectorController.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(drivingRestriction)
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                if (appContainer.settingsRepository.current().canRecord) CollectorController.start(this@MainActivity)
            }
        }

        setContent {
            CarMeterTheme {
                val isDriving by drivingRestriction.requiresDistractionOptimization.collectAsStateWithLifecycle()
                val settings by remember { appContainer.settingsRepository.settings }.collectAsStateWithLifecycle(initialValue = null)
                val engineStatus by appContainer.collectorEngine.status.collectAsStateWithLifecycle()
                var screen by rememberSaveable { mutableStateOf(Screen.REPORT) }
                var consentPostponed by rememberSaveable { mutableStateOf(false) }

                BackHandler(enabled = screen != Screen.REPORT && !isDriving) {
                    screen = if (screen == Screen.GUIDE) Screen.SETTINGS else Screen.REPORT
                }

                val current: CollectorSettings? = settings
                when {
                    // Avoids flashing the consent screen before settings load.
                    current == null -> Box(Modifier.fillMaxSize())
                    // Driving: only the three-number summary, whatever screen was open.
                    isDriving -> Report(isDriving = true, header = headerActions(current, engineStatus) {}, extras = ReportExtras())
                    screen == Screen.CONSENT || (!current.hasConsent && !consentPostponed) -> OnboardingScreen(
                        onAgree = {
                            screen = Screen.REPORT
                            agree()
                        },
                        onDismiss = {
                            consentPostponed = true
                            screen = Screen.REPORT
                        },
                    )
                    screen == Screen.GUIDE -> OnboardingScreen(onAgree = null, onDismiss = { screen = Screen.SETTINGS })
                    screen == Screen.SETTINGS -> Settings(
                        onBack = { screen = Screen.REPORT },
                        onShowGuide = { screen = Screen.GUIDE },
                        // Back to the first-run consent screen, as after a fresh install.
                        onWithdrawn = {
                            consentPostponed = false
                            reportViewModel.reloadAfterErase()
                            screen = Screen.REPORT
                        },
                    )
                    screen == Screen.DEBUG && BuildConfig.DEBUG -> DebugRoute(onBack = { screen = Screen.REPORT })
                    else -> Report(
                        isDriving = false,
                        header = headerActions(current, engineStatus) { screen = it },
                        extras = ReportExtras(
                            onOpenDebug = if (BuildConfig.DEBUG) ({ screen = Screen.DEBUG }) else null,
                            onLoadDemo = if (Demo.IS_AVAILABLE) ::loadDemo else null,
                            demoLoading = demoLoading.value,
                            demoMessage = demoMessage.value,
                        ),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Write rows still waiting for the 60-second flush, so today's numbers are current.
        lifecycleScope.launch {
            appContainer.collectorEngine.flush()
            reportViewModel.refresh()
        }
    }

    private fun agree() {
        lifecycleScope.launch {
            appContainer.settingsRepository.giveConsent(appContainer.clock.now())
            val needsAsking = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsAsking) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else CollectorController.start(this@MainActivity)
        }
    }

    private fun headerActions(
        settings: CollectorSettings,
        status: CollectorStatus,
        navigate: (Screen) -> Unit,
    ) = HeaderActions(
        indicator = RecordingIndicator.of(settings, status),
        onPrevious = reportViewModel::showPrevious,
        onNext = reportViewModel::showNext,
        onSelectKind = reportViewModel::selectKind,
        onOpenSettings = { navigate(Screen.SETTINGS) },
        onIndicatorClick = { navigate(if (settings.hasConsent) Screen.SETTINGS else Screen.CONSENT) },
    )

    @Composable
    private fun Report(isDriving: Boolean, header: HeaderActions, extras: ReportExtras) {
        val state by reportViewModel.state.collectAsStateWithLifecycle()
        ReportScreen(state = state, isDriving = isDriving, header = header, onRetry = reportViewModel::refresh, extras = extras)
    }

    private val demoLoading = mutableStateOf(false)
    private val demoMessage = mutableStateOf<String?>(null)

    /** Demo flavor only: replaces the records with synthetic months, then reloads the bill. */
    private fun loadDemo() {
        if (demoLoading.value) return
        demoLoading.value = true
        lifecycleScope.launch {
            demoMessage.value = try {
                Demo.loadSyntheticMonths(this@MainActivity)
            } catch (e: Exception) {
                "합성 데이터를 만들지 못했어요."
            } finally {
                demoLoading.value = false
            }
            reportViewModel.refresh()
        }
    }

    @Composable
    private fun Settings(onBack: () -> Unit, onShowGuide: () -> Unit, onWithdrawn: () -> Unit) {
        val state by settingsViewModel.state.collectAsStateWithLifecycle()
        SettingsScreen(
            state = state,
            onBack = onBack,
            onRecordingChange = settingsViewModel::setRecordingEnabled,
            onCategoryChange = settingsViewModel::setCategoryEnabled,
            onErase = { settingsViewModel.eraseAllData(reportViewModel::reloadAfterErase) },
            onWithdraw = { settingsViewModel.withdrawConsent(onWithdrawn) },
            onMessageShown = settingsViewModel::messageShown,
            onShowGuide = onShowGuide,
        )
    }

    @Composable
    private fun DebugRoute(onBack: () -> Unit) {
        val container = appContainer
        var bootStart by remember { mutableStateOf(if (BootStartSetting.isAvailable(this)) BootStartSetting.isEnabled(this) else null) }
        val status by container.collectorEngine.status.collectAsStateWithLifecycle()
        val intervals by remember { container.usageRepository.recentIntervals(RECENT_LIMIT) }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val counters by remember { container.usageRepository.recentCounters(RECENT_LIMIT) }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val trips by remember { container.tripRepository.recentTrips(RECENT_TRIPS) }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        // Recomputed when saved rows change or collection restarts (a restart adds a reading).
        val recordingRate by produceState<RecordingRate?>(null, intervals, trips, status) {
            val now = container.clock.now()
            value = container.recordingRateQuery.recordingRate(now - DAY_MS, now)
        }
        CollectorDebugScreen(
            data = DebugData(status, intervals, counters, trips, recordingRate, bootStart),
            onBack = onBack,
            onStart = { CollectorController.start(this) },
            onStop = { CollectorController.stop(this) },
            onBootStartChange = { enabled ->
                BootStartSetting.setEnabled(this, enabled)
                bootStart = BootStartSetting.isEnabled(this)
            },
        )
    }

    private companion object {
        const val RECENT_LIMIT = 30
        const val RECENT_TRIPS = 5
        const val DAY_MS = 24 * 60 * 60_000L
    }
}
