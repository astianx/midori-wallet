package org.astianx.midori.one.chat

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatMediaUxTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun photoExportCropsAndCompositesMarksWithoutChangingOriginal() {
        val original = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val edits = PhotoEdits(PhotoRect(.25f, .25f, .75f, .75f), listOf(
            PhotoMark.Stroke(listOf(PhotoPoint(.3f, .5f), PhotoPoint(.7f, .5f)), Color.RED),
            PhotoMark.Label("Hi", PhotoPoint(.5f, .42f), Color.WHITE)))
        var file: File? = null
        try {
            file = ChatPhotoEditor.export(context, original, edits)
            val sent = ChatPhotoEditor.decode(context, Uri.fromFile(file))
            assertEquals(200, sent.width); assertEquals(100, sent.height)
            assertEquals(Color.BLUE, original.getPixel(200, 100))
            assertEquals(Color.RED, sent.getPixel(100, 50))
            val before = ChatPhotoEditor.render(original, PhotoEdits())
            assertEquals(400, before.width); assertEquals(Color.BLUE, before.getPixel(200, 100))
            val pixels = IntArray(sent.width * sent.height)
            sent.getPixels(pixels, 0, sent.width, 0, 0, sent.width, sent.height)
            assertTrue("Text must be composited in exported pixels", pixels.any { Color.red(it) > 200 && Color.green(it) > 200 && Color.blue(it) > 200 })
            sent.recycle(); before.recycle()
            assertNull(PhotoRect.between(PhotoPoint(.4f, .4f), PhotoPoint(.4f, .4f)))
            assertEquals(PhotoRect(.2f, .2f, .8f, .8f), PhotoRect.between(PhotoPoint(.8f, .8f), PhotoPoint(.2f, .2f)))
        } finally { file?.delete(); original.recycle() }
    }

    @Test fun actualWaveformAndPlayerSupportSeekingPausingAndSwitchingFiles() = runBlocking {
        val first = waveFile(); val second = waveFile()
        var controller: ChatAudioController? = null
        try {
            val waveform = ChatWaveform.read(first.absolutePath)
            assertTrue(waveform.duration in 2900..3100)
            assertEquals(48, waveform.peaks.size)
            assertTrue("PCM amplitude should increase in the second half", waveform.peaks.take(20).average() < waveform.peaks.takeLast(20).average() / 2)
            instrumentation.runOnMainSync { controller = ChatAudioController(context); controller!!.toggle(first.absolutePath) }
            waitFor { controller!!.duration > 0 && !controller!!.loading }
            instrumentation.runOnMainSync { controller!!.seek(.5f); controller!!.pause() }
            assertFalse(controller!!.playing); assertTrue(controller!!.position in 1400..1600)
            instrumentation.runOnMainSync { controller!!.toggle(second.absolutePath) }
            waitFor { controller!!.path == second.absolutePath && controller!!.duration > 0 && !controller!!.loading }
            instrumentation.runOnMainSync { controller!!.pause() }
            assertFalse(controller!!.playing); assertFalse(controller!!.failed)
            // Pausing before async preparation completes must also suppress autoplay.
            instrumentation.runOnMainSync { controller!!.toggle(first.absolutePath); controller!!.pause() }
            waitFor { controller!!.duration > 0 }
            assertFalse(controller!!.playing)
        } finally {
            instrumentation.runOnMainSync { controller?.release() }; first.delete(); second.delete()
        }
    }
    private fun waitFor(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
        var ready = false
        while (!ready && android.os.SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync { ready = condition() }
            if (!ready) Thread.sleep(30)
        }
        assertTrue("Player did not prepare", ready)
    }
    private fun waveFile(): File {
        val samples = 48_000
        val bytes = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16_000).putInt(32_000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { i -> bytes.putShort((sin(2 * Math.PI * 440 * i / 16_000) * if (i < samples / 2) 2_000 else 16_000).toInt().toShort()) }
        return File.createTempFile("media-ux-", ".wav", context.cacheDir).apply { writeBytes(bytes.array()) }
    }
}
