package com.garam.hackathon.aggregate

import kotlin.math.roundToInt

/**
 * Picks the one-line tip at the bottom of the bill. Rules run in priority order and the
 * first that applies wins. Tips state what was recorded; they never estimate energy or
 * cost, which is outside the MVP.
 */
object TipGenerator {
    private const val LOW_RECORDING_RATE = 0.8
    private const val WINDOW_OPEN_DRIVING_SEC = 30 * 60L
    private const val BIG_INCREASE_PERCENT = 50

    fun tip(report: UsageReport, supported: Set<ReportItem>?): String {
        val rate = report.recordingRate.ratio
        if (rate != null && rate < LOW_RECORDING_RATE) {
            return "기록률이 ${(rate * 100).roundToInt()}%예요. 차에 탄 뒤 CarMeter를 한 번 열어 두면 빠짐없이 기록돼요."
        }
        if (!report.hasData) {
            return if (report.isCurrent) "아직 기록이 적어요. 며칠 타고 나면 고지서가 채워져요." else "${report.label}에는 기록이 없어요."
        }

        val byItem = report.usages.associateBy { it.item }
        val windowOpen = byItem[ReportItem.WINDOW_OPEN_DRIVING]?.onSec ?: 0
        if (windowOpen >= WINDOW_OPEN_DRIVING_SEC) {
            return "창문을 연 채 ${ReportFormat.duration(windowOpen)} 달렸어요. 빠르게 달릴 때는 창문을 닫으면 실내가 조용해져요."
        }

        val seatHeat = byItem[ReportItem.SEAT_HEAT]?.onSec ?: 0
        val wheelSupported = supported?.contains(ReportItem.STEERING_WHEEL_HEAT) == true
        val wheelUsed = (byItem[ReportItem.STEERING_WHEEL_HEAT]?.onSec ?: 0) > 0
        if (seatHeat > 0 && wheelSupported && !wheelUsed) {
            return "이 차는 핸들 열선도 지원해요. 시트 열선과 함께 써 보세요."
        }

        val topItem = report.top.firstOrNull()
        val increase = topItem?.changePercent
        if (topItem != null && increase != null && increase >= BIG_INCREASE_PERCENT) {
            return "${topItem.item.label} 사용이 지난달보다 ${ReportFormat.percent(increase)} 늘었어요."
        }

        report.unused.firstOrNull()?.let { item ->
            return "${ReportFormat.topic(item.label)} ${report.label} 한 번도 쓰지 않았어요."
        }

        val top = topItem ?: report.usages.first { it.amount > 0 }
        return "${report.label} 가장 많이 쓴 기능은 ${ReportFormat.copula(top.item.label)}."
    }
}
