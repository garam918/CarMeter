package com.garam.hackathon.collector

import com.garam.hackathon.data.CounterEvent
import com.garam.hackathon.vehicle.FeatureKey
import java.util.UUID

/**
 * Counts each time a feature turns on, such as a door opening. The first reading of a
 * zone only sets the baseline, and only live listener readings count, so a door that
 * was already open at start is not counted. A short off between two identical "on"
 * readings (a blinking turn signal, for example) can be merged with [mergeOffMs].
 * Not thread-safe: call it from one coroutine at a time.
 */
class CounterTracker(
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val mergeOffMs: (FeatureKey) -> Long = { 0L },
) {
    private data class Slot(val featureKey: FeatureKey, val zone: Int)

    private class State(var level: Int?, var offSince: Long?, var lastCounted: Int?)

    private val states = mutableMapOf<Slot, State>()

    /** Drops what is known about [featureKey], so its next reading sets a fresh baseline. */
    fun forget(featureKey: FeatureKey) {
        states.keys.removeAll { it.featureKey == featureKey }
    }

    /** Returns the event to save when this reading is a new use, or null. */
    fun onLevel(featureKey: FeatureKey, zone: Int, level: Int?, timestamp: Long, live: Boolean): CounterEvent? {
        val slot = Slot(featureKey, zone)
        val active = level != null && level != 0
        val state = states[slot]
        if (state == null) {
            states[slot] = State(level, if (active) null else timestamp, null)
            return null
        }

        var event: CounterEvent? = null
        val wasActive = state.level != null && state.level != 0
        if (active && level != state.level && live) {
            val offSince = state.offSince
            val bounce = !wasActive && state.lastCounted == level && offSince != null &&
                timestamp - offSince < mergeOffMs(featureKey)
            if (!bounce) {
                event = CounterEvent(newId(), featureKey, zone, timestamp)
                state.lastCounted = level
            }
        }
        if (!active && wasActive) state.offSince = timestamp
        state.level = level
        return event
    }
}
