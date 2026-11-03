package com.example.hyundaihackathon.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hyundaihackathon.aggregate.MonthlyReport
import com.example.hyundaihackathon.aggregate.ReportFormat
import kotlin.math.roundToInt

private const val NONE = "—"

/** The driving view: three large numbers, nothing to scroll and nothing to tap. */
@Composable
fun DrivingSummary(report: MonthlyReport?) {
    val top = report?.top?.firstOrNull()
    val tiles = listOf(
        Tile("가장 많이 쓴 기능", top?.let { ReportFormat.duration(it.onSec) } ?: NONE, top?.item?.label ?: NONE),
        Tile(
            "지난달 대비",
            top?.changePercent?.let(ReportFormat::percent) ?: if (top?.isNew == true) "새로 사용" else NONE,
            top?.item?.label ?: NONE,
        ),
        Tile("기록률", report?.recordingRate?.ratio?.let { "${(it * 100).roundToInt()}%" } ?: NONE, "이번 달"),
    )
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        val stacked = maxWidth < maxHeight
        Column(verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxSize()) {
            if (stacked) {
                tiles.forEach { TileView(it, Modifier.fillMaxWidth().weight(1f)) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.weight(1f)) {
                    tiles.forEach { TileView(it, Modifier.weight(1f).fillMaxSize()) }
                }
            }
            Text(
                "정차하면 전체 고지서를 볼 수 있어요",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class Tile(val title: String, val value: String, val caption: String)

@Composable
private fun TileView(tile: Tile, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(tile.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(tile.value, style = MaterialTheme.typography.displayLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(tile.caption, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
