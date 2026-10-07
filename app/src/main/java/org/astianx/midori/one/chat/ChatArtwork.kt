package org.astianx.midori.one.chat

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.util.UUID
import org.astianx.midori.one.R
import org.astianx.midori.one.UiText

/** Private local artwork selected by the user. Never uploaded, exposed via FileProvider or exported in themes. */
internal class ChatArtwork(private val context: Context) {
    private val directory get() = File(context.filesDir, "chat-artwork").apply { mkdirs() }
    fun file(name: String): File {
        require(name.matches(Regex("[a-f0-9-]{36}\\.jpg")))
        return File(directory, name)
    }
    fun import(uri: Uri): String {
        if (uri.scheme != "content") throw ChatException(UiText(R.string.style_image_error))
        val inputFile = File.createTempFile("artwork-", ".tmp", context.cacheDir)
        val destination = file("${UUID.randomUUID()}.jpg")
        try {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                inputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192); var total = 0
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        total += n; require(total <= 10 * 1024 * 1024)
                        output.write(buffer, 0, n)
                    }
                }
            }
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(inputFile)) { decoder, info, _ ->
                require(info.size.width > 0 && info.size.height > 0)
                val ratio = minOf(1.0, 1600.0 / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize(maxOf(1, (info.size.width * ratio).toInt()), maxOf(1, (info.size.height * ratio).toInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            try { destination.outputStream().use { require(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)) } }
            finally { bitmap.recycle() }
            return destination.name
        } catch (e: Exception) {
            destination.delete()
            throw ChatException(UiText(R.string.style_image_error))
        } finally { inputFile.delete() }
    }
}
