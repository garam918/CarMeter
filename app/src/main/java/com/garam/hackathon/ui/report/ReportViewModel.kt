package com.garam.hackathon.ui.report

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garam.hackathon.aggregate.Period
import com.garam.hackathon.aggregate.PeriodKind
import com.garam.hackathon.aggregate.ReportLoader
import com.garam.hackathon.aggregate.UsageReport
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportUiState(
    val period: Period,
    /** The last loaded report; kept on screen while a refresh runs. */
    val report: UsageReport? = null,
    /** This month's report, for the driving summary, whatever period is selected. */
    val monthReport: UsageReport? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val canShowNext: Boolean = false,
)

/** Holds the report for the selected day, week or month. The selection survives process death through [SavedStateHandle]. */
class ReportViewModel(private val loader: ReportLoader, private val savedState: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(ReportUiState(period = initialPeriod()))
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        val period = _state.value.period
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val today = loader.today()
            _state.update { it.copy(loading = true, failed = false, canShowNext = period.next().start <= today) }
            try {
                val report = loader.load(period)
                val thisMonth = Period.of(PeriodKind.MONTH, today)
                val monthReport = if (period == thisMonth) report else loader.load(thisMonth)
                _state.update { if (it.period == period) it.copy(report = report, monthReport = monthReport, loading = false) else it }
            } catch (e: CancellationException) {
                // A newer refresh replaced this one.
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Loading the report failed", e)
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun reloadAfterErase() {
        _state.update { it.copy(report = null, monthReport = null, loading = true, failed = false) }
        refresh()
    }

    fun showPrevious() = select(_state.value.period.previous())

    fun showNext() {
        val next = _state.value.period.next()
        if (next.start <= loader.today()) select(next)
    }

    /** Switches between day, week and month around today, or around the end of a past period. */
    fun selectKind(kind: PeriodKind) {
        val current = _state.value.period
        if (current.kind == kind) return
        val today = loader.today()
        select(Period.of(kind, if (today in current) today else current.endInclusive))
    }

    private fun select(period: Period) {
        savedState[KEY_PERIOD] = period.serialize()
        _state.update { it.copy(period = period, report = null) }
        refresh()
    }

    private fun initialPeriod(): Period =
        savedState.get<String>(KEY_PERIOD)?.let(Period::parse) ?: Period.of(PeriodKind.MONTH, loader.today())

    private companion object {
        const val TAG = "ReportViewModel"
        const val KEY_PERIOD = "period"
    }
}
