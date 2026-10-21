/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoiceRecordingSessionTest {
    @get:Rule val directory = TemporaryFolder()

    private class Device : VoiceRecorderDevice {
        var starts = 0
        var pauses = 0
        var resumes = 0
        var stops = 0
        var releases = 0
        var pauseError = false
        var stopError = false
        override fun start() { starts++ }
        override fun pause() { pauses++; if (pauseError) error("Recording unavailable") }
        override fun resume() { resumes++ }
        override fun stop() { stops++; if (stopError) error("No audio samples") }
        override fun release() { releases++ }
    }

    private fun capture(): File = directory.newFile("voice.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test fun pausedTimeIsExcludedAcrossReadingAndBackgroundIntervals() {
        val device = Device()
        var time = 10_000L
        val recording = VoiceRecordingSession(device, capture(), { time })
        recording.start()
        time += 2_300
        recording.pause()
        time += 60_000
        recording.updateDuration()
        assertEquals(2_300L, recording.state.value.elapsedMillis)
        recording.resume()
        time += 1_700
        recording.pause()
        time += 30_000
        recording.updateDuration()
        assertEquals(4_000L, recording.state.value.elapsedMillis)
        assertEquals(VoiceRecordingPhase.PAUSED, recording.state.value.phase)
        assertEquals(1, device.resumes)
    }

    @Test fun durationLimitPausesWithoutSendingOrDestroyingTheDraft() {
        val device = Device()
        val file = capture()
        var time = 0L
        val recording = VoiceRecordingSession(device, file, { time }, limitMillis = 5_000)
        recording.start()
        time = 8_000
        recording.updateDuration()
        recording.resume()
        assertTrue(recording.state.value.limitReached)
        assertEquals(5_000L, recording.state.value.elapsedMillis)
        assertEquals(VoiceRecordingPhase.PAUSED, recording.state.value.phase)
        assertEquals(0, device.stops)
        assertEquals(0, device.resumes)
        assertTrue(file.exists())
    }

    @Test fun uploadFailureRetainsTheSameFinalizedCaptureForAnExplicitRetry() {
        val device = Device()
        val file = capture()
        var time = 0L
        val recording = VoiceRecordingSession(device, file, { time })
        recording.start()
        time = 2_000
        assertEquals(file, recording.beginSend())
        assertNull(recording.beginSend())
        recording.completeSend(false)
        assertTrue(file.exists())
        assertEquals(VoiceRecordingPhase.READY, recording.state.value.phase)
        recording.resume()
        assertEquals(0, device.resumes)
        assertEquals(file, recording.beginSend())
        assertEquals(1, device.stops)
        assertEquals(1, device.releases)
        recording.completeSend(true)
        assertFalse(file.exists())
    }

    @Test fun navigationDuringUploadDefersDeletionUntilTheSenderFinishes() {
        val device = Device()
        val file = capture()
        var time = 0L
        val recording = VoiceRecordingSession(device, file, { time })
        recording.start()
        time = 2_000
        recording.beginSend()
        recording.discard()
        assertTrue(file.exists())
        recording.completeSend(false)
        assertFalse(file.exists())
        assertEquals(VoiceRecordingPhase.FINISHED, recording.state.value.phase)
        assertEquals(1, device.releases)
    }

    @Test fun cancellingAPausedCaptureReleasesAndDeletesExactlyOnce() {
        val device = Device()
        val file = capture()
        var time = 0L
        val recording = VoiceRecordingSession(device, file, { time })
        recording.start()
        time = 2_000
        recording.pause()
        recording.discard()
        recording.discard()
        recording.resume()
        recording.start()
        assertFalse(file.exists())
        assertEquals(1, device.starts)
        assertEquals(1, device.stops)
        assertEquals(1, device.releases)
    }

    @Test fun captureTooShortCannotBeSentAndCanStillContinue() {
        val device = Device()
        val file = capture()
        var time = 0L
        val recording = VoiceRecordingSession(device, file, { time })
        recording.start()
        time = 300
        assertNull(recording.beginSend())
        assertEquals(VoiceRecordingPhase.RECORDING, recording.state.value.phase)
        assertEquals(0, device.stops)
        time = 1_500
        assertEquals(file, recording.beginSend())
    }

    @Test fun nativePauseFailureCannotLeaveTheMicrophoneRunning() {
        val device = Device().apply { pauseError = true }
        val file = capture()
        val recording = VoiceRecordingSession(device, file, { 0L })
        recording.start()
        recording.pause()
        assertEquals(VoiceRecordingPhase.FAILED, recording.state.value.phase)
        assertEquals(1, device.releases)
        assertFalse(file.exists())
    }

    @Test fun stopFailureNeverReturnsAnInvalidAudioFile() {
        val device = Device().apply { stopError = true }
        val file = capture()
        var time = 0L
        val recording = VoiceRecordingSession(device, file, { time })
        recording.start()
        time = 1_500
        assertNull(recording.beginSend())
        assertEquals(VoiceRecordingPhase.FAILED, recording.state.value.phase)
        assertEquals(1, device.releases)
        assertFalse(file.exists())
    }
}
