package com.garam.hackathon.ui

import com.garam.hackathon.collector.CollectorStatus
import com.garam.hackathon.data.repo.CollectorSettings

/** The recording state shown at the top of the bill and in settings. */
enum class RecordingIndicator(val text: String, val active: Boolean) {
    NEEDS_CONSENT("기록하려면 동의가 필요해요", active = false),
    OFF("기록 꺼짐", active = false),
    STARTING("기록을 시작하는 중…", active = false),
    RECORDING("백그라운드 기록 중", active = true),
    UNAVAILABLE("차량에 연결하지 못했어요", active = false),
    WAITING("기록 대기 중", active = false),
    ;

    companion object {
        fun of(settings: CollectorSettings, status: CollectorStatus): RecordingIndicator = when {
            !settings.hasConsent -> NEEDS_CONSENT
            !settings.recordingEnabled -> OFF
            status is CollectorStatus.Running -> RECORDING
            status is CollectorStatus.Starting -> STARTING
            status is CollectorStatus.Failed -> UNAVAILABLE
            else -> WAITING
        }
    }
}
