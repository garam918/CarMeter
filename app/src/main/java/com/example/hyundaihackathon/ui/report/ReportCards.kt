package com.example.hyundaihackathon.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hyundaihackathon.aggregate.ItemUsage
import com.example.hyundaihackathon.aggregate.MonthlyReport
import com.example.hyundaihackathon.aggregate.ReportFormat
import com.example.hyundaihackathon.aggregate.ReportItem

/** How many unused items a card lists before summarizing the rest. */
private const val UNUSED_SHOWN = 5

@Composable
internal fun ReportCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

/** "시트 열선   14시간   +60%" */
@Composable
internal fun UsageRow(usage: ItemUsage) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(usage.item.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(ReportFormat.amount(usage), style = MaterialTheme.typography.bodyLarge)
        ChangeText(usage, Modifier.padding(start = 16.dp))
    }
}

@Composable
internal fun ChangeText(usage: ItemUsage, modifier: Modifier = Modifier) {
    val change = usage.changePercent
    val (text, color) = when {
        usage.isNew -> "새로 사용" to MaterialTheme.colorScheme.secondary
        change == null -> "" to Color.Unspecified
        change > 0 -> ReportFormat.percent(change) to MaterialTheme.colorScheme.secondary
        change < 0 -> ReportFormat.percent(change) to MaterialTheme.colorScheme.primary
        else -> "변화 없음" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = modifier)
}

@Composable
internal fun TopCard(report: MonthlyReport) {
    ReportCard("많이 쓴 기능") {
        if (report.top.isEmpty()) {
            Text("아직 기록된 사용이 없어요.", style = MaterialTheme.typography.bodyLarge)
        } else {
            report.top.forEach { UsageRow(it) }
        }
    }
}

@Composable
internal fun ChangesCard(report: MonthlyReport) {
    if (report.changes.isEmpty()) return
    ReportCard("지난달 대비 변화") {
        report.changes.forEach { Text(ReportFormat.headline(it), style = MaterialTheme.typography.bodyLarge) }
        Text(
            "지난달 1~${report.comparedUntil.dayOfMonth}일과 비교했어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun UnusedCard(report: MonthlyReport) {
    if (report.unused.isEmpty()) return
    ReportCard("안 쓴 기능") {
        report.unused.take(UNUSED_SHOWN).forEach { Text(ReportFormat.unused(it, report.monthLabel), style = MaterialTheme.typography.bodyLarge) }
        val rest = report.unused.size - UNUSED_SHOWN
        if (rest > 0) {
            Text(
                "외 ${rest}개: ${report.unused.drop(UNUSED_SHOWN).joinToString { it.label }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Counted uses, total driving and the average target temperature. */
@Composable
internal fun FactsCard(report: MonthlyReport) {
    val counts = report.usages.filter { it.item.kind == ReportItem.Kind.COUNT && it.count > 0 }
    ReportCard("${report.monthLabel} 기록") {
        Text("주행 ${ReportFormat.duration(report.drivingSec)}", style = MaterialTheme.typography.bodyLarge)
        counts.forEach { Text("${it.item.label} ${ReportFormat.amount(it)}", style = MaterialTheme.typography.bodyLarge) }
        report.avgTargetCelsius?.let { Text("평균 설정 온도 %.1f°C".format(it), style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
internal fun TipCard(report: MonthlyReport) {
    ReportCard("한 줄 팁") { Text(report.tip, style = MaterialTheme.typography.bodyLarge) }
}

/** Debug and demo entries. Absent in the submitted build. */
@Composable
internal fun ExtraButtons(extras: ReportExtras) {
    extras.demoMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        extras.onLoadDemo?.let { load ->
            OutlinedButton(onClick = load, enabled = !extras.demoLoading, modifier = Modifier.heightIn(min = TouchTarget)) {
                Text(if (extras.demoLoading) "합성 데이터 만드는 중…" else "데모: 합성 데이터 불러오기")
            }
        }
        extras.onOpenDebug?.let { open ->
            TextButton(onClick = open, modifier = Modifier.heightIn(min = TouchTarget)) { Text("수집 확인 (debug)") }
        }
    }
}
