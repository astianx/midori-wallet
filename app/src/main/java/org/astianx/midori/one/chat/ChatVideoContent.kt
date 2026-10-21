/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.astianx.midori.one.R

private data class VideoPreview(val bitmap: android.graphics.Bitmap?, val duration: Long?)

/** Local decrypted or explicitly selected media only. No network requests and no autoplay. */
@Composable
internal fun ChatVideoContent(uri: Uri, videoNote: Boolean, durationMs: Long? = null,
    semanticsPrefix: String = "chat-video-preview", onPlay: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val hostView = LocalView.current
    val player = remember(uri) { LocalVideoPlayback(context.applicationContext, uri, videoNote) }
    val preview by produceState<VideoPreview?>(null, uri) {
        value = withContext(Dispatchers.IO) { runCatching {
            require(uri.scheme in setOf("content", "file"))
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                VideoPreview(retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 480),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull())
            }
        }.getOrNull() }
    }
    DisposableEffect(player, lifecycle, hostView) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) player.pause()
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) { player.pause() }
        }
        context.registerReceiver(receiver, android.content.IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), Context.RECEIVER_NOT_EXPORTED)
        val windowFocus = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (!focused) player.pause()
        }
        hostView.viewTreeObserver.addOnWindowFocusChangeListener(windowFocus)
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (hostView.viewTreeObserver.isAlive) hostView.viewTreeObserver.removeOnWindowFocusChangeListener(windowFocus)
            context.unregisterReceiver(receiver); player.close()
        }
    }
    LaunchedEffect(player.playing) {
        while (player.playing) { player.updatePosition(); delay(100) }
    }
    val duration = player.duration.takeIf { it > 0 } ?: (preview?.duration ?: durationMs)
        ?.takeIf { it in 1..86_400_000L }?.toInt() ?: 0
    val shape = if (videoNote) CircleShape else RoundedCornerShape(8.dp)
    val seekLabel = stringResource(R.string.chat_video_seek)
    val actionLabel = stringResource(if (player.playing || player.loading) R.string.chat_pause else R.string.chat_play)
    val progress = if (duration > 0) (player.position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (videoNote) Modifier.width(216.dp) else Modifier.fillMaxWidth()) {
        Box((if (videoNote) Modifier.size(216.dp) else Modifier.fillMaxWidth().height(200.dp))
            .testTag(semanticsPrefix)) {
            Box(Modifier.fillMaxSize().clip(shape).background(Color.Black)
                .testTag("$semanticsPrefix-toggle")
                .clickable(role = Role.Button, onClickLabel = actionLabel) {
                    if (player.playing || player.loading) player.pause() else { onPlay(); player.play() }
                }.semantics {
                    contentDescription = actionLabel
                    if (videoNote) {
                        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                        if (player.duration > 0) setProgress { fraction -> player.seek(fraction); true }
                    }
                }, contentAlignment = Alignment.Center) {
                AndroidView(factory = { viewContext -> TextureView(viewContext).also { player.attach(it) } },
                    modifier = Modifier.fillMaxSize())
                if (!player.hasFrame) preview?.bitmap?.let {
                    Image(it.asImageBitmap(), null, Modifier.fillMaxSize(),
                        contentScale = if (videoNote) ContentScale.Crop else ContentScale.Fit)
                }
                if (player.loading) CircularProgressIndicator(Modifier.size(32.dp), color = Color.White)
                else if (!player.playing) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = .46f))
                        .testTag("$semanticsPrefix-paused"), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.PlayArrow, null, Modifier.size(32.dp), tint = Color.White)
                    }
                } else {
                    // A small visible pause sign is sufficient; the entire circle remains the tap target.
                    Box(Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(28.dp)
                        .clip(CircleShape).background(Color.Black.copy(alpha = .46f))
                        .testTag("$semanticsPrefix-playing"), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Pause, null, Modifier.size(20.dp), tint = Color.White)
                    }
                }
                if (videoNote && duration > 0) {
                    Text(audioTime(if (player.playing || player.position > 0) (duration - player.position).coerceAtLeast(0) else duration),
                        style = MaterialTheme.typography.labelMedium, color = Color.White,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = .55f))
                            .padding(horizontal = 7.dp, vertical = 2.dp).testTag("$semanticsPrefix-duration"))
                }
            }
            if (videoNote) {
                // Decorative overlay: playback and seek semantics belong to the same
                // actionable circle. A separate full-size node hides Play from TalkBack.
                Canvas(Modifier.fillMaxSize()) {
                    val width = 3.dp.toPx()
                    val inset = width / 2
                    val bounds = Size(size.width - width, size.height - width)
                    drawArc(Color.White.copy(alpha = .42f), -90f, 360f, false,
                        Offset(inset, inset), bounds, style = Stroke(width))
                    if (progress > 0f) drawArc(Color.White, -90f, progress * 360f, false,
                        Offset(inset, inset), bounds, style = Stroke(width, cap = StrokeCap.Round))
                }
            }
        }
        if (!videoNote && player.duration > 0) {
            Slider(value = progress, onValueChange = player::seek,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = seekLabel })
            Text("${audioTime(player.position)} / ${audioTime(player.duration)}", style = MaterialTheme.typography.labelSmall)
        }
        if (player.failed) Text(stringResource(R.string.chat_video_unavailable), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
    }
}

/** Audio and video players in this app share a UID, so Android audio focus alone is not exclusivity. */
internal fun pauseChatVideoPlayback() { LocalVideoPlayback.pauseCurrent() }

private class LocalVideoPlayback(private val context: Context, private val uri: Uri, private val crop: Boolean) {
    var playing by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    var hasFrame by mutableStateOf(false); private set
    var duration by mutableStateOf(0); private set
    var position by mutableStateOf(0); private set
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var texture: TextureView? = null
    private var prepared = false
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { if (it != AudioManager.AUDIOFOCUS_GAIN) pause() }.build()

    fun attach(view: TextureView) {
        texture = view
        if (crop) {
            view.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) { outline.setOval(0, 0, view.width, view.height) }
            }
            view.clipToOutline = true
        }
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(value: SurfaceTexture, width: Int, height: Int) {
                texture = view; surface?.release(); surface = Surface(value)
            }
            override fun onSurfaceTextureSizeChanged(value: SurfaceTexture, width: Int, height: Int) { transform() }
            override fun onSurfaceTextureDestroyed(value: SurfaceTexture): Boolean { close(); return true }
            override fun onSurfaceTextureUpdated(value: SurfaceTexture) { if (prepared) hasFrame = true }
        }
    }
    fun play() {
        current.get()?.takeIf { it !== this }?.pause()
        current = WeakReference(this)
        if (loading) return
        if (prepared) { start(); return }
        // A pause can cancel the intent to play while prepareAsync is still running.
        // Reuse that player; replacing it would orphan its decoder and late callback.
        if (player != null) { loading = true; return }
        if (surface == null) return
        loading = true; failed = false
        try {
            require(uri.scheme in setOf("file", "content"))
            val next = MediaPlayer()
            player = next
            next.setAudioAttributes(attributes)
            next.setSurface(surface)
            next.setOnPreparedListener {
                if (player === it) {
                    prepared = true; duration = it.duration.coerceAtLeast(1); transform()
                    position = position.coerceIn(0, duration)
                    // Surface recreation releases the decoder, but does not discard progress.
                    // A completed clip is restarted by start(), only after an explicit play.
                    if (position in 1 until duration) {
                        runCatching { it.seekTo(position.toLong(), MediaPlayer.SEEK_CLOSEST) }
                            .getOrElse { fail(); return@setOnPreparedListener }
                    }
                    val shouldStart = loading; loading = false
                    if (shouldStart) start()
                }
            }
            next.setOnCompletionListener { if (it === player) { position = duration; playing = false; audio.abandonAudioFocusRequest(focus) } }
            next.setOnErrorListener { mp, _, _ -> if (player === mp) fail(); true }
            next.setDataSource(context, uri)
            next.prepareAsync()
        } catch (_: Exception) { fail() }
    }
    private fun start() {
        if (!prepared || audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        runCatching { if (position >= duration) { player?.seekTo(0); position = 0 }; player?.start(); playing = true }.onFailure { fail() }
    }
    fun pause() {
        loading = false
        if (prepared) runCatching { if (player?.isPlaying == true) player?.pause() }
        playing = false; audio.abandonAudioFocusRequest(focus)
    }
    fun seek(fraction: Float) {
        if (!prepared || !fraction.isFinite()) return
        position = (fraction.coerceIn(0f, 1f) * duration).toInt()
        runCatching { player?.seekTo(position.toLong(), MediaPlayer.SEEK_CLOSEST) }.onFailure { fail() }
    }
    fun updatePosition() { runCatching { if (prepared) position = player?.currentPosition ?: 0 }.onFailure { fail() } }
    private fun transform() {
        val view = texture ?: return
        val width = player?.videoWidth?.takeIf { it > 0 } ?: return
        val height = player?.videoHeight?.takeIf { it > 0 } ?: return
        if (view.width == 0 || view.height == 0) return
        val sx = view.width.toFloat() / width; val sy = view.height.toFloat() / height
        val scale = if (crop) maxOf(sx, sy) else minOf(sx, sy)
        view.setTransform(Matrix().apply { setScale(width * scale / view.width, height * scale / view.height,
            view.width / 2f, view.height / 2f) })
    }
    private fun fail() { failed = true; releasePlayer() }
    private fun releasePlayer() {
        if (prepared && position < duration) runCatching {
            player?.currentPosition?.let { position = it.coerceIn(0, duration) }
        }
        pause(); player?.release(); player = null; prepared = false
    }
    fun close() {
        releasePlayer(); surface?.release(); surface = null; texture = null; hasFrame = false
        if (current.get() === this) current.clear()
    }
    companion object {
        private var current = WeakReference<LocalVideoPlayback>(null)
        fun pauseCurrent() { current.get()?.pause() }
    }
}
