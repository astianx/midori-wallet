package org.astianx.midori.one.chat

import java.net.URI
import org.json.JSONObject

/** This envelope is inside OMEMO. Upload URLs, filenames and keys never become stanza bodies. */
internal object ChatPayload {
    private const val PREFIX = "midori:content:1\n"
    fun encode(message: ChatMessage): String {
        if (message.attachment == null && message.sharedContact == null && message.location == null) return message.body
        return PREFIX + JSONObject().put("text", message.body).apply {
            message.attachment?.let { put("file", attachmentJson(it)) }
            message.sharedContact?.let { put("contact", it) }
            message.location?.let { put("geo", it) }
        }
    }
    fun decode(message: ChatMessage): ChatMessage {
        if (!message.body.startsWith(PREFIX)) return message
        return try {
            val json = JSONObject(message.body.removePrefix(PREFIX))
            val contact = json.optString("contact").takeIf { it.isNotBlank() }?.let(::chatAddress)
            val geo = json.optString("geo").takeIf { it.isNotBlank() }?.also(::validateLocation)
            message.copy(body = json.optString("text").take(8_000),
                attachment = json.optJSONObject("file")?.let(::readAttachment), sharedContact = contact, location = geo)
        } catch (_: Exception) { message.copy(body = "", protection = MessageProtection.UNREADABLE) }
    }
    fun attachmentJson(file: ChatAttachment) = JSONObject().put("url", file.url).put("key", file.key).put("iv", file.iv)
        .put("name", file.name).put("mime", file.mime).put("size", file.size).put("sha256", file.sha256)
    fun readAttachment(json: JSONObject): ChatAttachment {
        val file = ChatAttachment(json.getString("url"), json.getString("key"), json.getString("iv"),
            safeName(json.getString("name")), json.getString("mime"), json.getLong("size"), json.getString("sha256"))
        require(file.key.matches(Regex("[0-9a-f]{64}")) && file.iv.matches(Regex("[0-9a-f]{24}")))
        require(file.sha256.matches(Regex("[0-9a-f]{64}")) && file.size in 1..ChatFileCrypto.MAX_BYTES)
        require(file.mime.matches(Regex("[a-zA-Z0-9.+_-]+/[a-zA-Z0-9.+_-]+")))
        val uri = URI(file.url)
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.fragment == null)
        return file
    }
    fun safeName(name: String): String = name.substringAfterLast('/').substringAfterLast('\\')
        .filter { !it.isISOControl() && it !in ":*?\"<>|" }.take(120).ifBlank { "attachment" }
    fun validateLocation(value: String) {
        require(value.startsWith("geo:"))
        val coordinates = value.removePrefix("geo:").split(',')
        require(coordinates.size == 2)
        val lat = coordinates[0].toDouble(); val lon = coordinates[1].toDouble()
        require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0)
    }
}
