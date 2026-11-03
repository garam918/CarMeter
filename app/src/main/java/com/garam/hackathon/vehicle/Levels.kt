package com.garam.hackathon.vehicle

import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import kotlin.math.roundToInt

/**
 * Stable integer codes for SDK values. These numbers are stored in the database, so
 * never change an existing one. 0 always means off; null means the vehicle sent nothing.
 */
object Levels {
    const val ON = 1

    const val MODE_NORMAL = 1
    const val MODE_ECO = 2
    const val MODE_SPORT = 3

    /** Wiper running at an unknown speed. */
    const val WIPER_ON = 1
    const val WIPER_MIST = 2
    const val WIPER_INTERMITTENT_1 = 11
    const val WIPER_CONTINUOUS_1 = 21
    const val WIPER_AUTO = 30

    fun of(state: Boolean?): Int? = state?.let { if (it) ON else 0 }

    fun of(state: LightState?): Int? = state?.let { if (it == LightState.ON) ON else 0 }

    fun of(state: CruiseControl?): Int? = state?.let { if (it == CruiseControl.ACTIVATED) ON else 0 }

    fun of(mode: DrivingModeType?): Int? = when (mode) {
        DrivingModeType.NORMAL -> MODE_NORMAL
        DrivingModeType.ECO -> MODE_ECO
        DrivingModeType.SPORT -> MODE_SPORT
        null -> null
    }

    /** The speed of a running wiper. Levels 1 to 5 map to 11..15 and 21..25. */
    fun wiperSpeed(switch: WiperSwitch?): Int = when (switch) {
        WiperSwitch.MIST -> WIPER_MIST
        WiperSwitch.AUTO -> WIPER_AUTO
        WiperSwitch.INTERMITTENT_LEVEL_1 -> WIPER_INTERMITTENT_1
        WiperSwitch.INTERMITTENT_LEVEL_2 -> WIPER_INTERMITTENT_1 + 1
        WiperSwitch.INTERMITTENT_LEVEL_3 -> WIPER_INTERMITTENT_1 + 2
        WiperSwitch.INTERMITTENT_LEVEL_4 -> WIPER_INTERMITTENT_1 + 3
        WiperSwitch.INTERMITTENT_LEVEL_5 -> WIPER_INTERMITTENT_1 + 4
        WiperSwitch.CONTINUOUS_LEVEL_1 -> WIPER_CONTINUOUS_1
        WiperSwitch.CONTINUOUS_LEVEL_2 -> WIPER_CONTINUOUS_1 + 1
        WiperSwitch.CONTINUOUS_LEVEL_3 -> WIPER_CONTINUOUS_1 + 2
        WiperSwitch.CONTINUOUS_LEVEL_4 -> WIPER_CONTINUOUS_1 + 3
        WiperSwitch.CONTINUOUS_LEVEL_5 -> WIPER_CONTINUOUS_1 + 4
        else -> WIPER_ON
    }

    fun celsiusTenths(celsius: Float?): Int? = celsius?.let { (it * 10).roundToInt() }
}
