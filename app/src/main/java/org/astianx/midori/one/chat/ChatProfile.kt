package org.astianx.midori.one.chat

import org.json.JSONObject

/** Editable local profile. Only an explicit publish action makes a copy visible on the server. */
data class ChatProfile(val name: String = "", val bio: String = "", val photo: String? = null) {
    fun validate() {
        require(name.length <= 60 && bio.length <= 160 && name.none(Char::isISOControl) && bio.none(Char::isISOControl))
        require(photo == null || photo.matches(Regex("[a-f0-9-]{36}\\.jpg")))
    }
    fun toJson() = JSONObject().put("name", name).put("bio", bio).apply { photo?.let { put("photo", it) } }
    companion object {
        fun fromJson(value: JSONObject) = ChatProfile(value.optString("name"), value.optString("bio"),
            value.optString("photo").takeIf { it.isNotBlank() }).also { it.validate() }
    }
}

/** Bounded embedded avatar, cached only in the encrypted account history; never an external URL. */
data class PublishedChatProfile(val name: String = "", val bio: String = "", val photoBase64: String? = null,
    val photoMime: String? = null) {
    fun validate() {
        require(name.length <= 60 && bio.length <= 160 && name.none(Char::isISOControl) && bio.none(Char::isISOControl))
        require((photoBase64 == null) == (photoMime == null))
        photoBase64?.let {
            require(it.length <= MAX_AVATAR_ENCODED && photoMime in setOf("image/jpeg", "image/png"))
            val bytes = java.util.Base64.getDecoder().decode(it)
            require(bytes.isNotEmpty() && bytes.size <= MAX_AVATAR_BYTES)
            require(if (photoMime == "image/jpeg") bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
                else bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)))
        }
    }
    fun toJson() = JSONObject().put("name", name).put("bio", bio).apply {
        photoBase64?.let { put("photoBase64", it); put("photoMime", photoMime) }
    }
    override fun toString() = "PublishedChatProfile(name=$name, hasPhoto=${photoBase64 != null})"
    companion object {
        const val MAX_AVATAR_BYTES = 64 * 1024
        const val MAX_AVATAR_ENCODED = ((MAX_AVATAR_BYTES + 2) / 3) * 4
        fun fromJson(value: JSONObject) = PublishedChatProfile(value.optString("name"), value.optString("bio"),
            value.optString("photoBase64").takeIf(String::isNotBlank), value.optString("photoMime").takeIf(String::isNotBlank)).also { it.validate() }
    }
}

/** Keep recent avatars within a fixed budget: the encrypted vault is rewritten for message state. */
internal fun cacheContactProfile(current: Map<String, PublishedChatProfile>, address: String,
    profile: PublishedChatProfile): Map<String, PublishedChatProfile> {
    val recent = ((current - address) + (address to profile)).entries.toList().takeLast(200)
    var remaining = 512 * 1024 // Encoded bytes, under 384 KiB of original avatar data.
    return recent.asReversed().associate { (peer, value) ->
        val bytes = value.photoBase64?.length ?: 0
        peer to if (bytes <= remaining) { remaining -= bytes; value }
            else value.copy(photoBase64 = null, photoMime = null)
    }.entries.toList().asReversed().associate { it.toPair() }
}

/** A short handle resolves only in the currently selected account's domain. */
internal fun resolveChatHandle(input: String, account: String): String {
    val value = input.trim()
    if (!value.startsWith('@')) return chatAddress(value)
    val handle = value.drop(1)
    require(handle.isNotBlank() && !handle.contains('@') && !handle.contains('/') && handle.none(Char::isWhitespace))
    val domain = chatAddress(account).substringAfter('@')
    return chatAddress("$handle@$domain")
}
