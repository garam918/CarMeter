package com.garam.hackathon.aggregate

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Korean phrases for the reports, such as "14시간 20분" or "지난달 같은 기간보다 +18%". */
object ReportFormat {
    private val Weekdays = listOf("월", "화", "수", "목", "금", "토", "일")

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

    fun recordingRate(rate: RecordingRate): String =
        rate.ratio?.let { "기록률 ${(it * 100).roundToInt()}%" } ?: "기록률 없음"

    /** Why there is no recording rate, or null when there is one. */
    fun recordingRateNote(rate: RecordingRate, drivingSec: Long): String? = when {
        rate.ratio != null -> null
        drivingSec == 0L -> "이 기간에는 주행 기록이 없어 기록률을 계산하지 않았어요."
        rate.basis == RecordingRate.Basis.NONE -> "이 차는 주행 시간·거리를 알려 주지 않아 기록률을 계산할 수 없어요."
        else -> "차가 알려 준 주행 시간·거리가 늘지 않아 기록률을 계산할 수 없어요."
    }

    fun level(item: ReportItem, avgLevel: Double?): String? = when {
        avgLevel == null || !item.hasLevels -> null
        item == ReportItem.TARGET_TEMPERATURE -> "%.1f°C".format(avgLevel / 10)
        else -> "평균 %.1f단".format(avgLevel)
    }

    /** "월" to "일". */
    fun weekday(date: LocalDate): String = Weekdays[date.dayOfWeek.value - 1]

    /** "11월 3일", or "11월 1일 ~ 3일", or "10월 27일 ~ 11월 2일" across months. */
    fun range(first: LocalDate, last: LocalDate): String = when {
        first == last -> "${first.monthValue}월 ${first.dayOfMonth}일"
        first.month == last.month -> "${first.monthValue}월 ${first.dayOfMonth}일 ~ ${last.dayOfMonth}일"
        else -> "${first.monthValue}월 ${first.dayOfMonth}일 ~ ${last.monthValue}월 ${last.dayOfMonth}일"
    }

    /** The screen title: "11월 고지서", "10월 27일 ~ 11월 2일", "11월 3일 화요일". */
    fun periodTitle(period: Period): String = when (period.kind) {
        PeriodKind.MONTH -> "${period.start.monthValue}월 고지서"
        PeriodKind.WEEK -> range(period.start, period.endInclusive)
        PeriodKind.DAY -> "${period.start.monthValue}월 ${period.start.dayOfMonth}일 ${weekday(period.start)}요일"
    }

    /** "이번 달" or "10월", "이번 주" or "10월 27일 주", "오늘" or "11월 2일". */
    fun periodLabel(period: Period, isCurrent: Boolean): String = when (period.kind) {
        PeriodKind.MONTH -> if (isCurrent) "이번 달" else "${period.start.monthValue}월"
        PeriodKind.WEEK -> if (isCurrent) "이번 주" else "${period.start.monthValue}월 ${period.start.dayOfMonth}일 주"
        PeriodKind.DAY -> if (isCurrent) "오늘" else "${period.start.monthValue}월 ${period.start.dayOfMonth}일"
    }

    /** The compared period in a column header or after "대비": "지난달", "지난주". */
    fun versus(kind: PeriodKind): String = if (kind == PeriodKind.MONTH) "지난달" else "지난주"

    /** "지난달 같은 기간보다 +18%", "지난주 같은 요일보다 -5%", or what to say without a comparison. */
    fun totalChange(report: UsageReport): String {
        val compared = when {
            report.period.kind == PeriodKind.DAY -> "지난주 같은 요일"
            report.isComplete -> versus(report.period.kind)
            else -> "${versus(report.period.kind)} 같은 기간"
        }
        val change = report.totalChangePercent
        return when {
            change != null -> "${compared}보다 ${percent(change)}"
            report.totalSec > 0 -> "${compared}에는 기록이 없어요"
            else -> ""
        }
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
