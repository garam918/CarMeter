package com.example.hyundaihackathon.data

import com.example.hyundaihackathon.vehicle.FeatureKey

/** One use of a counted feature, such as a door opening. */
data class CounterEvent(
    val id: String,
    val featureKey: FeatureKey,
    val zone: Int,
    val ts: Long,
    val tripId: Long? = null,
)
