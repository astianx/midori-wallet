/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smackx.vcardtemp.VCardManager
import org.jivesoftware.smackx.vcardtemp.packet.VCard
import org.jxmpp.jid.impl.JidCreate

/** XEP-0054. No URL fetch, phone discovery, automatic publication, or encryption claim. */
internal object SmackProfiles {
    fun load(connection: XMPPConnection, address: String): PublishedChatProfile = try {
        decode(VCardManager.getInstanceFor(connection).loadVCard(JidCreate.entityBareFrom(address)))
    } catch (e: org.jivesoftware.smack.XMPPException.XMPPErrorException) {
        if (e.stanzaError.condition == org.jivesoftware.smack.packet.StanzaError.Condition.item_not_found) PublishedChatProfile() else throw e
    }

    fun publish(connection: XMPPConnection, value: PublishedChatProfile) {
        value.validate()
        val manager = VCardManager.getInstanceFor(connection)
        // Preserve fields authored by another client. Failed reads must not erase those fields.
        val card = try { manager.loadVCard() }
        catch (e: org.jivesoftware.smack.XMPPException.XMPPErrorException) {
            if (e.stanzaError.condition == org.jivesoftware.smack.packet.StanzaError.Condition.item_not_found) VCard() else throw e
        }
        encode(value, card)
        manager.saveVCard(card)
    }

    internal fun encode(value: PublishedChatProfile, card: VCard = VCard()): VCard {
        value.validate()
        card.setField("FN", value.name)
        card.nickName = value.name
        card.setField("DESC", value.bio)
        if (value.photoBase64 == null) card.removeAvatar() else card.setAvatar(value.photoBase64, value.photoMime)
        return card
    }

    internal fun decode(card: VCard): PublishedChatProfile {
        // Bound before Smack decodes BINVAL. EXTVAL/URL images are never dereferenced.
        require(card.toXML().length <= 150_000)
        val name = (card.getField("FN")?.takeIf(String::isNotBlank) ?: card.nickName.orEmpty())
            .filterNot(Char::isISOControl).take(60)
        val bio = card.getField("DESC").orEmpty().filterNot(Char::isISOControl).take(160)
        val avatar = runCatching {
            val bytes = card.avatar ?: return@runCatching null
            require(bytes.size <= PublishedChatProfile.MAX_AVATAR_BYTES)
            PublishedChatProfile(name, bio, java.util.Base64.getEncoder().encodeToString(bytes), card.avatarMimeType).also { it.validate() }
        }.getOrNull()
        return avatar ?: PublishedChatProfile(name, bio)
    }
}
