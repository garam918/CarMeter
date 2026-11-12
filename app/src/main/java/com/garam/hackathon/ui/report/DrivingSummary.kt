package com.garam.hackathon.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garam.hackathon.aggregate.ReportFormat
import com.garam.hackathon.aggregate.UsageReport

private const val NONE = "기록 없음"

/**
 * Three full-size tiles stacked with the title and the hint need about 860dp. Shorter
 * portrait windows (Connect-S 12.9", WS and WF, 800 to 855dp tall) get tighter tiles,
 * since nothing can scroll while driving.
 */
private val RoomyStackHeight = 880.dp

/**
 * The driving view: three facts about this month, each readable at a glance, with nothing
 * to scroll or tap. Without a recording rate the third fact is this month's driving time.
 */
@Composable
fun DrivingSummary(report: UsageReport?) {
    val top = report?.top?.firstOrNull()
    val ratio = report?.recordingRate?.ratio
    val drives = report?.let { "주행 ${it.drivingCount}회" }
    val tiles = listOf(
        Tile(
            "이번 달 기능 사용",
            report?.takeIf { it.totalSec > 0 }?.let { ReportFormat.duration(it.totalSec) } ?: NONE,
            report?.let { ReportFormat.range(it.firstDay, it.lastDay) },
        ),
        Tile("가장 많이 쓴 기능", top?.item?.label ?: NONE, top?.let { ReportFormat.duration(it.onSec) }),
        if (ratio != null) {
            Tile("기록률", percentText(ratio), drives)
        } else {
            Tile("이번 달 주행", report?.let { ReportFormat.duration(it.drivingSec) } ?: NONE, drives)
        },
    )
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        val stacked = maxWidth < maxHeight && maxHeight >= 720.dp
        val dense = stacked && maxHeight < RoomyStackHeight
        Column(
            verticalArrangement = Arrangement.spacedBy(if (dense) 16.dp else 32.dp, Alignment.CenterVertically),
            modifier = Modifier.fillMaxSize(),
        ) {
            Text("주행 중 요약", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            if (stacked) {
                tiles.forEachIndexed { index, tile -> TileView(tile, Modifier.fillMaxWidth(), featured = index == 0, dense = dense) }
            } else {
                // Equal heights, whichever tile has the longest text.
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                    tiles.forEachIndexed { index, tile -> TileView(tile, Modifier.weight(1f).fillMaxHeight(), featured = index == 0) }
                }
            }
            Text("P에 놓으면 전체 고지서로 돌아가요", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class Tile(val title: String, val value: String, val caption: String? = null)

@Composable
private fun TileView(tile: Tile, modifier: Modifier, featured: Boolean, dense: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val detailColor = if (featured) ReportMint else colors.onSurfaceVariant
    val valueStyle = if (dense) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium
    Column(
        modifier = modifier
            .background(if (featured) ReportInk else colors.surfaceVariant, RoundedCornerShape(20.dp))
            .padding(if (dense) 20.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 8.dp),
    ) {
        Text(tile.title, style = MaterialTheme.typography.titleLarge, color = detailColor, maxLines = 2)
        Text(tile.value, style = valueStyle, color = if (featured) Color.White else colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        tile.caption?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = detailColor, maxLines = 2) }
    }
}
