package com.garam.hackathon.demo

import android.content.Context

/**
 * The submitted build has no demo data. The demo flavor replaces this object with one
 * that can write a synthetic month (src/demo).
 */
object Demo {
    const val IS_AVAILABLE = false

    @Suppress("UNUSED_PARAMETER")
    suspend fun loadSyntheticMonths(context: Context): String = throw UnsupportedOperationException("No demo data in this build")
}
