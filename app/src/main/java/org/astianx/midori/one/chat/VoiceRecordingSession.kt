/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal enum class VoiceRecordingPhase { PREPARING, RECORDING, PAUSED, READY, SENDING, FINISHED, FAILED }

internal data class VoiceRecordingState(
    val phase: VoiceRecordingPhase = VoiceRecordingPhase.PREPARING,
    val elapsedMillis: Long = 0,
    val limitReached: Boolean = false
)

internal interface VoiceRecorderDevice {
    fun start()
    fun pause()
    fun resume()
    fun stop()
    fun release()
}

/** Owns one private capture until it is either discarded or represented by a saved chat message. */
internal class VoiceRecordingSession(
    private val device: VoiceRecorderDevice,
    private val file: File,
    private val elapsedRealtime: () -> Long,
    private val limitMillis: Long = 120_000
) {
    private val mutableState = MutableStateFlow(VoiceRecordingState())
    val state: StateFlow<VoiceRecordingState> = mutableState
    private var accumulated = 0L
    private var segmentStart = 0L
    private var released = false
    private var disposed = false

    @Synchronized fun start() {
        if (disposed || state.value.phase != VoiceRecordingPhase.PREPARING) return
        attempt {
            device.start()
            segmentStart = elapsedRealtime()
            mutableState.value = state.value.copy(phase = VoiceRecordingPhase.RECORDING)
        }
    }

    @Synchronized fun pause() {
        if (state.value.phase != VoiceRecordingPhase.RECORDING) return
        attempt {
            device.pause()
            accumulated = duration()
            mutableState.value = state.value.copy(phase = VoiceRecordingPhase.PAUSED,
                elapsedMillis = accumulated, limitReached = accumulated >= limitMillis)
        }
    }

    @Synchronized fun resume() {
        if (disposed || state.value.phase != VoiceRecordingPhase.PAUSED || state.value.limitReached) return
        attempt {
            device.resume()
            segmentStart = elapsedRealtime()
            mutableState.value = state.value.copy(phase = VoiceRecordingPhase.RECORDING)
        }
    }

    @Synchronized fun updateDuration() {
        if (state.value.phase != VoiceRecordingPhase.RECORDING) return
        val elapsed = duration()
        if (elapsed >= limitMillis) pause()
        else mutableState.value = state.value.copy(elapsedMillis = elapsed)
    }

    /** Finalizing releases the microphone. An upload failure keeps this same file for explicit retry. */
    @Synchronized fun beginSend(): File? {
        if (disposed || state.value.phase !in setOf(VoiceRecordingPhase.RECORDING, VoiceRecordingPhase.PAUSED, VoiceRecordingPhase.READY)) return null
        if (duration() < 1_000) return null
        return try {
            if (state.value.phase != VoiceRecordingPhase.READY) {
                accumulated = duration()
                device.stop()
                release()
                check(file.isFile && file.length() > 0)
            }
            mutableState.value = state.value.copy(phase = VoiceRecordingPhase.SENDING, elapsedMillis = accumulated)
            file
        } catch (_: Exception) {
            fail()
            null
        }
    }

    @Synchronized fun completeSend(accepted: Boolean) {
        if (state.value.phase != VoiceRecordingPhase.SENDING) return
        if (accepted || disposed) {
            file.delete()
            mutableState.value = state.value.copy(phase = VoiceRecordingPhase.FINISHED)
        } else mutableState.value = state.value.copy(phase = VoiceRecordingPhase.READY)
    }

    @Synchronized fun discard() {
        disposed = true
        // A send already owns an open file; its completion handles cleanup even after navigation.
        if (state.value.phase == VoiceRecordingPhase.SENDING) return
        if (!released && state.value.phase in setOf(VoiceRecordingPhase.RECORDING, VoiceRecordingPhase.PAUSED)) {
            runCatching { device.stop() }
        }
        release()
        file.delete()
        mutableState.value = state.value.copy(phase = VoiceRecordingPhase.FINISHED)
    }

    private fun duration(): Long = (accumulated + if (state.value.phase == VoiceRecordingPhase.RECORDING)
        (elapsedRealtime() - segmentStart).coerceAtLeast(0) else 0).coerceAtMost(limitMillis)

    private fun attempt(action: () -> Unit) {
        try { action() } catch (_: Exception) { fail() }
    }

    private fun fail() {
        release()
        file.delete()
        mutableState.value = state.value.copy(phase = VoiceRecordingPhase.FAILED)
    }

    private fun release() {
        if (!released) {
            released = true
            runCatching { device.release() }
        }
    }
}
