package com.example.hyundaihackathon.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hyundaihackathon.aggregate.MonthlyReport

/** Narrow windows (portrait, one window): summary cards in one column. */
@Composable
fun ReportCompact(
    report: MonthlyReport,
    state: ReportUiState,
    header: HeaderActions,
    extras: ReportExtras,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ReportHeader(report, state, header) }
        item { TopCard(report) }
        item { ChangesCard(report) }
        item { FactsCard(report) }
        item { UnusedCard(report) }
        item { TipCard(report) }
        item { ExtraButtons(extras) }
    }
}
