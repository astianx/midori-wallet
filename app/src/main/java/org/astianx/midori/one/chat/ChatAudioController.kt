package org.astianx.midori.one.chat

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One foreground player for the conversation and its attachment preview. Main-thread only. */
internal class ChatAudioController(context: Context) {
    var path by mutableStateOf<String?>(null); private set
    var playing by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    var position by mutableStateOf(0); private set
    var duration by mutableStateOf(0); private set
    private var player: MediaPlayer? = null
    private var prepared = false
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener { if (it != AudioManager.AUDIOFOCUS_GAIN) pause() }.build()

    fun toggle(source: String) {
        if (source == path && !failed) {
            if (playing || loading) pause() else if (prepared) start()
            return
        }
        release()
        path = source; failed = false; loading = true; position = 0; duration = 0
        try {
            val next = MediaPlayer()
            player = next
            next.setAudioAttributes(attributes)
            next.setOnPreparedListener {
                if (player === it) {
                    prepared = true; duration = it.duration.coerceAtLeast(0)
                    // A lifecycle/focus pause while preparing must not start audio in the background.
                    val shouldStart = loading
                    loading = false
                    if (shouldStart) start()
                }
            }
            next.setOnCompletionListener { if (player === it) { playing = false; position = duration; abandonFocus() } }
            next.setOnErrorListener { mp, _, _ -> if (player === mp) fail(); true }
            next.setDataSource(source)
            next.prepareAsync()
        } catch (_: Exception) { fail() }
    }
    private fun start() {
        if (!prepared) return
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        try {
            if (position >= duration) { player?.seekTo(0); position = 0 }
            player?.start(); playing = true
        } catch (_: Exception) { fail() }
    }
    fun pause() {
        loading = false
        if (prepared) runCatching { if (player?.isPlaying == true) player?.pause() }
        playing = false; abandonFocus()
    }
    fun seek(fraction: Float) {
        if (!prepared || !fraction.isFinite()) return
        position = (duration * fraction.coerceIn(0f, 1f)).toInt()
        runCatching { player?.seekTo(position.toLong(), MediaPlayer.SEEK_CLOSEST) }.onFailure { fail() }
    }
    fun updatePosition() {
        if (playing && prepared) runCatching { position = player?.currentPosition ?: 0 }.onFailure { fail() }
    }
    private fun fail() { failed = true; release() }
    private fun abandonFocus() { audio.abandonAudioFocusRequest(focus) }
    fun release() {
        loading = false; playing = false; prepared = false
        player?.release(); player = null; abandonFocus()
    }
}

internal fun audioTime(milliseconds: Int): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return java.lang.String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}
