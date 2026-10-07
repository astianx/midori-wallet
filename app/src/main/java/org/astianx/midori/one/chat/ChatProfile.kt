package org.astianx.midori.one.chat

import org.json.JSONObject

/** Local profile is account-scoped and encrypted with chat history. It is never a global identity claim. */
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

/** A short handle resolves only in the currently selected account's domain. */
internal fun resolveChatHandle(input: String, account: String): String {
    val value = input.trim()
    if (!value.startsWith('@')) return chatAddress(value)
    val handle = value.drop(1)
    require(handle.isNotBlank() && !handle.contains('@') && !handle.contains('/') && handle.none(Char::isWhitespace))
    val domain = chatAddress(account).substringAfter('@')
    return chatAddress("$handle@$domain")
}
