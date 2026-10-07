package org.astianx.midori.one.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import org.astianx.midori.one.BuildConfig
import org.astianx.midori.one.R
import org.astianx.midori.one.UiText

/** User-selected files only. Ciphertext on the server; authenticated decryption before preview/share. */
internal class ChatMedia(private val context: Context,
    private val pin: String = if (BuildConfig.DEBUG) BuildConfig.CHAT_DEV_CERT_SHA256 else "") {
    fun describe(uri: Uri): MediaSelection {
        require(uri.scheme in setOf("content", "file"))
        var name = "attachment"; var size = -1L
        if (uri.scheme == "content") context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) {
                name = it.getString(0) ?: name
                if (!it.isNull(1)) size = it.getLong(1)
            }
        } else {
            val file = File(requireNotNull(uri.path))
            require(file.canonicalPath.startsWith(File(context.cacheDir, "chat-media").canonicalPath + File.separator))
            name = file.name; size = file.length()
        }
        if (size > ChatFileCrypto.MAX_BYTES || size == 0L) throw ChatException(UiText(R.string.chat_file_too_large))
        val mime = context.contentResolver.getType(uri) ?: when (name.substringAfterLast('.').lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "m4a" -> "audio/mp4"; else -> "application/octet-stream"
        }
        val ownCapture = (uri.scheme == "file" || uri.authority == "${context.packageName}.chat.files") &&
            uri.path.orEmpty().contains("/capture/")
        if (ownCapture && mime in setOf("image/jpeg", "audio/mp4")) {
            val label = context.getString(if (mime.startsWith("audio/")) R.string.chat_voice else R.string.chat_camera)
            val time = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date())
            name = "$label $time.${if (mime.startsWith("audio/")) "m4a" else "jpg"}"
        }
        return MediaSelection(uri.toString(), ChatPayload.safeName(name), mime, size)
    }
    fun previewCopy(selection: MediaSelection): File {
        val bytes = context.contentResolver.openInputStream(Uri.parse(selection.uri))?.use { limitedRead(it, ChatFileCrypto.MAX_BYTES) }
            ?: throw ChatException(UiText(R.string.chat_file_unavailable))
        val directory = File(context.cacheDir, "chat-media/${UUID.randomUUID()}").apply { mkdirs() }
        return File(directory, ChatPayload.safeName(selection.name)).also {
            try { it.writeBytes(bytes) } finally { bytes.fill(0) }
        }
    }
    fun exportImage(source: android.graphics.Bitmap, edits: PhotoEdits) = ChatPhotoEditor.export(context, source, edits)
    fun hasPreview(path: String): Boolean = runCatching {
        val file = File(path)
        file.isFile && file.canonicalPath.startsWith(File(context.cacheDir, "chat-media").canonicalPath + File.separator)
    }.getOrDefault(false)
    fun removePreview(path: String) {
        val file = File(path)
        if (file.canonicalPath.startsWith(File(context.cacheDir, "chat-media").canonicalPath + File.separator)) file.delete()
    }
    fun upload(selection: MediaSelection, client: ChatTransport, progress: (Int) -> Unit): ChatAttachment {
        val bytes = context.contentResolver.openInputStream(Uri.parse(selection.uri))?.use { limitedRead(it, ChatFileCrypto.MAX_BYTES) }
            ?: throw ChatException(UiText(R.string.chat_file_unavailable))
        val encrypted = try { ChatFileCrypto.encrypt(bytes) } finally { bytes.fill(0) }
        val slot = client.requestUpload(encrypted.bytes.size.toLong())
        client.validateMediaUrl(slot.putUrl); client.validateMediaUrl(slot.getUrl)
        val connection = open(slot.putUrl)
        try {
            connection.requestMethod = "PUT"; connection.doOutput = true
            connection.setFixedLengthStreamingMode(encrypted.bytes.size)
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            slot.headers.forEach { (name, value) ->
                require(name.lowercase() in setOf("authorization", "cookie", "expires") && !value.contains('\n') && !value.contains('\r'))
                connection.setRequestProperty(name, value)
            }
            connection.outputStream.use { stream ->
                var written = 0
                while (written < encrypted.bytes.size) {
                    val count = minOf(64 * 1024, encrypted.bytes.size - written)
                    stream.write(encrypted.bytes, written, count); written += count
                    progress((written * 100L / encrypted.bytes.size).toInt())
                }
            }
            if (connection.responseCode !in 200..299) throw ChatException(UiText(R.string.chat_upload_failed))
        } finally { connection.disconnect() }
        return ChatAttachment(slot.getUrl, encrypted.key, encrypted.iv, selection.name, selection.mime,
            (encrypted.bytes.size - 16).toLong(), encrypted.sha256)
    }
    fun download(file: ChatAttachment, client: ChatTransport, progress: (Int) -> Unit): File {
        client.validateMediaUrl(file.url)
        val connection = open(file.url)
        val bytes = try {
            if (connection.responseCode != 200) throw ChatException(UiText(R.string.chat_download_failed))
            require(connection.contentLengthLong <= ChatFileCrypto.MAX_BYTES + 16)
            connection.inputStream.use { limitedRead(it, file.size + 16) { count -> progress((count * 100 / (file.size + 16)).toInt()) } }
        } finally { connection.disconnect() }
        val plain = try { ChatFileCrypto.decrypt(bytes, file) }
            catch (_: Exception) { throw ChatException(UiText(R.string.chat_file_integrity_error)) }
        val directory = File(context.cacheDir, "chat-media/${UUID.randomUUID()}").apply { mkdirs() }
        return File(directory, ChatPayload.safeName(file.name)).also { target ->
            try { target.writeBytes(plain) } finally { plain.fill(0) }
        }
    }
    private fun open(value: String): HttpsURLConnection {
        val original = URL(value)
        require(original.protocol == "https" && original.userInfo == null && original.ref == null)
        val local = BuildConfig.DEBUG && pin.isNotEmpty() && original.host == "localhost" && original.port == 5443
        val url = if (local) URL("https", "10.0.2.2", original.port, original.file) else original
        return (url.openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = false; useCaches = false
            if (local) {
                // The emulator gateway changes routing, not the upload service's virtual host.
                setRequestProperty("Host", "localhost:5443")
                val trust = object : X509TrustManager {
                    override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException()
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                        val leaf = chain.firstOrNull() ?: throw CertificateException()
                        leaf.checkValidity()
                        if (ChatFileCrypto.hash(leaf.encoded) != pin.lowercase()) throw CertificateException("Local certificate changed")
                    }
                }
                sslSocketFactory = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }.socketFactory
                hostnameVerifier = javax.net.ssl.HostnameVerifier { host, session ->
                    host == "10.0.2.2" && runCatching { ChatFileCrypto.hash(session.peerCertificates.first().encoded) == pin.lowercase() }.getOrDefault(false)
                }
            }
        }
    }
    fun clearPreviews() { File(context.cacheDir, "chat-media").deleteRecursively() }
    companion object {
        fun limitedRead(stream: InputStream, max: Long, progress: (Long) -> Unit = {}): ByteArray {
            require(max in 1..ChatFileCrypto.MAX_BYTES + 16)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size().toLong() + count > max) throw ChatException(UiText(R.string.chat_file_too_large))
                output.write(buffer, 0, count); progress(output.size().toLong())
            }
            return output.toByteArray()
        }
    }
}

data class MediaSelection(val uri: String, val name: String, val mime: String, val size: Long)
