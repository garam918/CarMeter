package com.example.hyundaihackathon.ui

import com.example.hyundaihackathon.vehicle.FeatureKey
import com.example.hyundaihackathon.vehicle.Levels

/** The Korean name shown for each feature. */
val FeatureKey.label: String
    get() = when (this) {
        FeatureKey.HVAC_POWER -> "공조 전원"
        FeatureKey.HVAC_AC -> "에어컨"
        FeatureKey.HVAC_TEMPERATURE -> "설정 온도"
        FeatureKey.HVAC_FAN_SPEED -> "풍량"
        FeatureKey.HVAC_RECIRCULATION -> "내기순환"
        FeatureKey.HVAC_DEFROST -> "성에 제거"
        FeatureKey.HVAC_AUTO -> "공조 AUTO"
        FeatureKey.HVAC_MAX_AC -> "MAX 에어컨"
        FeatureKey.HVAC_MAX_DEFROST -> "MAX 성에 제거"
        FeatureKey.HVAC_DUAL -> "듀얼 공조"
        FeatureKey.HVAC_AIR_CLEANER -> "공기청정"
        FeatureKey.STEERING_WHEEL_HEAT -> "핸들 열선"
        FeatureKey.SIDE_MIRROR_HEAT -> "사이드미러 열선"
        FeatureKey.SEAT_HEAT -> "시트 열선·통풍"
        FeatureKey.SEAT_VENTILATION -> "시트 통풍"
        FeatureKey.LOW_BEAM -> "전조등(하향)"
        FeatureKey.HIGH_BEAM -> "상향등"
        FeatureKey.AMBIENT_LIGHT -> "앰비언트 라이트"
        FeatureKey.TURN_SIGNAL -> "방향지시등"
        FeatureKey.WIPER -> "와이퍼"
        FeatureKey.DRIVING_MODE -> "드라이브 모드"
        FeatureKey.CRUISE -> "크루즈"
        FeatureKey.WINDOW_OPEN -> "창문 열림"
        FeatureKey.DOOR_OPEN -> "문 열림"
        FeatureKey.DRIVING -> "주행"
    }

/** A readable level, for example "22.5°C" or "에코". */
fun levelLabel(featureKey: FeatureKey, level: Int): String = when (featureKey) {
    FeatureKey.HVAC_TEMPERATURE -> "%.1f°C".format(level / 10.0)
    FeatureKey.DRIVING_MODE -> when (level) {
        Levels.MODE_NORMAL -> "일반"
        Levels.MODE_ECO -> "에코"
        Levels.MODE_SPORT -> "스포츠"
        else -> "모드 $level"
    }
    FeatureKey.SEAT_HEAT -> if (level < 0) "통풍 ${-level}단" else "열선 ${level}단"
    else -> "단계 $level"
}
