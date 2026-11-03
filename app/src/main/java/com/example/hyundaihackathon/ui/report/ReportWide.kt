package com.example.hyundaihackathon.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hyundaihackathon.aggregate.ItemUsage
import com.example.hyundaihackathon.aggregate.MonthlyReport
import com.example.hyundaihackathon.aggregate.ReportFormat
import com.example.hyundaihackathon.aggregate.ReportItem

/** Wide windows (landscape, two windows): summary on the left, the full table and chart on the right. */
@Composable
fun ReportWide(
    report: MonthlyReport,
    state: ReportUiState,
    header: HeaderActions,
    extras: ReportExtras,
) {
    Row(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(
            modifier = Modifier.weight(0.4f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ReportHeader(report, state, header)
            TipCard(report)
            FactsCard(report)
            ChangesCard(report)
            UnusedCard(report)
            ExtraButtons(extras)
        }
        UsageTable(report, Modifier.weight(0.6f).fillMaxHeight())
    }
}

/** Every time item with a bar scaled to the most used one, then the counted items. */
@Composable
private fun UsageTable(report: MonthlyReport, modifier: Modifier) {
    val timed = report.usages.filter { it.item.kind == ReportItem.Kind.TIME }
    val counted = report.usages.filter { it.item.kind == ReportItem.Kind.COUNT }
    val maxSec = timed.maxOfOrNull { maxOf(it.onSec, it.previousOnSec) }?.coerceAtLeast(1) ?: 1
    ReportCard("기능별 사용", modifier) {
        TableHeader()
        HorizontalDivider()
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(timed, key = { it.item }) { usage -> TableRow(usage, usage.onSec.toFloat() / maxSec, usage.previousOnSec.toFloat() / maxSec) }
            items(counted, key = { it.item }) { usage -> TableRow(usage, bar = null, previousBar = null) }
            if (report.usages.isEmpty()) item { Text("아직 기록된 사용이 없어요.", style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

private val LabelWidth = 220.dp
private val ValueWidth = 150.dp
private val ChangeWidth = 130.dp

@Composable
private fun TableHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HeaderCell("기능", Modifier.width(LabelWidth))
        HeaderCell("이번 달 / 지난달", Modifier.weight(1f))
        HeaderCell("이번 달", Modifier.width(ValueWidth), TextAlign.End)
        HeaderCell("변화", Modifier.width(ChangeWidth), TextAlign.End)
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier, textAlign = align)
}

@Composable
private fun TableRow(usage: ItemUsage, bar: Float?, previousBar: Float?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(LabelWidth)) {
            Text(usage.item.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ReportFormat.level(usage.item, usage.avgLevel)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (bar != null && previousBar != null) {
                Bar(bar, MaterialTheme.colorScheme.primary)
                Bar(previousBar, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        }
        Text(ReportFormat.amount(usage), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(ValueWidth), textAlign = TextAlign.End)
        Box(Modifier.width(ChangeWidth), contentAlignment = Alignment.CenterEnd) { ChangeText(usage) }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(14.dp)) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.01f, 1f))
                    .height(14.dp)
                    .background(color, RoundedCornerShape(7.dp)),
            )
        }
    }
}
