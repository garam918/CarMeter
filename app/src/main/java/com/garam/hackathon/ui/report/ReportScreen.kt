package com.garam.hackathon.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garam.hackathon.R
import com.garam.hackathon.aggregate.PeriodKind
import com.garam.hackathon.aggregate.ReportFormat
import com.garam.hackathon.ui.RecordingIndicator
import com.garam.hackathon.ui.components.NavIconButton
import com.garam.hackathon.ui.components.PrimaryButton
import com.garam.hackathon.ui.components.SecondaryButton
import com.garam.hackathon.ui.components.SegmentedTabs

/** Windows at least this wide get two columns: summary and chart, then the table. */
private val WideWidth = 1000.dp

private val PeriodTabs = listOf(PeriodKind.DAY to "일간", PeriodKind.WEEK to "주간", PeriodKind.MONTH to "월간")

/**
 * The usage report for a day, a week or a month. While driving only three large numbers
 * about this month are shown, with no scrolling and nothing to tap. When parked, narrow
 * windows get one column and wide windows two.
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
        if (isDriving) {
            DrivingSummary(state.monthReport)
            return@Surface
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wide = maxWidth >= WideWidth
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
                ReportHeader(state, header, wide)
                Spacer(Modifier.height(24.dp))
                val report = state.report
                Box(Modifier.weight(1f)) {
                    when {
                        report == null && state.failed -> Message("기록을 불러오지 못했어요.", action = "다시 시도", onAction = onRetry)
                        report == null -> Loading()
                        wide -> ReportWide(report, header, extras)
                        else -> ReportCompact(report, header, extras)
                    }
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

/** What the header shows besides the report, and what its controls do. */
data class HeaderActions(
    val indicator: RecordingIndicator,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onSelectKind: (PeriodKind) -> Unit,
    val onOpenSettings: () -> Unit,
    /** Tapping the recording state: opens consent when it is missing, settings otherwise. */
    val onIndicatorClick: () -> Unit,
)

/** Period navigation, the day/week/month tabs and settings. Wide windows fit them in one row. */
@Composable
private fun ReportHeader(state: ReportUiState, actions: HeaderActions, wide: Boolean) {
    val tabs = @Composable { SegmentedTabs(PeriodTabs, state.period.kind, actions.onSelectKind) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NavIconButton(R.drawable.ic_chevron_left, "이전", actions.onPrevious)
            Text(
                ReportFormat.periodTitle(state.period),
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            NavIconButton(R.drawable.ic_chevron_right, "다음", actions.onNext, enabled = state.canShowNext)
            if (state.loading) CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            Spacer(Modifier.weight(1f))
            if (wide) tabs()
            SecondaryButton("설정", actions.onOpenSettings)
        }
        if (!wide) tabs()
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text("기록을 정리하는 중…", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Message(text: String, action: String, onAction: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text, style = MaterialTheme.typography.titleLarge)
            PrimaryButton(action, onAction)
        }
    }
}
