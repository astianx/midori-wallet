package org.astianx.midori.one.chat

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder
import kotlin.math.abs
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class AudioPreview(val duration: Int, val peaks: List<Float> = emptyList())

/** Actual PCM amplitudes, never decorative/random bars. Bounded decoding for voice notes; long audio uses a seek track. */
internal object ChatWaveform {
    suspend fun read(path: String): AudioPreview {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var duration = 0
        try {
            extractor.setDataSource(path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return AudioPreview(0)
            val format = extractor.getTrackFormat(track)
            val micros = format.getLong(MediaFormat.KEY_DURATION)
            duration = (micros / 1000).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            if (duration <= 0 || duration > 180_000) return AudioPreview(duration)
            extractor.selectTrack(track)
            val decoder = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0); decoder.start()
            var inputDone = false
            var outputDone = false
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val peaks = FloatArray(48)
            val info = MediaCodec.BufferInfo()
            val deadline = android.os.SystemClock.elapsedRealtime() + 8_000
            while (!outputDone) {
                currentCoroutineContext().ensureActive()
                if (android.os.SystemClock.elapsedRealtime() > deadline) return AudioPreview(duration)
                if (!inputDone) {
                    val input = decoder.dequeueInputBuffer(5_000)
                    if (input >= 0) {
                        val buffer = requireNotNull(decoder.getInputBuffer(input))
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                        } else {
                            decoder.queueInputBuffer(input, 0, size, extractor.sampleTime, 0); extractor.advance()
                        }
                    }
                }
                when (val output = decoder.dequeueOutputBuffer(info, 5_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        pcmEncoding = decoder.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        sampleRate = decoder.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = decoder.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    else -> if (output >= 0) {
                        val buffer = decoder.getOutputBuffer(output)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset); buffer.limit(info.offset + info.size); buffer.order(ByteOrder.nativeOrder())
                            val bytesPerSample = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                            if (pcmEncoding !in setOf(AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_16BIT)) return AudioPreview(duration)
                            var sample = 0L
                            while (buffer.remaining() >= bytesPerSample) {
                                val peak = if (bytesPerSample == 4) abs(buffer.float) else abs(buffer.short.toInt()) / 32768f
                                val timestamp = info.presentationTimeUs + (sample / channels) * 1_000_000 / sampleRate
                                val bar = (timestamp * peaks.size / micros).toInt().coerceIn(peaks.indices)
                                if (peak.isFinite()) peaks[bar] = maxOf(peaks[bar], peak.coerceIn(0f, 1f))
                                sample++
                            }
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(output, false)
                    }
                }
            }
            val maximum = peaks.maxOrNull()?.coerceAtLeast(0.001f) ?: 1f
            return AudioPreview(duration, peaks.map { it / maximum })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { return AudioPreview(duration) }
        finally { codec?.release(); extractor.release() }
    }
}
