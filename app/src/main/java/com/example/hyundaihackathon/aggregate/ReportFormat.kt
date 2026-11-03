package com.example.hyundaihackathon.aggregate

import kotlin.math.abs
import kotlin.math.roundToInt

/** Korean phrases for the bill, such as "시트 열선 14시간 (지난달 대비 +60%)". */
object ReportFormat {
    fun duration(seconds: Long): String {
        if (seconds <= 0) return "0분"
        if (seconds < 60) return "1분 미만"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            hours == 0L -> "${minutes}분"
            hours >= 10 || minutes == 0L -> "${hours}시간"
            else -> "${hours}시간 ${minutes}분"
        }
    }

    fun amount(usage: ItemUsage): String =
        if (usage.item.kind == ReportItem.Kind.COUNT) "${usage.count}회" else duration(usage.onSec)

    fun percent(change: Int): String = when {
        change > 0 -> "+$change%"
        change < 0 -> "-${abs(change)}%"
        else -> "0%"
    }

    /** "지난달 대비 +60%", "지난달엔 안 씀", or null when neither month had any use. */
    fun change(usage: ItemUsage): String? = when {
        usage.isNew -> "지난달엔 안 씀"
        usage.changePercent != null -> "지난달 대비 ${percent(usage.changePercent!!)}"
        else -> null
    }

    fun headline(usage: ItemUsage): String {
        val change = change(usage)
        return "${usage.item.label} ${amount(usage)}" + if (change != null) " ($change)" else ""
    }

    fun unused(item: ReportItem, monthLabel: String = "이번 달"): String = "${item.label}: $monthLabel 0회"

    fun recordingRate(rate: RecordingRate): String =
        rate.ratio?.let { "기록률 ${(it * 100).roundToInt()}%" } ?: "기록률 측정 불가"

    fun level(item: ReportItem, avgLevel: Double?): String? = when {
        avgLevel == null || !item.hasLevels -> null
        item == ReportItem.TARGET_TEMPERATURE -> "%.1f°C".format(avgLevel / 10)
        else -> "평균 %.1f단".format(avgLevel)
    }

    /** "시트 열선은", "와이퍼는". Words not ending in Hangul get "은(는)". */
    fun topic(word: String): String = word + pick(word, "은", "는", "은(는)")

    /** "시트 열선이에요", "와이퍼예요". */
    fun copula(word: String): String = word + pick(word, "이에요", "예요", "이에요")

    private fun pick(word: String, withFinal: String, withoutFinal: String, unknown: String): String {
        val last = word.lastOrNull() ?: return unknown
        if (last !in '가'..'힣') return unknown
        return if ((last - '가') % 28 != 0) withFinal else withoutFinal
    }
}
