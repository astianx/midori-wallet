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
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatMediaUxTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun pickerResultsCannotMoveToAnotherConversationOrAccount() = runBlocking {
        // Isolated records and dispatcher: no live chat account, network or wallet access.
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val records = object : ChatRecords {
            private val values = mutableMapOf<String, String>()
            override fun get(key: String) = values[key]
            override fun put(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
            override fun keys(prefix: String) = values.keys.filter { it.startsWith(prefix) }.toSet()
        }
        lateinit var vm: ChatViewModel
        val store = androidx.lifecycle.ViewModelStore()
        val created = mutableListOf<File>()
        fun capture() = ChatPlatform.captureFile(context, "txt").apply { writeText("isolated attachment selection") }.also(created::add)
        instrumentation.runOnMainSync {
            vm = ChatViewModel(records, { error("This isolated test must not create a transport") }, io = dispatcher, media = ChatMedia(context))
            store.put("media-selection", vm)
        }
        try {
            val a = "selection-a@example.org"
            val b = "selection-b@example.org"
            vm.open(a).join()
            val selected = capture()
            vm.selectMedia(Uri.fromFile(selected), expectedPeer = a).join()
            assertNotNull(vm.state.value.pendingMedia)
            vm.open(b).join()
            assertNull(vm.state.value.pendingMedia)
            assertFalse("Leaving A must discard its unsent capture", selected.exists())

            val late = capture()
            vm.selectMedia(Uri.fromFile(late), expectedPeer = a).join()
            assertNull(vm.state.value.pendingMedia)
            assertFalse("A late callback for A must not install into B", late.exists())

            vm.open(a).join()
            val gate = java.util.concurrent.CountDownLatch(1)
            executor.submit { gate.await(5, java.util.concurrent.TimeUnit.SECONDS) }
            val switch = vm.open(b)
            val queued = capture()
            val selection = vm.selectMedia(Uri.fromFile(queued)) // Defaults capture A before queued IO runs.
            gate.countDown()
            switch.join(); selection.join()
            assertEquals(b, vm.state.value.selectedPeer)
            assertNull(vm.state.value.pendingMedia)
            assertFalse(queued.exists())

            val anotherAccount = capture()
            vm.selectMedia(Uri.fromFile(anotherAccount), expectedPeer = b, expectedAccount = "prior-account@example.org").join()
            assertNull(vm.state.value.pendingMedia)
            assertFalse(anotherAccount.exists())
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            dispatcher.close()
            created.forEach { it.delete() }
        }
    }

    @Test fun discardingCaptureCannotDeleteAnExternalOrDownloadedFile() {
        val media = ChatMedia(context)
        val capture = ChatPlatform.captureFile(context, "mp4").apply { writeBytes(byteArrayOf(1)) }
        val providerCapture = ChatPlatform.captureFile(context, "jpg").apply { writeBytes(byteArrayOf(1)) }
        val external = File.createTempFile("user-document-", ".jpg", context.cacheDir).apply { writeBytes(byteArrayOf(1)) }
        val downloaded = File(context.cacheDir, "chat-media/${java.util.UUID.randomUUID()}").apply { mkdirs() }
            .resolve("download.mp4").apply { writeBytes(byteArrayOf(1)) }
        fun selection(uri: Uri) = MediaSelection(uri.toString(), "selection", "application/octet-stream", 1)
        try {
            assertFalse(media.removeOwnCapture(selection(Uri.fromFile(external))))
            assertFalse(media.removeOwnCapture(selection(Uri.fromFile(downloaded))))
            assertFalse(media.removeOwnCapture(selection(Uri.parse("content://unrelated.provider/chat_preview/capture/${providerCapture.name}"))))
            assertTrue(external.exists()); assertTrue(downloaded.exists()); assertTrue(providerCapture.exists())
            assertTrue(media.removeOwnCapture(selection(Uri.fromFile(capture))))
            assertTrue(media.removeOwnCapture(selection(ChatPlatform.uri(context, providerCapture))))
            assertFalse(capture.exists()); assertFalse(providerCapture.exists())
        } finally { capture.delete(); providerCapture.delete(); external.delete(); downloaded.delete(); downloaded.parentFile?.delete() }
    }

    @Test fun selectablePhotoQualityUsesOriginalPixelsAndLeavesSourceUntouched() {
        val source = Bitmap.createBitmap(3000, 2000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE); setHasAlpha(false) }
        val original = ChatPlatform.captureFile(context, "jpg")
        val exports = mutableListOf<File>()
        try {
            original.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
            val before = ChatFileCrypto.hash(original.readBytes())
            val media = ChatMedia(context)
            val selected = media.describe(Uri.fromFile(original))
            val standard = media.exportImage(selected, PhotoEdits(), PhotoQuality.STANDARD).also(exports::add)
            val hd = media.exportImage(selected, PhotoEdits(), PhotoQuality.HD).also(exports::add)
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(standard.absolutePath, bounds)
            assertEquals(1280, bounds.outWidth)
            assertEquals(853, bounds.outHeight)
            android.graphics.BitmapFactory.decodeFile(hd.absolutePath, bounds)
            assertEquals(2560, bounds.outWidth)
            assertEquals(1707, bounds.outHeight)
            assertEquals(before, ChatFileCrypto.hash(original.readBytes()))
            assertTrue(standard.length() in 1..ChatFileCrypto.MAX_BYTES)
            assertTrue(hd.length() in 1..ChatFileCrypto.MAX_BYTES)
            assertTrue(media.describe(Uri.fromFile(original), asDocument = true).asDocument)
        } finally { exports.forEach { it.delete() }; original.delete(); source.recycle() }
    }

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
