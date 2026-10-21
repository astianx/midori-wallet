/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import java.net.URI
import java.net.URLDecoder

/** A link is an address proposal only. Callers must ask before adding or starting a conversation. */
internal fun parseChatContactLink(input: String): String {
    val value = input.trim()
    require(value.length in 3..2048 && value.none(Char::isISOControl))
    if (!value.contains(':')) return chatAddress(value)
    val uri = URI(value)
    require(uri.rawFragment == null)
    val address = when (uri.scheme?.lowercase()) {
        "xmpp" -> {
            require(!uri.rawSchemeSpecificPart.startsWith("//"))
            val parts = uri.rawSchemeSpecificPart.split('?', limit = 2)
            require(parts.size == 1 || parts[1] == "message")
            decodeLinkPart(parts[0])
        }
        "midori" -> {
            require(uri.host == "contact" && uri.rawUserInfo == null && uri.port == -1 && uri.rawPath.orEmpty().isEmpty())
            val query = requireNotNull(uri.rawQuery)
            require(query.startsWith("address=") && !query.contains('&'))
            decodeLinkPart(query.removePrefix("address="))
        }
        else -> throw IllegalArgumentException("Unsupported contact link")
    }
    require(address.none(Char::isISOControl) && !address.contains('?') && !address.contains('#'))
    return chatAddress(address)
}

private fun decodeLinkPart(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
internal fun chatContactLink(address: String): String = "xmpp:" + URI(null, null, chatAddress(address), null).rawPath
internal fun validatedContactDetails(name: String, phone: String): Pair<String, String> {
    require(name.length <= 60 && name.none(Char::isISOControl))
    require(phone.length <= 40 && phone.all { it.isDigit() || it in "+ ()-." })
    return name.trim() to phone.trim()
}

internal data class DeviceChatContact(val address: String, val name: String)

/** Reads only explicit IM identities after permission. No phone/email guessing and no network upload. */
@Suppress("DEPRECATION")
internal fun readDeviceChatContacts(context: Context): List<DeviceChatContact> {
    check(context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    val im = ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE
    val rows = mutableListOf<DeviceChatContact>()
    context.contentResolver.query(ContactsContract.Data.CONTENT_URI,
        arrayOf(ContactsContract.Data.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Im.DATA,
            ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL),
        "${ContactsContract.Data.MIMETYPE} = ?", arrayOf(im), null)?.use { cursor ->
        while (cursor.moveToNext()) {
            val protocol = cursor.getInt(2)
            val custom = cursor.getString(3).orEmpty().lowercase()
            if (protocol != ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER &&
                !(protocol == ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM && custom in setOf("xmpp", "jabber", "midori", "midori wallet"))) continue
            val address = runCatching { parseChatContactLink(cursor.getString(1).orEmpty()) }.getOrNull() ?: continue
            val name = cursor.getString(0).orEmpty().filterNot(Char::isISOControl).take(60)
            rows += DeviceChatContact(address, name.ifBlank { address.substringBefore('@') })
        }
    }
    return rows.distinctBy { it.address }.sortedBy { it.name.lowercase() }
}
