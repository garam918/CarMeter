package com.garam.hackathon.collector

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The collector's only timer. It calls [onBeat] every [periodMs] until stopped. */
class Heartbeat(private val periodMs: Long, private val onBeat: suspend () -> Unit) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                delay(periodMs)
                onBeat()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
