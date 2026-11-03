package com.example.hyundaihackathon.aggregate

import com.example.hyundaihackathon.vehicle.CapabilityResult
import com.example.hyundaihackathon.vehicle.FeatureCategory
import com.example.hyundaihackathon.vehicle.FeatureKey
import java.time.LocalDate

/**
 * One line of the monthly bill. Most items map to one feature; a few combine or split
 * features (seat heating versus ventilation, window open while driving). The enum name
 * is stored in daily_summary, so never rename an entry once it has shipped.
 */
enum class ReportItem(val label: String, val kind: Kind, val hasLevels: Boolean = false) {
    SEAT_HEAT("시트 열선", Kind.TIME, hasLevels = true),
    SEAT_VENTILATION("시트 통풍", Kind.TIME, hasLevels = true),
    STEERING_WHEEL_HEAT("핸들 열선", Kind.TIME, hasLevels = true),
    SIDE_MIRROR_HEAT("사이드미러 열선", Kind.TIME, hasLevels = true),
    AIR_CONDITIONING("에어컨", Kind.TIME),
    CLIMATE_POWER("공조", Kind.TIME),
    FAN("송풍", Kind.TIME, hasLevels = true),
    CLIMATE_AUTO("공조 AUTO", Kind.TIME),
    RECIRCULATION("내기순환", Kind.TIME),
    DEFROST("성에 제거", Kind.TIME),
    MAX_DEFROST("MAX 성에 제거", Kind.TIME),
    MAX_AC("MAX 에어컨", Kind.TIME),
    DUAL_CLIMATE("듀얼 공조", Kind.TIME),
    AIR_CLEANER("공기청정", Kind.TIME),
    LOW_BEAM("전조등", Kind.TIME),
    HIGH_BEAM("상향등", Kind.TIME),
    AMBIENT_LIGHT("앰비언트 라이트", Kind.TIME),
    WIPER("와이퍼", Kind.TIME),
    CRUISE("크루즈", Kind.TIME),
    ECO_MODE("에코 모드", Kind.TIME),
    SPORT_MODE("스포츠 모드", Kind.TIME),
    WINDOW_OPEN_DRIVING("창문 열고 주행", Kind.TIME),
    DOOR_OPEN("문 열림", Kind.COUNT),
    TURN_SIGNAL("방향지시등", Kind.COUNT),

    /** Total driving, for context rather than as a feature used. */
    DRIVING("주행", Kind.STAT),

    /** Average target temperature while the climate system was on, in tenths of a degree. */
    TARGET_TEMPERATURE("평균 설정 온도", Kind.STAT, hasLevels = true),
    ;

    enum class Kind {
        /** Measured in time used. */
        TIME,

        /** Measured in number of uses. */
        COUNT,

        /** Context shown on the bill but never ranked or listed as unused. */
        STAT,
    }

    /** Whether this item is a feature the driver uses, so it can be ranked or listed as unused. */
    val isUsage: Boolean get() = kind != Kind.STAT

    /** The settings category that switches this item's recording on and off. */
    val category: FeatureCategory
        get() = when (this) {
            SEAT_HEAT, SEAT_VENTILATION -> FeatureCategory.SEAT
            LOW_BEAM, HIGH_BEAM, AMBIENT_LIGHT, TURN_SIGNAL -> FeatureCategory.LIGHT
            WIPER, CRUISE, ECO_MODE, SPORT_MODE -> FeatureCategory.DRIVING
            WINDOW_OPEN_DRIVING, DOOR_OPEN -> FeatureCategory.BODY
            DRIVING -> FeatureCategory.SYSTEM
            else -> FeatureCategory.CLIMATE
        }

    companion object {
        /**
         * Items the vehicle supports, according to the capability snapshot. Null when there is
         * no snapshot yet, so callers can avoid claiming a feature was never used.
         */
        fun supported(capabilities: Map<FeatureKey, CapabilityResult>): Set<ReportItem>? {
            if (capabilities.isEmpty()) return null
            fun has(key: FeatureKey) = capabilities[key]?.supported == true
            val seatCools = capabilities[FeatureKey.SEAT_HEAT]?.takeIf { it.supported }?.zones
                ?.any { (it.min ?: 0) < 0 } == true
            return entries.filterTo(mutableSetOf()) { item ->
                when (item) {
                    SEAT_HEAT -> has(FeatureKey.SEAT_HEAT)
                    SEAT_VENTILATION -> has(FeatureKey.SEAT_VENTILATION) || seatCools
                    STEERING_WHEEL_HEAT -> has(FeatureKey.STEERING_WHEEL_HEAT)
                    SIDE_MIRROR_HEAT -> has(FeatureKey.SIDE_MIRROR_HEAT)
                    AIR_CONDITIONING -> has(FeatureKey.HVAC_AC)
                    CLIMATE_POWER -> has(FeatureKey.HVAC_POWER)
                    FAN -> has(FeatureKey.HVAC_FAN_SPEED)
                    CLIMATE_AUTO -> has(FeatureKey.HVAC_AUTO)
                    RECIRCULATION -> has(FeatureKey.HVAC_RECIRCULATION)
                    DEFROST -> has(FeatureKey.HVAC_DEFROST)
                    MAX_DEFROST -> has(FeatureKey.HVAC_MAX_DEFROST)
                    MAX_AC -> has(FeatureKey.HVAC_MAX_AC)
                    DUAL_CLIMATE -> has(FeatureKey.HVAC_DUAL)
                    AIR_CLEANER -> has(FeatureKey.HVAC_AIR_CLEANER)
                    LOW_BEAM -> has(FeatureKey.LOW_BEAM)
                    HIGH_BEAM -> has(FeatureKey.HIGH_BEAM)
                    AMBIENT_LIGHT -> has(FeatureKey.AMBIENT_LIGHT)
                    WIPER -> has(FeatureKey.WIPER)
                    CRUISE -> has(FeatureKey.CRUISE)
                    ECO_MODE, SPORT_MODE -> has(FeatureKey.DRIVING_MODE)
                    WINDOW_OPEN_DRIVING -> has(FeatureKey.WINDOW_OPEN) && has(FeatureKey.DRIVING)
                    DOOR_OPEN -> has(FeatureKey.DOOR_OPEN)
                    TURN_SIGNAL -> has(FeatureKey.TURN_SIGNAL)
                    DRIVING -> has(FeatureKey.DRIVING)
                    TARGET_TEMPERATURE -> has(FeatureKey.HVAC_TEMPERATURE) && has(FeatureKey.HVAC_POWER)
                }
            }
        }
    }
}

/** One day of one item. */
data class DailySummary(
    val date: LocalDate,
    val item: ReportItem,
    val onSec: Long,
    val count: Int,
    /** Time-weighted average level, for items with levels. */
    val avgLevel: Double? = null,
)
