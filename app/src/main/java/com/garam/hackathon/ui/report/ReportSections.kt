package com.garam.hackathon.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garam.hackathon.aggregate.ItemUsage
import com.garam.hackathon.aggregate.PeriodKind
import com.garam.hackathon.aggregate.ReportFormat
import com.garam.hackathon.aggregate.ReportItem
import com.garam.hackathon.aggregate.UsageBucket
import com.garam.hackathon.aggregate.UsageReport
import com.garam.hackathon.ui.components.Badge
import com.garam.hackathon.ui.components.BadgeTone
import com.garam.hackathon.ui.components.MeterBar
import com.garam.hackathon.ui.components.SecondaryButton
import com.garam.hackathon.ui.components.Section
import com.garam.hackathon.ui.components.TouchTarget
import kotlin.math.roundToInt

/** Recording rates below this get a warm badge: some driving was missed. */
private const val GOOD_RECORDING_RATE = 0.8

/** The total for the period, how it compares, badges for recording rate and driving, and the recording state. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SummaryHero(report: UsageReport, header: HeaderActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(heroCaption(report), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(ReportFormat.duration(report.totalSec), style = MaterialTheme.typography.displayMedium)
        val change = ReportFormat.totalChange(report)
        if (change.isNotEmpty()) Text(change, style = MaterialTheme.typography.titleMedium, color = changeColor(report.totalChangePercent))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            val ratio = report.recordingRate.ratio
            val rateTone = when {
                ratio == null -> BadgeTone.NEUTRAL
                ratio >= GOOD_RECORDING_RATE -> BadgeTone.ACCENT
                else -> BadgeTone.WARM
            }
            Badge(ReportFormat.recordingRate(report.recordingRate), rateTone)
            Badge("주행 ${report.drivingCount}회 · ${ReportFormat.duration(report.drivingSec)}")
            report.avgTargetCelsius?.let { Badge("평균 설정 온도 %.1f°C".format(it)) }
        }
        TextButton(
            onClick = header.onIndicatorClick,
            contentPadding = PaddingValues(horizontal = 0.dp),
            modifier = Modifier.heightIn(min = TouchTarget),
        ) {
            val color = if (header.indicator.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            Text("● ${header.indicator.text}", style = MaterialTheme.typography.titleMedium, color = color)
        }
        ReportFormat.recordingRateNote(report.recordingRate, report.drivingSec)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "11월 1일 ~ 3일 · 기능 사용 시간 합계 · 12월 1일 완성" */
private fun heroCaption(report: UsageReport): String {
    val parts = mutableListOf(ReportFormat.range(report.firstDay, report.lastDay), "기능 사용 시간 합계")
    if (report.isCurrent && report.period.kind == PeriodKind.MONTH) {
        val next = report.period.next().start
        parts += "${next.monthValue}월 ${next.dayOfMonth}일 완성"
    }
    return parts.joinToString(" · ")
}

/** More use reads warm, less reads blue, the same reads neutral. */
@Composable
private fun changeColor(percent: Int?): Color = when {
    percent == null || percent == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
    percent > 0 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

/** Time used per hour of a day, or per day of a week or month. */
@Composable
internal fun UsageChart(report: UsageReport) {
    val buckets = report.buckets
    if (buckets.isEmpty()) return
    val max = buckets.maxOf { it.seconds }.coerceAtLeast(1)
    val unit = when (report.period.kind) {
        PeriodKind.DAY -> "시간대"
        PeriodKind.WEEK -> "요일"
        PeriodKind.MONTH -> "날짜"
    }
    Section("${unit}별 사용") {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(if (buckets.size > 12) 3.dp else 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .semantics { contentDescription = "${unit}별 사용 막대그래프" },
        ) {
            buckets.forEach { bucket ->
                val color = when {
                    bucket.isNow -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.primary
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    if (bucket.seconds > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight((bucket.seconds.toFloat() / max).coerceAtLeast(0.03f))
                                .background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(Modifier.fillMaxWidth()) {
            buckets.forEachIndexed { index, bucket ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    axisLabel(report.period.kind, index, bucket)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true),
                        )
                    }
                }
            }
        }
        peak(report)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Every weekday; every sixth hour; every seventh day of a month. */
private fun axisLabel(kind: PeriodKind, index: Int, bucket: UsageBucket): String? = when (kind) {
    PeriodKind.WEEK -> bucket.label
    PeriodKind.DAY -> if (index % 6 == 0) "${bucket.label}시" else null
    PeriodKind.MONTH -> if (index % 7 == 0) "${bucket.label}일" else null
}

/**
 * "가장 많이 쓴 날: 10일 · 2시간 10분". An hour names no total: features used together add
 * up past sixty minutes, which reads like an error.
 */
private fun peak(report: UsageReport): String? {
    val top = report.buckets.maxByOrNull { it.seconds }?.takeIf { it.seconds > 0 } ?: return null
    return when (report.period.kind) {
        PeriodKind.DAY -> "가장 많이 쓴 시간대: ${top.label}시"
        PeriodKind.WEEK -> "가장 많이 쓴 요일: ${top.label}요일 · ${ReportFormat.duration(top.seconds)}"
        PeriodKind.MONTH -> "가장 많이 쓴 날: ${top.label}일 · ${ReportFormat.duration(top.seconds)}"
    }
}

private val LabelWidth = 240.dp
private val ValueWidth = 170.dp
private val ChangeWidth = 150.dp

/**
 * Every item used in this period; one used only before shows in the total's change and,
 * monthly, among the unused chips. Wide windows show a bar for this period and a thinner
 * one for the compared period; narrow windows put one bar under each row.
 */
@Composable
internal fun UsageTable(report: UsageReport, wide: Boolean, modifier: Modifier = Modifier) {
    val versus = ReportFormat.versus(report.period.kind)
    val used = report.usages.filter { it.amount > 0 }
    val timed = used.filter { it.item.kind == ReportItem.Kind.TIME }
    val counted = used.filter { it.item.kind == ReportItem.Kind.COUNT }
    val maxSec = timed.maxOfOrNull { maxOf(it.onSec, it.previousOnSec) }?.coerceAtLeast(1) ?: 1
    Section("기능별 사용", modifier) {
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderCell("기능", Modifier.width(LabelWidth))
                HeaderCell("${report.label} / $versus", Modifier.weight(1f))
                HeaderCell(report.label, Modifier.width(ValueWidth), TextAlign.End)
                HeaderCell("변화", Modifier.width(ChangeWidth), TextAlign.End)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        val top = report.top.firstOrNull()?.item
        timed.forEach { usage ->
            val color = if (usage.item == top) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            if (wide) WideRow(usage, usage.onSec.toFloat() / maxSec, usage.previousOnSec.toFloat() / maxSec, color) else CompactRow(usage, usage.onSec.toFloat() / maxSec, color)
        }
        if (counted.isNotEmpty() && timed.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        counted.forEach { usage -> if (wide) WideRow(usage, null, null, Color.Unspecified) else CompactRow(usage, null, Color.Unspecified) }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier, textAlign = align, maxLines = 1)
}

@Composable
private fun WideRow(usage: ItemUsage, bar: Float?, previousBar: Float?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(LabelWidth)) {
            Text(usage.item.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ReportFormat.level(usage.item, usage.avgLevel)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (bar != null && previousBar != null) {
                MeterBar(bar, color)
                MeterBar(previousBar, MaterialTheme.colorScheme.outline, thickness = 8.dp)
            }
        }
        Text(ReportFormat.amount(usage), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(ValueWidth), textAlign = TextAlign.End)
        Box(Modifier.width(ChangeWidth), contentAlignment = Alignment.CenterEnd) { ChangeText(usage) }
    }
}

@Composable
private fun CompactRow(usage: ItemUsage, bar: Float?, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(usage.item.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(ReportFormat.amount(usage), style = MaterialTheme.typography.bodyLarge)
            ChangeText(usage)
        }
        if (bar != null) MeterBar(bar, color, thickness = 12.dp)
    }
}

@Composable
private fun ChangeText(usage: ItemUsage) {
    val change = usage.changePercent
    val (text, color) = when {
        usage.isNew -> "새로 사용" to MaterialTheme.colorScheme.secondary
        change == null -> "" to Color.Unspecified
        change == 0 -> "변화 없음" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> ReportFormat.percent(change) to changeColor(change)
    }
    if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1)
}

/** Supported features with no use this month, as chips. Monthly reports only. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UnusedChips(report: UsageReport) {
    if (report.unused.isEmpty()) return
    Section("${report.label} 안 쓴 기능") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            report.unused.forEach { item ->
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        Text("차가 지원하지만 ${report.label} 한 번도 켜지 않은 기능이에요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Hidden for an empty period, whose empty state already says what to expect. */
@Composable
internal fun TipLine(report: UsageReport) {
    if (report.tip.isBlank() || !report.hasData) return
    Section("한 줄 팁") { Text(report.tip, style = MaterialTheme.typography.bodyLarge) }
}

/** What a period with no record shows instead of the chart and table. */
@Composable
internal fun EmptyState(report: UsageReport, modifier: Modifier = Modifier) {
    val body = when {
        !report.isCurrent -> "${report.label}에는 기록이 없어요. 앱이 꺼져 있었거나 차를 타지 않은 기간이에요."
        report.period.kind == PeriodKind.DAY -> "차를 타고 기능을 켜면 오늘 기록이 여기에 쌓여요."
        report.period.kind == PeriodKind.WEEK -> "이번 주에 차를 타면 날마다 쓴 기능이 여기에 쌓여요."
        else -> {
            val next = report.period.next().start
            "이번 달 고지서는 ${next.monthValue}월 ${next.dayOfMonth}일에 완성돼요. 그동안 쌓이는 기록은 일간·주간에서도 볼 수 있어요."
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("아직 기록이 없어요", style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Debug and demo entries. Absent in the submitted build. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExtraButtons(extras: ReportExtras) {
    if (extras.onLoadDemo == null && extras.onOpenDebug == null) return
    extras.demoMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        extras.onLoadDemo?.let { load ->
            SecondaryButton(if (extras.demoLoading) "합성 데이터 만드는 중…" else "데모: 합성 데이터 불러오기", load, enabled = !extras.demoLoading)
        }
        extras.onOpenDebug?.let { open -> SecondaryButton("수집 확인 (debug)", open) }
    }
}

/** Percent of [ratio] for the driving summary. */
internal fun percentText(ratio: Double): String = "${(ratio * 100).roundToInt()}%"
