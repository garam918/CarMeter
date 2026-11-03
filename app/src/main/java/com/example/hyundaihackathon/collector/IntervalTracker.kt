package com.example.hyundaihackathon.collector

import com.example.hyundaihackathon.data.ClosedBy
import com.example.hyundaihackathon.data.Interval
import com.example.hyundaihackathon.vehicle.FeatureKey
import java.util.UUID

/**
 * Turns level readings into usage intervals, one open interval per feature and zone.
 * Level 0 and null mean off. Every call returns the intervals it opened or closed, ready
 * to be saved. Not thread-safe: call it from one coroutine at a time.
 */
class IntervalTracker(private val newId: () -> String = { UUID.randomUUID().toString() }) {
    private data class Slot(val featureKey: FeatureKey, val zone: Int)

    private val open = mutableMapOf<Slot, Interval>()

    val openIntervals: Collection<Interval> get() = open.values

    /** [tripId] tags an interval this reading opens. */
    fun onLevel(featureKey: FeatureKey, zone: Int, level: Int?, timestamp: Long, tripId: Long? = null): List<Interval> {
        val slot = Slot(featureKey, zone)
        val current = open[slot]
        val active = level != null && level != 0
        if (current == null && !active) return emptyList()
        if (current != null && current.level == level) return emptyList()

        val changed = mutableListOf<Interval>()
        var start = timestamp
        if (current != null) {
            val closed = current.close(timestamp, ClosedBy.EVENT)
            changed += closed
            open.remove(slot)
            // A late reading must not start before the interval it ends.
            start = closed.endTs!!
        }
        if (active) {
            val started = Interval(newId(), featureKey, zone, level, start, tripId = tripId)
            open[slot] = started
            changed += started
        }
        return changed
    }

    /** Closes the open intervals of one feature, for example when its category is switched off. */
    fun closeFeature(featureKey: FeatureKey, timestamp: Long, by: ClosedBy): List<Interval> {
        val slots = open.keys.filter { it.featureKey == featureKey }
        return slots.map { slot -> open.remove(slot)!!.close(timestamp, by) }
    }

    fun closeAll(timestamp: Long, by: ClosedBy): List<Interval> {
        val closed = open.values.map { it.close(timestamp, by) }
        open.clear()
        return closed
    }
}
