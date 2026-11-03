package com.example.hyundaihackathon.collector

import com.example.hyundaihackathon.data.CounterEvent
import com.example.hyundaihackathon.data.Interval
import com.example.hyundaihackathon.data.repo.UsageRepository

/**
 * Keeps changed intervals and counter events in memory and writes them in one batch,
 * either when [maxPending] rows are waiting or when [flush] is called. An interval
 * opened and closed between two flushes is written once, in its final state.
 * Not thread-safe.
 */
class EventBuffer(private val usage: UsageRepository, private val maxPending: Int = 50) {
    private val intervals = LinkedHashMap<String, Interval>()
    private val counters = mutableListOf<CounterEvent>()

    val pendingCount: Int get() = intervals.size + counters.size

    suspend fun add(changed: List<Interval>) {
        for (interval in changed) intervals[interval.id] = interval
        flushIfFull()
    }

    suspend fun add(counter: CounterEvent) {
        counters += counter
        flushIfFull()
    }

    suspend fun flush() {
        if (pendingCount == 0) return
        val intervalBatch = intervals.values.toList()
        val counterBatch = counters.toList()
        intervals.clear()
        counters.clear()
        try {
            usage.save(intervalBatch, counterBatch)
        } catch (e: Exception) {
            // Keep the rows for the next flush, without overwriting anything newer.
            for (interval in intervalBatch) intervals.putIfAbsent(interval.id, interval)
            counters.addAll(0, counterBatch)
            throw e
        }
    }

    private suspend fun flushIfFull() {
        if (pendingCount >= maxPending) flush()
    }
}
