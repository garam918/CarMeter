package com.example.hyundaihackathon.vehicle

/**
 * Adapts one vehicle feature to the collector. Call [checkCapability] first; the other
 * methods only cover the zones it reported as supported.
 */
interface FeatureSource {
    val featureKey: FeatureKey

    suspend fun checkCapability(): CapabilityResult

    /** Reads the current level of every supported zone. Zones that fail to read are left out. */
    suspend fun readInitialState(): List<ZoneLevel>

    /** Registers the SDK listeners. Throws [SdkError] if the SDK rejects the registration. */
    fun start(listener: FeatureListener)

    fun stop()
}

/** The current level of one zone. 0 means off; null means the vehicle sent no value. */
data class ZoneLevel(val zone: Int, val level: Int?)

interface FeatureListener {
    fun onLevel(featureKey: FeatureKey, reading: ZoneLevel)

    fun onError(featureKey: FeatureKey, error: SdkError)
}

data class CapabilityResult(
    val supported: Boolean,
    val zones: List<ZoneRange>,
    /** Why the feature is unsupported, as an [SdkError] code name. */
    val error: String? = null,
    /** False when the check failed because the vehicle service could not be reached. */
    val serviceReachable: Boolean = true,
) {
    /**
     * Whether this is a fact about the vehicle: supported, or unsupported with no error or a
     * "not supported" error. Any other error (internal, undefined, permission, timeout) says
     * nothing about the vehicle, so such a result must not be saved.
     */
    val isDefinitive: Boolean get() = supported || error == null || error in SdkError.NOT_SUPPORTED_CODES

    /** True when both describe the same support and zones, ignoring zone order. */
    fun sameAs(other: CapabilityResult): Boolean =
        supported == other.supported && zones.sortedBy { it.zone } == other.zones.sortedBy { it.zone }

    companion object {
        fun failed(error: SdkError) = CapabilityResult(
            supported = false,
            zones = emptyList(),
            error = error.code,
            serviceReachable = error !is SdkError.Connection && error !is SdkError.Timeout,
        )
    }
}

/** A supported zone and its level bounds, when the SDK reports them. */
data class ZoneRange(val zone: Int, val min: Int? = null, val max: Int? = null)
