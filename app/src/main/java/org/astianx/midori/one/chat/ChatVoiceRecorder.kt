/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.astianx.midori.one.R
import org.astianx.midori.one.UiText

/** The conversation remains scrollable while recording; no dialog or activity owns the microphone. */
@Composable
internal fun ChatVoiceRecorderBar(chat: ChatViewModel, peer: String, canSend: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val recorder = remember(peer) { ChatVoiceRecorder(context.applicationContext) }
    val state by recorder.state.collectAsState()
    val close by rememberUpdatedState(onClose)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focused = LocalWindowInfo.current.isWindowFocused

    DisposableEffect(recorder, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) recorder.pause()
        }
        lifecycle.addObserver(observer)
        recorder.start()
        onDispose { lifecycle.removeObserver(observer); recorder.close() }
    }
    LaunchedEffect(focused) { if (!focused) recorder.pause() }
    LaunchedEffect(state.phase) {
        if (state.phase == VoiceRecordingPhase.FAILED) {
            chat.showError(UiText(R.string.chat_record_failed))
            close()
        } else if (state.phase == VoiceRecordingPhase.FINISHED) close()
    }

    val working = state.phase in setOf(VoiceRecordingPhase.PREPARING, VoiceRecordingPhase.SENDING)
    val recording = state.phase == VoiceRecordingPhase.RECORDING
    val canResume = state.phase == VoiceRecordingPhase.PAUSED && !state.limitReached
    Surface(modifier = Modifier.fillMaxWidth().testTag("chat-voice-recorder"), color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Mic, null, Modifier.size(18.dp),
                    tint = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(when {
                    state.limitReached -> R.string.chat_voice_limit
                    state.phase == VoiceRecordingPhase.READY -> R.string.chat_voice_ready
                    state.phase == VoiceRecordingPhase.SENDING -> R.string.chat_voice_sending
                    state.phase == VoiceRecordingPhase.PREPARING -> R.string.chat_voice_preparing
                    recording -> R.string.chat_voice_recording
                    else -> R.string.chat_voice_paused
                }), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(audioTime(state.elapsedMillis.toInt()), Modifier.testTag("chat-voice-duration"),
                    style = MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { recorder.close(); close() }, enabled = state.phase != VoiceRecordingPhase.SENDING,
                    modifier = Modifier.size(48.dp).testTag("chat-voice-cancel")) {
                    Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.chat_voice_discard))
                }
                FilledTonalButton(onClick = { if (recording) recorder.pause() else recorder.resume() },
                    enabled = recording || canResume,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("chat-voice-pause-resume")) {
                    if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else {
                        Icon(if (recording) Icons.Outlined.Pause else Icons.Outlined.Mic, null, Modifier.size(20.dp))
                        Text(stringResource(if (recording) R.string.chat_voice_pause else R.string.chat_voice_resume),
                            Modifier.padding(start = 8.dp))
                    }
                }
                FilledIconButton(onClick = { recorder.send { file, complete -> chat.sendVoiceRecording(peer, file, complete) } },
                    enabled = canSend && !working && state.elapsedMillis >= 1_000,
                    modifier = Modifier.size(48.dp).testTag("chat-voice-send")) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.chat_voice_send))
                }
            }
        }
    }
}

/** All native recording work is serialized off the UI thread; cleanup outlives the composition. */
private class ChatVoiceRecorder(context: Context) {
    private val file = ChatPlatform.captureFile(context, "m4a")
    private val session = VoiceRecordingSession(AndroidVoiceRecorderDevice(context, file), file, SystemClock::elapsedRealtime)
    val state = session.state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val closed = AtomicBoolean(false)
    private val pauseRequested = AtomicBoolean(false)

    fun start() {
        action { session.start(); if (pauseRequested.get() || closed.get()) session.pause() }
        scope.launch {
            while (!closed.get()) { delay(200); mutex.withLock { session.updateDuration() } }
        }
    }
    fun pause() {
        // Also covers focus loss while native prepare() is still in progress.
        pauseRequested.set(true)
        action { session.pause() }
    }
    fun resume() {
        pauseRequested.set(false)
        action { if (!pauseRequested.get()) session.resume() }
    }
    fun send(submit: (File, (Boolean) -> Unit) -> Unit) = action {
        session.beginSend()?.let { submit(it, session::completeSend) }
    }
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.launch { mutex.withLock { session.discard() }; scope.cancel() }
    }
    private fun action(block: () -> Unit) = scope.launch { mutex.withLock { if (!closed.get()) block() } }
}

private class AndroidVoiceRecorderDevice(private val context: Context, private val file: File) : VoiceRecorderDevice {
    private var recorder: MediaRecorder? = null
    override fun start() {
        val next = MediaRecorder(context)
        recorder = next
        next.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64_000)
            setAudioSamplingRate(22_050)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
    }
    override fun pause() = checkNotNull(recorder).pause()
    override fun resume() = checkNotNull(recorder).resume()
    override fun stop() = checkNotNull(recorder).stop()
    override fun release() { recorder?.release(); recorder = null }
}
