package com.garam.hackathon.vehicle

/**
 * A vehicle feature the collector records. The enum name is stored in the database,
 * so never rename an entry once it has shipped.
 */
enum class FeatureKey(val category: FeatureCategory, val kind: FeatureKind = FeatureKind.INTERVAL) {
    HVAC_POWER(FeatureCategory.CLIMATE),
    HVAC_AC(FeatureCategory.CLIMATE),

    /** Target temperature in tenths of a degree Celsius. */
    HVAC_TEMPERATURE(FeatureCategory.CLIMATE),
    HVAC_FAN_SPEED(FeatureCategory.CLIMATE),
    HVAC_RECIRCULATION(FeatureCategory.CLIMATE),

    /** Zone is a window area (front or rear windshield). */
    HVAC_DEFROST(FeatureCategory.CLIMATE),
    HVAC_AUTO(FeatureCategory.CLIMATE),
    HVAC_MAX_AC(FeatureCategory.CLIMATE),
    HVAC_MAX_DEFROST(FeatureCategory.CLIMATE),
    HVAC_DUAL(FeatureCategory.CLIMATE),
    HVAC_AIR_CLEANER(FeatureCategory.CLIMATE),
    STEERING_WHEEL_HEAT(FeatureCategory.CLIMATE),
    SIDE_MIRROR_HEAT(FeatureCategory.CLIMATE),

    /** Raw `HvacSeatTemperature`: positive is heating; on the emulator negative is ventilation. */
    SEAT_HEAT(FeatureCategory.SEAT),
    SEAT_VENTILATION(FeatureCategory.SEAT),

    LOW_BEAM(FeatureCategory.LIGHT),
    HIGH_BEAM(FeatureCategory.LIGHT),
    AMBIENT_LIGHT(FeatureCategory.LIGHT),

    /** Zone 1 is left, 2 is right. */
    TURN_SIGNAL(FeatureCategory.LIGHT, FeatureKind.COUNT),

    /** Level is a [Levels] wiper code. Zone is a window area. */
    WIPER(FeatureCategory.DRIVING),

    /** Level is a [Levels] driving mode code. */
    DRIVING_MODE(FeatureCategory.DRIVING),
    CRUISE(FeatureCategory.DRIVING),

    /** Level is the window position above its closed value; zone is a window area. */
    WINDOW_OPEN(FeatureCategory.BODY),

    /** Zone is a door area. */
    DOOR_OPEN(FeatureCategory.BODY, FeatureKind.COUNT),

    /** The car requires distraction-optimized UI (gear in D or R). Trips and the recording rate use it. */
    DRIVING(FeatureCategory.SYSTEM),
}

/** Groups features for the per-category on/off setting. [SYSTEM] cannot be turned off. */
enum class FeatureCategory { CLIMATE, SEAT, LIGHT, DRIVING, BODY, SYSTEM }

enum class FeatureKind {
    /** Recorded as time spent at each level. */
    INTERVAL,

    /** Recorded as one event each time the feature turns on. */
    COUNT,
}
