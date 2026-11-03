package com.example.hyundaihackathon.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.hyundaihackathon.aggregate.MonthlyReport
import com.example.hyundaihackathon.aggregate.ReportFormat
import com.example.hyundaihackathon.ui.RecordingIndicator
import java.time.LocalDate

/** Windows at least this wide get the detailed table and chart. */
private val WideWidth = 1000.dp

/** The minimum touch target for in-car buttons. */
internal val TouchTarget = 64.dp

/**
 * The monthly bill. While driving only three large numbers are shown, with no scrolling
 * and nothing to tap. When parked, narrow windows get summary cards and wide windows get
 * the detailed table and bar chart.
 */
@Composable
fun ReportScreen(
    state: ReportUiState,
    isDriving: Boolean,
    header: HeaderActions,
    onRetry: () -> Unit,
    extras: ReportExtras,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        val report = state.report
        when {
            isDriving -> DrivingSummary(report)
            report == null && state.failed -> Message("고지서를 만들지 못했어요.", action = "다시 시도", onAction = onRetry)
            report == null -> Loading()
            else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                if (maxWidth >= WideWidth) {
                    ReportWide(report, state, header, extras)
                } else {
                    ReportCompact(report, state, header, extras)
                }
            }
        }
    }
}

/** Buttons only some builds show: the debug collection screen and the demo data loader. */
data class ReportExtras(
    val onOpenDebug: (() -> Unit)? = null,
    val onLoadDemo: (() -> Unit)? = null,
    val demoLoading: Boolean = false,
    val demoMessage: String? = null,
)

/** What the header shows besides the report, and what its buttons do. */
data class HeaderActions(
    val indicator: RecordingIndicator,
    val onPreviousMonth: () -> Unit,
    val onNextMonth: () -> Unit,
    val onOpenSettings: () -> Unit,
    /** Tapping the recording state: opens consent when it is missing, settings otherwise. */
    val onIndicatorClick: () -> Unit,
)

/** Month, period, navigation, the recording rate and the recording state. Shared by both parked layouts. */
@Composable
internal fun ReportHeader(report: MonthlyReport, state: ReportUiState, actions: HeaderActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = actions.onPreviousMonth,
                modifier = Modifier.heightIn(min = TouchTarget).semantics { contentDescription = "이전 달" },
            ) { Text("‹") }
            Text("${report.month.monthValue}월 고지서", style = MaterialTheme.typography.headlineMedium)
            OutlinedButton(
                onClick = actions.onNextMonth,
                enabled = state.canShowNextMonth,
                modifier = Modifier.heightIn(min = TouchTarget).semantics { contentDescription = "다음 달" },
            ) { Text("›") }
            if (state.loading) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = actions.onOpenSettings, modifier = Modifier.heightIn(min = TouchTarget)) { Text("설정") }
        }
        TextButton(onClick = actions.onIndicatorClick, modifier = Modifier.heightIn(min = TouchTarget)) {
            val color = if (actions.indicator.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            Text("● ${actions.indicator.text}", style = MaterialTheme.typography.titleMedium, color = color)
        }
        Text(
            "${periodText(report.firstDay, report.lastDay)} · ${ReportFormat.recordingRate(report.recordingRate)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun periodText(first: LocalDate, last: LocalDate): String =
    if (first == last) "${first.monthValue}월 ${first.dayOfMonth}일" else "${first.monthValue}월 ${first.dayOfMonth}일 ~ ${last.dayOfMonth}일"

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text("고지서를 만드는 중…", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Message(text: String, action: String, onAction: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text, style = MaterialTheme.typography.titleLarge)
            Button(onClick = onAction, modifier = Modifier.heightIn(min = TouchTarget)) { Text(action) }
        }
    }
}
