package com.example.hyundaihackathon.ui.report

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hyundaihackathon.aggregate.MonthlyReport
import com.example.hyundaihackathon.aggregate.ReportLoader
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportUiState(
    val month: YearMonth,
    /** The last loaded report; kept on screen while a refresh runs. */
    val report: MonthlyReport? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val canShowNextMonth: Boolean = false,
)

/** Holds the bill for the selected month. The month survives process death through [SavedStateHandle]. */
class ReportViewModel(private val loader: ReportLoader, private val savedState: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(ReportUiState(month = initialMonth()))
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        val month = _state.value.month
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, failed = false, canShowNextMonth = month < currentMonth()) }
            try {
                val report = loader.load(month)
                _state.update { if (it.month == month) it.copy(report = report, loading = false) else it }
            } catch (e: CancellationException) {
                // A newer refresh replaced this one.
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Loading the report failed", e)
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun showPreviousMonth() = select(_state.value.month.minusMonths(1))

    fun showNextMonth() {
        val next = _state.value.month.plusMonths(1)
        if (next <= currentMonth()) select(next)
    }

    private fun select(month: YearMonth) {
        savedState[KEY_MONTH] = month.toString()
        _state.update { it.copy(month = month, report = null) }
        refresh()
    }

    private fun currentMonth(): YearMonth = YearMonth.from(loader.today())

    private fun initialMonth(): YearMonth = savedState.get<String>(KEY_MONTH)?.let(YearMonth::parse) ?: currentMonth()

    private companion object {
        const val TAG = "ReportViewModel"
        const val KEY_MONTH = "month"
    }
}
