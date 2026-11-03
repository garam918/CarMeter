package com.example.hyundaihackathon.vehicle

import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import android.util.Log
import com.example.hyundaihackathon.data.VehicleMetrics

/** Readings taken at trip boundaries and collector start/stop. Never polled. */
interface VehicleMetricsReader {
    /** Checks which metrics this vehicle reports and returns them. */
    suspend fun prepare(): Set<VehicleMetric>

    /** Reads every supported metric; a metric that fails to read is null. */
    suspend fun read(): VehicleMetrics
}

enum class VehicleMetric { ODOMETER, DRIVING_TIME, FUEL_ECONOMY, EV_EFFICIENCY, SOC }

class SdkVehicleMetricsReader(private val gateway: VehicleGateway) : VehicleMetricsReader {
    private val vehicle get() = gateway.vehicle
    private var supported: Set<VehicleMetric> = emptySet()

    override suspend fun prepare(): Set<VehicleMetric> {
        supported = VehicleMetric.entries.filterTo(mutableSetOf()) { metric ->
            try {
                val accessType: AccessType = gateway.await { onComplete, onFailed ->
                    val ok = { config: CapabilityConfig -> onComplete(config.accessType) }
                    when (metric) {
                        VehicleMetric.ODOMETER -> vehicle.getOdometer().checkCurrentDistanceDrivenCapabilities(ok, onFailed)
                        VehicleMetric.DRIVING_TIME -> vehicle.getOdometer().checkTripCumulativeDrivingTimeCapabilities(ok, onFailed)
                        VehicleMetric.FUEL_ECONOMY -> vehicle.getFuel().checkCurrentFuelEconomyCapabilities(ok, onFailed)
                        VehicleMetric.EV_EFFICIENCY -> vehicle.getEvBattery().checkCurrentEvEfficiencyCapabilities(ok, onFailed)
                        VehicleMetric.SOC -> vehicle.getEvBattery().checkEvBatteryStateOfChargeCapabilities(ok, onFailed)
                    }
                }
                accessType == AccessType.READ || accessType == AccessType.READ_WRITE
            } catch (e: SdkError) {
                Log.i(TAG, "$metric unsupported: ${e.code}")
                false
            }
        }
        return supported
    }

    override suspend fun read(): VehicleMetrics = VehicleMetrics(
        odometerKm = readIf(VehicleMetric.ODOMETER) { ok, fail ->
            vehicle.getOdometer().getCurrentDistanceDriven(onComplete = { km: Float? -> ok(km?.toDouble()) }, onFailed = fail)
        },
        drivingTimeMin = readIf(VehicleMetric.DRIVING_TIME) { ok, fail ->
            vehicle.getOdometer().getTripCumulativeDrivingTime(onComplete = ok, onFailed = fail)
        },
        fuelEconomy = readIf(VehicleMetric.FUEL_ECONOMY) { ok, fail ->
            vehicle.getFuel().getCurrentFuelEconomy(onComplete = { value: Float? -> ok(value?.toDouble()) }, onFailed = fail)
        },
        evEfficiency = readIf(VehicleMetric.EV_EFFICIENCY) { ok, fail ->
            vehicle.getEvBattery().getCurrentEvEfficiency(onComplete = { value: Float? -> ok(value?.toDouble()) }, onFailed = fail)
        },
        socPercent = readIf(VehicleMetric.SOC) { ok, fail ->
            vehicle.getEvBattery().getEvBatteryStateOfCharge(onComplete = ok, onFailed = fail)
        },
    )

    private suspend fun <T> readIf(
        metric: VehicleMetric,
        call: (onComplete: (T?) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
    ): T? {
        if (metric !in supported) return null
        return try {
            gateway.await(call = call)
        } catch (e: SdkError) {
            Log.w(TAG, "$metric read failed: ${e.code}")
            null
        }
    }

    private companion object {
        const val TAG = "VehicleMetrics"
    }
}
