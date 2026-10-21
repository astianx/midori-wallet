/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File
import kotlinx.coroutines.delay
import org.astianx.midori.one.R

/** An inline front-camera recorder: the surrounding conversation remains scrollable. */
@Composable
internal fun ChatVideoNoteRecorder(onCancel: () -> Unit, onReady: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focused = LocalWindowInfo.current.isWindowFocused
    val currentReady by rememberUpdatedState(onReady)
    val currentCancel by rememberUpdatedState(onCancel)
    val controller = remember { VideoNoteCapture(context.applicationContext) { currentReady(it) } }
    var permitted by remember { mutableStateOf(listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) }
    var denied by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permitted = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).all { result[it] == true }
        denied = !permitted
    }
    LaunchedEffect(Unit) {
        if (!permitted) permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            // Capture must never continue invisibly after leaving the foreground.
            if (event == Lifecycle.Event.ON_PAUSE) controller.pause()
            if (event == Lifecycle.Event.ON_STOP && permitted) {
                when (controller.phase) {
                    VideoCapturePhase.RECORDING, VideoCapturePhase.PAUSED -> controller.finish()
                    VideoCapturePhase.FINISHING -> Unit
                    else -> { controller.close(); currentCancel() }
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.close() }
    }
    LaunchedEffect(focused) { if (!focused) controller.pause() }
    LaunchedEffect(controller.phase) {
        while (controller.phase == VideoCapturePhase.RECORDING) { controller.tick(); delay(100) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (permitted) Box(Modifier.size(184.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            AndroidView(factory = { TextureView(it).also(controller::attach) }, modifier = Modifier.size(184.dp))
            if (controller.phase == VideoCapturePhase.OPENING) CircularProgressIndicator(Modifier.size(28.dp))
        }
        when {
            denied -> Text(stringResource(R.string.chat_video_permission), style = MaterialTheme.typography.bodySmall)
            controller.phase == VideoCapturePhase.FAILED -> Text(stringResource(R.string.chat_video_record_failed), color = MaterialTheme.colorScheme.error)
            controller.phase in setOf(VideoCapturePhase.RECORDING, VideoCapturePhase.PAUSED) ->
                Text("${audioTime(controller.elapsedMs.toInt())} / 1:00", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.testTag("chat-video-duration"))
            controller.phase == VideoCapturePhase.READY -> Text(stringResource(R.string.chat_video_ready), style = MaterialTheme.typography.labelLarge)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { controller.close(); onCancel() }, modifier = Modifier.size(48.dp).testTag("chat-video-discard")) {
                Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.chat_video_cancel))
            }
            when (controller.phase) {
                VideoCapturePhase.READY -> FilledIconButton(onClick = controller::start, enabled = focused,
                    modifier = Modifier.size(48.dp).testTag("chat-video-record")) {
                    Icon(Icons.Outlined.Videocam, stringResource(R.string.chat_video_record))
                }
                VideoCapturePhase.RECORDING, VideoCapturePhase.PAUSED -> {
                    IconButton(onClick = { if (controller.phase == VideoCapturePhase.PAUSED) controller.resume() else controller.pause() },
                        enabled = focused, modifier = Modifier.size(48.dp).testTag("chat-video-pause-resume")) {
                        Icon(if (controller.phase == VideoCapturePhase.PAUSED) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            stringResource(if (controller.phase == VideoCapturePhase.PAUSED) R.string.chat_record_resume else R.string.chat_pause))
                    }
                    FilledIconButton(onClick = controller::finish, enabled = controller.elapsedMs >= 700, modifier = Modifier.size(48.dp).testTag("chat-video-finish")) {
                        Icon(Icons.Outlined.Stop, stringResource(R.string.chat_video_finish))
                    }
                }
                else -> Unit
            }
        }
        if (controller.phase == VideoCapturePhase.READY) Text(stringResource(R.string.chat_video_note_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private enum class VideoCapturePhase { OPENING, READY, RECORDING, PAUSED, FINISHING, FAILED }

/** Camera and codec work is serialized away from Compose's main thread. */
private class VideoNoteCapture(private val context: Context, private val onReady: (File) -> Unit) {
    var phase by mutableStateOf(VideoCapturePhase.OPENING); private set
    var elapsedMs by mutableStateOf(0L); private set
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("midori-video-capture").apply { start() }
    private val worker = Handler(thread.looper)
    private val manager = context.getSystemService(CameraManager::class.java)
    private val file = ChatPlatform.captureFile(context, "mp4")
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var recorder: MediaRecorder? = null
    private var previewSurface: Surface? = null
    private var recording = false
    private var paused = false
    private var prepared = false
    @Volatile private var closed = false
    @Volatile private var handedOff = false
    @Volatile private var pauseRequested = false
    private var completedMs = 0L
    private var segmentStart = 0L

    fun attach(view: TextureView) {
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) { outline.setOval(0, 0, view.width, view.height) }
        }
        view.clipToOutline = true
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) { open(view, texture) }
            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { close(); return true }
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }
    }
    @Suppress("MissingPermission", "DEPRECATION")
    private fun open(view: TextureView, texture: SurfaceTexture) {
        worker.post {
            if (closed) return@post
            try {
                val ids = manager.cameraIdList
                val id = ids.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT }
                    ?: ids.firstOrNull() ?: error("No camera")
                val characteristics = manager.getCameraCharacteristics(id)
                val front = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
                val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(MediaRecorder::class.java).orEmpty()
                val size = sizes.filter { it.width <= 1280 && it.height <= 720 && it.width >= 320 && it.height >= 240 }
                    .minByOrNull { kotlin.math.abs(it.width - 640) + kotlin.math.abs(it.height - 480) } ?: error("No recording size")
                val sensor = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                val display = when (view.display?.rotation) { Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0 }
                val orientation = (sensor + (if (front) display else -display) + 360) % 360
                texture.setDefaultBufferSize(size.width, size.height)
                previewSurface = Surface(texture)
                main.post {
                    if (!closed) {
                        val rotated = orientation % 180 != 0
                        val ratio = if (rotated) size.width.toFloat() / size.height else size.height.toFloat() / size.width
                        view.setTransform(Matrix().apply {
                            setRotate(orientation.toFloat(), view.width / 2f, view.height / 2f)
                            postScale(if (front) -maxOf(1f, ratio) else maxOf(1f, ratio), maxOf(1f, 1f / ratio), view.width / 2f, view.height / 2f)
                        })
                    }
                }
                val nativeRecorder = MediaRecorder(context)
                // Retain ownership before any configuration/prepare call can fail.
                recorder = nativeRecorder
                nativeRecorder.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    setVideoSize(size.width, size.height)
                    setVideoFrameRate(30)
                    setVideoEncodingBitRate(1_800_000)
                    setAudioEncodingBitRate(64_000)
                    setAudioSamplingRate(44_100)
                    setOrientationHint(orientation)
                    setMaxDuration(ChatMediaPolicy.VIDEO_NOTE_MAX_MS.toInt())
                    setMaxFileSize(ChatFileCrypto.MAX_BYTES)
                    setOutputFile(file)
                    setOnInfoListener { _, what, _ ->
                        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) finish()
                    }
                    setOnErrorListener { _, _, _ -> fail() }
                    prepare()
                }
                prepared = true
                if (closed) { releaseDevices(); return@post }
                manager.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        if (closed) { device.close(); return }
                        camera = device
                        try {
                            val surfaces = listOf(requireNotNull(previewSurface), requireNotNull(recorder).surface)
                            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(configured: CameraCaptureSession) {
                                    if (closed) { configured.close(); return }
                                    session = configured
                                    try {
                                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                            surfaces.forEach(::addTarget)
                                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                        }.build()
                                        configured.setRepeatingRequest(request, null, worker)
                                        publish(VideoCapturePhase.READY)
                                    } catch (_: Exception) { fail() }
                                }
                                override fun onConfigureFailed(session: CameraCaptureSession) { fail() }
                            }, worker)
                        } catch (_: Exception) { fail() }
                    }
                    override fun onDisconnected(device: CameraDevice) { device.close(); fail() }
                    override fun onError(device: CameraDevice, error: Int) { device.close(); fail() }
                }, worker)
            } catch (_: Exception) { fail() }
        }
    }
    fun start() {
        if (phase != VideoCapturePhase.READY) return
        pauseRequested = false // An explicit new recording action, never a lifecycle resume.
        publish(VideoCapturePhase.OPENING)
        worker.post {
            if (closed || !prepared || recording) return@post
            if (pauseRequested) { publish(VideoCapturePhase.READY); return@post }
            try {
                recorder?.start(); recording = true; segmentStart = SystemClock.elapsedRealtime()
                if (pauseRequested) pauseRecording() else publish(VideoCapturePhase.RECORDING)
            }
            catch (_: Exception) { fail() }
        }
    }
    fun tick() { worker.post { if (recording) updateElapsed() } }
    private fun updateElapsed() {
        val value = completedMs + if (paused) 0 else SystemClock.elapsedRealtime() - segmentStart
        main.post { elapsedMs = value.coerceAtMost(ChatMediaPolicy.VIDEO_NOTE_MAX_MS) }
    }
    fun pause() {
        // Immediate flag also covers focus loss while native start/resume is still blocked.
        pauseRequested = true
        worker.post {
            if (closed || !recording || paused) return@post
            try { pauseRecording() } catch (_: Exception) { fail() }
        }
    }
    private fun pauseRecording() {
        recorder?.pause()
        completedMs += SystemClock.elapsedRealtime() - segmentStart
        paused = true; updateElapsed(); publish(VideoCapturePhase.PAUSED)
    }
    fun resume() {
        if (phase != VideoCapturePhase.PAUSED) return
        pauseRequested = false
        worker.post {
            if (closed || !recording || !paused || pauseRequested) return@post
            try {
                recorder?.resume(); segmentStart = SystemClock.elapsedRealtime(); paused = false
                if (pauseRequested) pauseRecording() else publish(VideoCapturePhase.RECORDING)
            } catch (_: Exception) { fail() }
        }
    }
    fun finish() {
        worker.post {
            if (closed || !recording) return@post
            publish(VideoCapturePhase.FINISHING)
            recording = false
            try {
                recorder?.stop()
                releaseDevices()
                require(file.length() in 1..ChatFileCrypto.MAX_BYTES)
                // Validate an actual playable video before handing ownership to the pending-media flow.
                ChatMedia(context).describe(android.net.Uri.fromFile(file), videoNote = true)
                handedOff = true
                main.post { if (!closed) onReady(file) else file.delete() }
            } catch (_: Exception) { fail() }
        }
    }
    private fun publish(value: VideoCapturePhase) { main.post { if (!closed) phase = value } }
    private fun fail() {
        worker.post { releaseDevices(); if (!handedOff) file.delete(); publish(VideoCapturePhase.FAILED) }
    }
    private fun releaseDevices() {
        runCatching { session?.close() }; session = null
        runCatching { camera?.close() }; camera = null
        runCatching { recorder?.release() }; recorder = null
        previewSurface?.release(); previewSurface = null
        prepared = false; recording = false
    }
    fun close() {
        if (closed) return
        closed = true
        worker.post { releaseDevices(); if (!handedOff) file.delete(); thread.quitSafely() }
    }
}
