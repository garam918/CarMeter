package com.example.hyundaihackathon.vehicle

import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.MirrorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.AreaValue
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.MirrorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue

/**
 * A zone is stored as the area ID the SDK builds internally: the bitwise OR of the area
 * values' IDs. A capability result may return one area that covers several seats.
 */
object Zone {
    /** Features with no area, such as the steering wheel heater. */
    const val GLOBAL = 0

    const val TURN_LEFT = 1
    const val TURN_RIGHT = 2

    fun of(area: SeatArea): Int = of(area.values)

    fun of(area: WindowArea): Int = of(area.values)

    fun of(area: DoorArea): Int = of(area.values)

    fun of(area: MirrorArea): Int = of(area.values)

    private fun of(values: Array<out AreaValue>): Int = values.fold(0) { acc, value -> acc or value.id }

    /** A short Korean label for [zone] of [featureKey], for example "1열 왼쪽+2열 왼쪽". */
    fun label(featureKey: FeatureKey, zone: Int): String = when (featureKey) {
        FeatureKey.HVAC_DEFROST, FeatureKey.WIPER, FeatureKey.WINDOW_OPEN -> labels(WindowValue.entries, zone)
        FeatureKey.DOOR_OPEN -> labels(DoorValue.entries, zone)
        FeatureKey.SIDE_MIRROR_HEAT -> labels(MirrorValue.entries, zone)
        FeatureKey.TURN_SIGNAL -> if (zone == TURN_LEFT) "왼쪽" else "오른쪽"
        else -> if (zone == GLOBAL) "전체" else labels(SeatValue.entries, zone)
    }

    private fun <T> labels(values: List<T>, zone: Int): String where T : Enum<T>, T : AreaValue {
        val matched = values.filter { zone and it.id == it.id }
        if (matched.isEmpty()) return "영역 $zone"
        return matched.joinToString("+") { nameLabel(it.name) }
    }

    private fun nameLabel(name: String): String {
        if (name.startsWith("ROW_")) {
            val (_, row, side) = name.split("_")
            val sideLabel = when (side) {
                "LEFT" -> "왼쪽"
                "RIGHT" -> "오른쪽"
                else -> "가운데"
            }
            return "${row}열 $sideLabel"
        }
        return when (name) {
            "FRONT_WINDSHIELD" -> "앞유리"
            "REAR_WINDSHIELD" -> "뒷유리"
            "ROOF_TOP_1" -> "선루프 1"
            "ROOF_TOP_2" -> "선루프 2"
            "HOOD" -> "후드"
            "REAR" -> "트렁크"
            "DRIVER_LEFT" -> "왼쪽 미러"
            "DRIVER_RIGHT" -> "오른쪽 미러"
            "DRIVER_CENTER" -> "실내 미러"
            else -> name
        }
    }
}
