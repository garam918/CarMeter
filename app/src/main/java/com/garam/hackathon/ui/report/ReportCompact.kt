package com.garam.hackathon.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garam.hackathon.aggregate.UsageReport

/** Narrow windows (portrait, one window): the same sections in one column. */
@Composable
fun ReportCompact(report: UsageReport, header: HeaderActions, extras: ReportExtras) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { SummaryHero(report, header) }
        if (report.hasData) {
            item { UsageChart(report) }
            item { UsageTable(report, wide = false) }
        } else {
            item { EmptyState(report) }
        }
        item { TipLine(report) }
        item { UnusedChips(report) }
        item { ExtraButtons(extras) }
    }
}
