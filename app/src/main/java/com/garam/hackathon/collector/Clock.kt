package com.garam.hackathon.collector

/** Wall-clock time in epoch milliseconds. Tests replace it. */
fun interface Clock {
    fun now(): Long

    companion object {
        val SYSTEM = Clock { System.currentTimeMillis() }
    }
}
