package com.example.hyundaihackathon.ui

import com.example.hyundaihackathon.collector.CollectorStatus
import com.example.hyundaihackathon.data.repo.CollectorSettings
import com.example.hyundaihackathon.vehicle.FeatureCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingIndicatorTest {
    private val consented = CollectorSettings(consentAt = 1L)
    private val running = CollectorStatus.Running(emptyMap(), emptySet())

    @Test
    fun `consent comes before anything else`() {
        assertEquals(RecordingIndicator.NEEDS_CONSENT, RecordingIndicator.of(CollectorSettings(), running))
    }

    @Test
    fun `the master switch overrides the engine state`() {
        assertEquals(RecordingIndicator.OFF, RecordingIndicator.of(consented.copy(recordingEnabled = false), running))
    }

    @Test
    fun `otherwise the engine state shows`() {
        assertEquals(RecordingIndicator.RECORDING, RecordingIndicator.of(consented, running))
        assertEquals(RecordingIndicator.STARTING, RecordingIndicator.of(consented, CollectorStatus.Starting))
        assertEquals(RecordingIndicator.UNAVAILABLE, RecordingIndicator.of(consented, CollectorStatus.Failed(emptyMap())))
        assertEquals(RecordingIndicator.WAITING, RecordingIndicator.of(consented, CollectorStatus.Stopped))
    }

    @Test
    fun `the system category can never be switched off`() {
        val settings = consented.copy(disabledCategories = setOf(FeatureCategory.SYSTEM, FeatureCategory.SEAT))

        assertTrue(settings.isEnabled(FeatureCategory.SYSTEM))
        assertFalse(settings.isEnabled(FeatureCategory.SEAT))
        assertTrue(settings.canRecord)
        assertFalse(CollectorSettings().canRecord)
    }
}
