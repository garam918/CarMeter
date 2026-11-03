package com.garam.hackathon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garam.hackathon.aggregate.RecordingRate
import com.garam.hackathon.collector.CollectorStatus
import com.garam.hackathon.data.CounterEvent
import com.garam.hackathon.data.Interval
import com.garam.hackathon.data.Trip
import com.garam.hackathon.vehicle.CapabilityResult
import com.garam.hackathon.vehicle.FeatureKey
import com.garam.hackathon.vehicle.Zone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the temporary collection screen shows. */
data class DebugData(
    val status: CollectorStatus,
    val intervals: List<Interval>,
    val counters: List<CounterEvent>,
    val trips: List<Trip>,
    val recordingRate: RecordingRate?,
    /** Null when this build has no boot receiver. */
    val bootStartEnabled: Boolean?,
)

/** Debug-only screen for checking collection on the emulator, opened from the bill. */
@Composable
fun CollectorDebugScreen(
    data: DebugData,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBootStartChange: (Boolean) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 64.dp)) { Text("‹ 고지서로") } }
            item { Text("CarMeter · 수집 확인", style = MaterialTheme.typography.headlineSmall) }
            item { Text(statusText(data.status), style = MaterialTheme.typography.titleMedium) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val running = data.status is CollectorStatus.Running || data.status is CollectorStatus.Starting
                    Button(onClick = onStart, enabled = !running) { Text("기록 시작") }
                    OutlinedButton(onClick = onStop, enabled = running) { Text("기록 중지") }
                }
            }
            item { Text(recordingRateText(data.recordingRate), style = MaterialTheme.typography.titleSmall) }
            data.bootStartEnabled?.let { enabled ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("부팅 시 자동 시작 (디버그 측정용)", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = enabled, onCheckedChange = onBootStartChange)
                    }
                }
            }
            capabilitiesOf(data.status)?.let { capabilities ->
                item { SectionTitle("지원 기능") }
                items(capabilities.entries.toList(), key = { it.key }) { (key, result) -> Body(capabilityText(key, result)) }
            }
            item { SectionTitle("최근 트립") }
            items(data.trips, key = { "trip-${it.id}" }) { Body(tripText(it)) }
            item { SectionTitle("최근 횟수") }
            items(data.counters, key = { it.id }) { Body(counterText(it)) }
            item { SectionTitle("최근 구간 (60초마다 저장, 이 화면으로 돌아오면 바로 저장)") }
            items(data.intervals, key = { it.id }) { Body(intervalText(it)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    Text(text, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)

private fun statusText(status: CollectorStatus) = when (status) {
    CollectorStatus.Stopped -> "기록 꺼짐"
    CollectorStatus.Starting -> "시작하는 중…"
    is CollectorStatus.Running -> "기록 중 · 차량 지표: ${status.metrics.joinToString().ifEmpty { "없음" }}"
    is CollectorStatus.Failed -> "차량 서비스에 연결하지 못했어요"
}

private fun capabilitiesOf(status: CollectorStatus) = when (status) {
    is CollectorStatus.Running -> status.capabilities
    is CollectorStatus.Failed -> status.capabilities
    else -> null
}

private fun recordingRateText(rate: RecordingRate?): String {
    if (rate == null) return "기록률(최근 24시간): 계산 중"
    val ratio = rate.ratio ?: return "기록률(최근 24시간): 측정 불가 (${rate.basis})"
    val percent = (ratio * 100).toInt()
    return "기록률(최근 24시간): $percent%% · 기록 %.1f / 차량 %.1f %s (%s)"
        .format(rate.recorded, rate.reference, rate.basis.unit, rate.basis)
}

private fun capabilityText(key: FeatureKey, result: CapabilityResult): String {
    if (!result.supported) return "${key.label}: 미지원 (${result.error ?: "지원 영역 없음"})"
    val zones = result.zones.joinToString { zone ->
        val range = if (zone.min != null && zone.max != null) " ${zone.min}..${zone.max}" else ""
        Zone.label(key, zone.zone) + range
    }
    return "${key.label}: $zones"
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

private fun time(ts: Long) = timeFormat.format(Instant.ofEpochMilli(ts))

private fun intervalText(interval: Interval): String {
    val end = interval.endTs
    val span = if (end == null) {
        "${time(interval.startTs)} → 진행 중"
    } else {
        "${time(interval.startTs)} → ${time(end)} (${durationText(end - interval.startTs)}, ${interval.closedBy})"
    }
    val feature = interval.featureKey
    return "${feature.label} · ${Zone.label(feature, interval.zone)} · ${levelLabel(feature, interval.level)} · $span"
}

private fun counterText(counter: CounterEvent) =
    "${counter.featureKey.label} · ${Zone.label(counter.featureKey, counter.zone)} · ${time(counter.ts)}"

private fun tripText(trip: Trip): String {
    val end = trip.endTs?.let(::time) ?: "진행 중"
    val km = trip.end.odometerKm?.let { endKm -> trip.start.odometerKm?.let { "%.1f km".format(endKm - it) } } ?: "거리 없음"
    return "${time(trip.startTs)} → $end · $km"
}

private fun durationText(millis: Long): String {
    val seconds = millis / 1000
    return if (seconds < 60) "${seconds}초" else "${seconds / 60}분 ${seconds % 60}초"
}
