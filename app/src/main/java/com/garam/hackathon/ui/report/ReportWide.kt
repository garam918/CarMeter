package com.garam.hackathon.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garam.hackathon.aggregate.UsageReport

/** Wide windows (landscape, two windows): total, chart and notes on the left, the table on the right. */
@Composable
fun ReportWide(report: UsageReport, header: HeaderActions, extras: ReportExtras) {
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(
            modifier = Modifier.weight(0.44f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SummaryHero(report, header)
            if (report.hasData) UsageChart(report)
            TipLine(report)
            UnusedChips(report)
            ExtraButtons(extras)
        }
        Column(modifier = Modifier.weight(0.56f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            if (report.hasData) UsageTable(report, wide = true) else EmptyState(report)
        }
    }
}
