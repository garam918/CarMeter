package com.garam.hackathon.aggregate

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class TipAndFormatTest {
    private val month = YearMonth.of(2026, 11)
    private val today = LocalDate.of(2026, 11, 10)
    private val fullRate = RecordingRate(RecordingRate.Basis.DISTANCE, recorded = 100.0, reference = 100.0)

    private fun report(
        current: List<DailySummary>,
        previous: List<DailySummary> = emptyList(),
        supported: Set<ReportItem>? = null,
        rate: RecordingRate = fullRate,
    ) = UsageReportBuilder.build(Period.of(PeriodKind.MONTH, today), today, current, previous, supported, rate)

    private fun use(item: ReportItem, onSec: Long, m: YearMonth = month) = DailySummary(m.atDay(1), item, onSec, 1)

    @Test
    fun `durations read naturally`() {
        assertEquals("0분", ReportFormat.duration(0))
        assertEquals("1분 미만", ReportFormat.duration(30))
        assertEquals("45분", ReportFormat.duration(45 * 60))
        assertEquals("3시간 20분", ReportFormat.duration(3 * 3600 + 20 * 60))
        assertEquals("14시간", ReportFormat.duration(14 * 3600 + 20 * 60))
    }

    @Test
    fun `particles follow the last syllable`() {
        assertEquals("시트 열선은", ReportFormat.topic("시트 열선"))
        assertEquals("와이퍼는", ReportFormat.topic("와이퍼"))
        assertEquals("공조 AUTO은(는)", ReportFormat.topic("공조 AUTO"))
        assertEquals("에코 모드예요", ReportFormat.copula("에코 모드"))
        assertEquals("에어컨이에요", ReportFormat.copula("에어컨"))
    }

    @Test
    fun `a low recording rate comes first`() {
        val tip = report(listOf(use(ReportItem.SEAT_HEAT, 3600)), rate = RecordingRate(RecordingRate.Basis.DISTANCE, 35.0, 100.0)).tip

        assertEquals("기록률이 35%예요. 차에 탄 뒤 CarMeter를 한 번 열어 두면 빠짐없이 기록돼요.", tip)
    }

    @Test
    fun `no data asks for a few more days`() {
        assertEquals("아직 기록이 적어요. 며칠 타고 나면 고지서가 채워져요.", report(emptyList()).tip)
    }

    @Test
    fun `long window-open driving is pointed out`() {
        val tip = report(listOf(use(ReportItem.WINDOW_OPEN_DRIVING, 2 * 3600))).tip

        assertEquals("창문을 연 채 2시간 달렸어요. 빠르게 달릴 때는 창문을 닫으면 실내가 조용해져요.", tip)
    }

    @Test
    fun `seat heating without the supported wheel heater suggests it`() {
        val tip = report(listOf(use(ReportItem.SEAT_HEAT, 3600)), supported = setOf(ReportItem.SEAT_HEAT, ReportItem.STEERING_WHEEL_HEAT)).tip

        assertEquals("이 차는 핸들 열선도 지원해요. 시트 열선과 함께 써 보세요.", tip)
    }

    @Test
    fun `a big rise of the top item is reported`() {
        val tip = report(listOf(use(ReportItem.AIR_CONDITIONING, 2 * 3600)), previous = listOf(use(ReportItem.AIR_CONDITIONING, 3600, month.minusMonths(1)))).tip

        assertEquals("에어컨 사용이 지난달보다 +100% 늘었어요.", tip)
    }

    @Test
    fun `otherwise an unused supported feature is named`() {
        val tip = report(listOf(use(ReportItem.AIR_CONDITIONING, 3600)), supported = setOf(ReportItem.AIR_CONDITIONING, ReportItem.WIPER)).tip

        assertEquals("와이퍼는 이번 달 한 번도 쓰지 않았어요.", tip)
    }

    @Test
    fun `the fallback names the most used feature`() {
        assertEquals("이번 달 가장 많이 쓴 기능은 에어컨이에요.", report(listOf(use(ReportItem.AIR_CONDITIONING, 3600))).tip)
    }
}
