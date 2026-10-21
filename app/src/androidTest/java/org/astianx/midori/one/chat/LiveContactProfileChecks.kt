/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.astianx.midori.one.R
import org.json.JSONObject
import org.junit.Assert.*

/** Uses the actual peer's server-published profile and the phone's existing account. No contact fixtures. */
internal fun verifyLiveContactUi(compose: ComposeTestRule, chat: ChatViewModel, peer: String,
    capture: (String) -> Unit): JSONObject {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val account = chat.state.value.account
    val localProfile = chat.state.value.profile
    val contactsBefore = chat.state.value.contacts.map { it.address }.toSet()
    val localDetails = chat.state.value.conversationOptions[peer]?.let { it.alias to it.phone }
    check(chat.state.value.selectedPeer == peer)

    // Opening the real header causes a fresh XEP-0054 request to the running ejabberd.
    compose.onNodeWithTag("chat-contact-profile").performClick()
    compose.waitUntil(60_000) {
        !chat.state.value.profileLoading && chat.state.value.contactProfiles[peer]?.name == "Interlocutor local de Midori" &&
            chat.state.value.contactProfiles[peer]?.bio == "Cuenta de pruebas creada en ejabberd"
    }
    compose.onNodeWithText("Cuenta de pruebas creada en ejabberd").assertIsDisplayed()
    capture("contact-profile-real")
    compose.onNodeWithText(context.getString(R.string.contacts_edit_local)).performScrollTo().performClick()
    compose.onNode(hasText(context.getString(R.string.contacts_local_name)) and hasSetTextAction()).performScrollTo().assertIsDisplayed()
    compose.onNode(hasText(context.getString(R.string.contacts_local_phone)) and hasSetTextAction()).performScrollTo().assertIsDisplayed()
    capture("contact-local-details")
    // Inspect existing fields without typing or saving a fabricated name/phone number.
    compose.onNodeWithTag("chat-contact-profile-close").performScrollTo().performClick()
    compose.waitUntil(10_000) {
        runCatching { compose.onNodeWithContentDescription(context.getString(R.string.chat_options))
            .assertIsDisplayed() }.isSuccess
    }

    compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
    compose.onNodeWithContentDescription(context.getString(R.string.profile)).performClick()
    compose.onNodeWithText(context.getString(R.string.profile_show_qr)).performScrollTo().performClick()
    compose.onNodeWithContentDescription(context.getString(R.string.profile_contact_qr)).assertIsDisplayed()
    capture("own-contact-qr")
    val bitmap = compose.onNodeWithContentDescription(context.getString(R.string.profile_contact_qr)).captureToImage().asAndroidBitmap()
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))),
        mapOf(DecodeHintType.CHARACTER_SET to "UTF-8")).text
    assertEquals(account, parseChatContactLink(decoded))
    assertEquals(chatContactLink(account), decoded)
    compose.onNodeWithText(context.getString(R.string.close)).performClick()
    // The consent control is visible, but the test never publishes or edits the phone profile.
    compose.onNodeWithText(context.getString(R.string.profile_publish)).performScrollTo().assertIsDisplayed()
    capture("own-profile-publication-control")
    compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
    compose.onNode(hasText(context.getString(R.string.chats)) and isSelectable()).performClick()

    // Exercise the same validated proposal used by QR/deep links. Receiving it must not add or open anything.
    chat.prepareContact(chatContactLink(peer))
    compose.waitUntil(10_000) { chat.state.value.pendingContactAddress == peer }
    compose.onNode(hasText(context.getString(R.string.contacts_address_link)) and hasSetTextAction()).assertExists()
    compose.onNodeWithText(context.getString(R.string.contacts_save_chat)).assertIsDisplayed()
    capture("contact-link-review")
    assertEquals(contactsBefore, chat.state.value.contacts.map { it.address }.toSet())
    compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
    compose.waitUntil(10_000) { chat.state.value.pendingContactAddress == null }
    assertEquals(localProfile, chat.state.value.profile)
    assertEquals(localDetails, chat.state.value.conversationOptions[peer]?.let { it.alias to it.phone })
    assertEquals(contactsBefore, chat.state.value.contacts.map { it.address }.toSet())
    compose.onNodeWithTag("chat-row-$peer").performClick()
    // Opening a real conversation persists its read state before navigating.
    // Compose idleness alone cannot wait for that serialized IO coroutine.
    compose.waitUntil(10_000) {
        runCatching { compose.onNodeWithContentDescription(context.getString(R.string.chat_options))
            .assertIsDisplayed() }.isSuccess
    }
    assertEquals(peer, chat.state.value.selectedPeer)
    println("LIVE_UI: server profile, own QR and confirmed contact proposal verified")

    return JSONObject().put("profileReadFromEjabberd", true).put("remoteProfileDisplayed", true)
        .put("localContactFieldsInspected", true).put("contactDetailsChanged", false)
        .put("ownQrDecoded", true).put("ownQrMatchesCurrentAccount", true)
        .put("publicationControlDisplayed", true).put("phoneProfilePublishedByTest", false)
        .put("linkRequiresConfirmation", true).put("linkDismissedWithoutAddingContact", true)
        .put("phoneContactsRead", false)
}
