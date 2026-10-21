/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.astianx.midori.one.BuildConfig
import org.astianx.midori.one.MainActivity
import org.astianx.midori.one.R
import org.astianx.midori.one.wallet.WalletViewModel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit opt-in REAL UI/server acceptance. Runs MainActivity and its normal chat vault.
 * A separate JVM peer talks to ejabberd; no invented contacts, messages or balances.
 * Never creates/imports/deletes a wallet, reads its recovery words or sends funds.
 * Refuses to replace an existing chat account. The new lab account remains signed in.
 */
@RunWith(AndroidJUnit4::class)
class LiveChatUiDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun realConversationAndWalletNavigation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val configuration = File(context.filesDir, "chat-live-ui.json")
        assumeTrue("Explicit live UI laboratory configuration is required", configuration.exists())
        val config = JSONObject(configuration.readText())
        val first = config.getString("first")
        val second = config.getString("second")
        require(first.matches(Regex("midori-ui-a-[a-z0-9]+@localhost")))
        require(second.matches(Regex("midori-ui-b-[a-z0-9]+@localhost")))
        require(config.getString("pin").equals(BuildConfig.CHAT_DEV_CERT_SHA256, ignoreCase = true)) {
            "The APK must contain the explicitly inspected laboratory certificate pin"
        }
        lateinit var chat: ChatViewModel
        instrumentation.runOnMainSync { chat = ChatSession.get(context) }
        await("chat restoration") { !chat.state.value.restoringSession }
        check(chat.state.value.account.isNotBlank() || chat.state.value.error == null) {
            "Chat restoration failed; an unreadable saved session must not be replaced"
        }
        check(chat.state.value.account.isBlank() || chat.state.value.account == first) {
            "An unrelated chat account already exists; refusing to replace it"
        }
        if (config.optBoolean("requireExistingPhoneAccount", false)) {
            check(chat.state.value.account == first) {
                "The preserved phone account must already exist; refusing to provision or replace it"
            }
        }
        val reportFile = File(context.cacheDir, "live-ui/device-report.json")
        reportFile.delete() // Only this opt-in test's previous, non-secret report.

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.waitForIdle()
            lateinit var wallet: WalletViewModel
            scenario.onActivity { wallet = ViewModelProvider(it)[WalletViewModel::class.java] }
            // Restoration may complete by navigating back to Chats. Wait for that normal
            // startup flow before the test starts interacting with account/conversation UI.
            await("normal wallet startup, without modifying it") { !wallet.state.value.isLoading }
            check(wallet.state.value.restoreError == null) { "Existing wallet recovery failed; preserve it and stop UI acceptance" }
            val started = compose.onAllNodesWithText(context.getString(R.string.get_started))
            if (started.fetchSemanticsNodes().isNotEmpty()) started.onFirst().performClick()
            val goChats = compose.onAllNodes(hasText(context.getString(R.string.chats)) and isSelectable())
            if (goChats.fetchSemanticsNodes().isNotEmpty()) goChats.onFirst().performClick()

            if (chat.state.value.account.isBlank()) {
                compose.onNodeWithText(context.getString(R.string.chat_connect)).performClick()
                compose.onNode(hasText(context.getString(R.string.chat_account_address)) and hasSetTextAction()).assertExists()
                // Keep the password out of text selectors and assertion-failure UI dumps. This
                // invokes the same real authentication/persistence path as the form's action.
                runBlocking { chat.connect(first, config.getString("password"), "10.0.2.2", "5222").join() }
            } else if (chat.state.value.connection != ChatConnection.ONLINE) {
                chat.retry()
            }
            await("real ejabberd login") { chat.state.value.connection == ChatConnection.ONLINE }
            println("LIVE_UI: existing laboratory account connected")
            assertEquals(first, chat.state.value.account)
            assertTrue(chat.state.value.ownFingerprint.isNotBlank())
            publish(File(context.filesDir, "chat-live-ui-phone.json"), JSONObject()
                .put("account", first).put("fingerprint", chat.state.value.ownFingerprint).toString())

            runBlocking { chat.addContact(second).join() }
            await("companion contact request") { second in chat.state.value.requests ||
                chat.state.value.contacts.any { it.address == second && it.online } }
            if (second in chat.state.value.requests) runBlocking { chat.answerSubscription(second, true).join() }
            await("companion presence after reciprocal subscription") {
                chat.state.value.contacts.any { it.address == second && it.online }
            }
            runBlocking { chat.open(second).join(); chat.loadDevices().join() }
            val devices = chat.state.value.devices.filter { it.address == second }
            assertEquals("Exactly the independently inspected companion device must exist", 1, devices.size)
            assertEquals(config.getString("peerFingerprint"), devices.single().fingerprint)
            runBlocking { chat.trust(devices.single(), true).join() }
            await("companion verified the phone fingerprint") {
                File(context.filesDir, "chat-live-ui-peer-trusted").exists()
            }

            // Open the actual conversation row. Avoid launching a second activity while
            // ActivityScenario owns lifecycle acceptance for the original MainActivity.
            val accountToChats = compose.onAllNodesWithText(context.getString(R.string.go_to_chats))
            if (accountToChats.fetchSemanticsNodes().isNotEmpty()) {
                accountToChats.onFirst().performScrollTo().performClick()
            } else {
                compose.onNode(hasText(context.getString(R.string.chats)) and isSelectable()).performClick()
            }
            compose.onNodeWithTag("chat-row-$second").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription(context.getString(R.string.chat_options))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val draft = chat.state.value.drafts[second].orEmpty()
            check(draft.isEmpty() || draft == config.optString("previousMessage") || draft == config.getString("message")) {
                "An unknown draft exists in this conversation; preserve it and stop acceptance"
            }
            if (draft.isNotEmpty()) compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(hasSetTextAction()).performTextInput(config.getString("message"))
            compose.onNodeWithContentDescription(context.getString(R.string.send)).performClick()
            await("peer decrypted and replied through ejabberd") {
                chat.state.value.messages.any { !it.outgoing && it.peer == second &&
                    it.body == config.getString("reply") && it.protection == MessageProtection.ENCRYPTED }
            }
            val outgoing = chat.state.value.messages.last { it.outgoing && it.peer == second &&
                it.body == config.getString("message") }
            await("real delivery or read receipt") {
                chat.state.value.messages.any { it.id == outgoing.id &&
                    it.delivery in setOf(MessageDelivery.DELIVERED, MessageDelivery.READ) }
            }
            assertTrue(chat.state.value.drafts[second].isNullOrEmpty())
            // Transport state precedes composition/layout and the normal incoming-message scroll animation.
            compose.waitUntil(15_000) {
                runCatching { compose.onNodeWithText(config.getString("reply")).assertIsDisplayed() }.isSuccess
            }
            compose.onNodeWithText(config.getString("reply")).assertIsDisplayed()
            println("LIVE_UI: encrypted text and receipt verified")
            capture("conversation")
            val richChat = if (config.optBoolean("richChat", false)) exerciseVoiceAndEmoji(chat, config, second) else null
            compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
            val chatList = verifyConversationRow(second)
            capture("chats")
            if (richChat != null) {
                compose.onNodeWithContentDescription(context.getString(R.string.new_chat)).performClick()
                compose.onNodeWithTag("chat-contact-list").performScrollToNode(hasText(second))
                compose.onNode(hasText(second) and hasAnyAncestor(hasTestTag("chat-contact-list"))).assertIsDisplayed()
                capture("new-chat-contacts")
                assertNull("Returning to the list must clear the previous conversation", chat.state.value.selectedPeer)
                // This row belongs to a separate bottom-sheet window. Use its real
                // accessible action; injected root coordinates can dismiss the sheet
                // instead of hitting the contact after scrolling/layout changes.
                compose.onNode(hasText(second) and hasAnyAncestor(hasTestTag("chat-contact-list")))
                    .assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick) { action -> assertTrue(action()) }
                await("select an existing contact without typing its address") { chat.state.value.selectedPeer == second }
                // Selection precedes the contact sheet's exit animation and conversation layout.
                compose.waitUntil(10_000) {
                    runCatching { compose.onNodeWithContentDescription(context.getString(R.string.chat_options))
                        .assertIsDisplayed() }.isSuccess
                }
                richChat.put("contactChosenFromList", true)
                compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
            }

            // These are the user's normal screens and genuine wallet state. No wallet
            // action (create/import/delete/sign/submit), clipboard or recovery word access.
            compose.onNode(hasText(context.getString(R.string.wallet)) and isSelectable()).performClick()
            compose.waitForIdle()
            capture("wallet")
            compose.onNode(hasText(context.getString(R.string.more)) and isSelectable()).performClick()
            compose.waitForIdle()
            capture("settings")
            compose.onNode(hasText(context.getString(R.string.chats)) and isSelectable()).performClick()
            compose.onNodeWithTag("chat-row-$second").performClick()
            compose.waitUntil(10_000) {
                runCatching { compose.onNodeWithContentDescription(context.getString(R.string.chat_options))
                    .assertIsDisplayed() }.isSuccess
            }
            assertEquals(second, chat.state.value.selectedPeer)

            publish(reportFile, JSONObject()
                .put("status", "body_passed").put("realActivity", MainActivity::class.java.name)
                .put("server", "localhost:5222 via STARTTLS with inspected pin")
                .put("account", first).put("peer", second).put("outgoingId", outgoing.id)
                .put("encryptedReply", true).put("deliveryReceipt", true)
                .put("chatList", chatList)
                .put("richChat", richChat ?: JSONObject.NULL)
                .put("wallet", "normal UI read only; no credentials or financial actions accessed by test")
                .put("accountRetained", true).toString(2))
        } catch (failure: Throwable) {
            // Preserve the real failing screen before ActivityScenario/runner cleanup.
            // Screenshot failures must never replace the original assertion or error.
            runCatching { writeScreenshot("failure") }
            throw failure
        } finally {
            // Credentials and messages remain in the app's encrypted chat vault. The runner
            // removes only its plaintext provisioning file and reopens the normal activity.
            scenario.close()
        }
        // A completed round trip is not a completed test if real activity destruction fails.
        publish(reportFile, JSONObject(reportFile.readText()).put("status", "passed")
            .put("activityDestroyed", true).toString(2))
    }

    private fun exerciseVoiceAndEmoji(chat: ChatViewModel, config: JSONObject, peer: String): JSONObject {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(chat.state.value.drafts[peer].isNullOrEmpty()) { "Preserve an existing text draft" }
        compose.onNodeWithContentDescription(context.getString(R.string.chat_emoji)).performClick()
        compose.waitForIdle()
        capture("emoji-picker")
        compose.onNodeWithContentDescription(context.getString(R.string.chat_emoji)).performClick()
        assertTrue(chat.state.value.drafts[peer].isNullOrEmpty())
        println("LIVE_UI: emoji picker opened and closed")

        // The normal recorder captures the emulator's microphone. There is no generated
        // recording, file fixture, substituted MediaRecorder or claimed human speech.
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.RECORD_AUDIO)
        val before = chat.state.value.messages.map { it.id }.toSet()
        compose.onNodeWithContentDescription(context.getString(R.string.chat_record)).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("chat-voice-recorder").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { recordedTime() != "0:00" }
        compose.onNodeWithText(config.getString("reply")).assertIsDisplayed()
        capture("voice-recording")
        compose.onNodeWithTag("chat-voice-pause-resume").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(context.getString(R.string.chat_voice_paused)).fetchSemanticsNodes().isNotEmpty()
        }
        val paused = recordedTime()
        Thread.sleep(1_200)
        compose.onNodeWithTag("chat-voice-duration").assertTextEquals(paused)
        // Reading the actual history remains possible while the recorder occupies only the footer.
        compose.onNode(hasScrollAction()).performTouchInput { swipeDown() }
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.onNodeWithTag("chat-voice-recorder").assertIsDisplayed()
        capture("voice-paused")
        compose.onNodeWithTag("chat-voice-pause-resume").performClick()
        compose.waitUntil(10_000) { recordedTime() != paused }
        compose.onNodeWithTag("chat-voice-cancel").performClick()
        compose.onNodeWithTag("chat-voice-recorder").assertDoesNotExist()
        assertEquals(before, chat.state.value.messages.map { it.id }.toSet())
        println("LIVE_UI: inline voice pause, resume and discard verified")

        compose.onNodeWithContentDescription(context.getString(R.string.chat_record)).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("chat-voice-recorder").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { recordedTime() !in setOf("0:00", "0:01") }
        compose.onNodeWithTag("chat-voice-send").performClick()
        await("a new real voice message from the microphone") {
            chat.state.value.messages.any { it.id !in before && it.outgoing && it.peer == peer && it.attachment?.mime == "audio/mp4" }
        }
        val voice = chat.state.value.messages.last { it.id !in before && it.outgoing && it.peer == peer && it.attachment?.mime == "audio/mp4" }
        await("companion downloaded and authenticated the encrypted voice attachment", timeoutMillis = 240_000) {
            chat.state.value.messages.any { !it.outgoing && it.peer == peer && it.protection == MessageProtection.ENCRYPTED &&
                it.body == "Audio recibido y descifrado · ${voice.id}" }
        }
        await("voice delivery receipt") {
            chat.state.value.messages.any { it.id == voice.id && it.delivery in setOf(MessageDelivery.DELIVERED, MessageDelivery.READ) }
        }
        compose.onNodeWithTag("chat-voice-recorder").assertDoesNotExist()
        assertTrue(chat.state.value.drafts[peer].isNullOrEmpty())
        capture("voice-sent")
        println("LIVE_UI: actual voice attachment decrypted by companion")
        return JSONObject().put("voiceMessageId", voice.id).put("voicePauseResume", true)
            .put("voiceCancelled", true).put("historyReadableDuringRecording", true)
            .put("pausedTimeExcluded", true).put("voiceEncryptedRoundTrip", true)
            .put("emojiPickerOpened", true).put("microphone", "real emulator capture; speech quality not asserted")
            .put("media", verifyLiveMediaUi(compose, chat, peer, ::capture))
            .put("contactsProfile", verifyLiveContactUi(compose, chat, peer, ::capture))
    }

    private fun recordedTime(): String = compose.onNodeWithTag("chat-voice-duration").fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Compose can be idle before SurfaceFlinger has presented its newest frame.
        // Capture the visible emulator after rendering, rather than the previous panel.
        Thread.sleep(250)
        compose.waitForIdle()
        writeScreenshot(name)
    }

    private fun writeScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = File(context.cacheDir, "live-ui").apply { check(mkdirs() || isDirectory) }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        println("LIVE_UI: screenshot $name")
    }

    private fun verifyConversationRow(peer: String): JSONObject {
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val row = bounds("chat-row-$peer")
        val avatar = bounds("chat-row-avatar-$peer")
        val name = bounds("chat-row-name-$peer")
        val preview = bounds("chat-row-preview-$peer")
        val time = bounds("chat-row-time-$peer")
        assertTrue("The actual conversation must remain a compact row", row.height / density <= 104f)
        assertEquals("The avatar must remain square", avatar.width, avatar.height, 1f)
        assertTrue("The avatar must not expand over the conversation text", avatar.width <= row.width * 0.25f)
        assertTrue("The actual name needs usable horizontal space", name.width >= row.width * 0.35f)
        assertTrue("The actual latest-message preview needs usable horizontal space", preview.width >= row.width * 0.35f)
        assertTrue("The avatar must precede the name", avatar.right <= name.left)
        assertTrue("The preview must sit below the name", preview.top >= name.bottom)
        assertTrue("The timestamp must stay at the row end", time.left >= name.right - 1f)
        assertTrue("The row must contain its timestamp", time.right <= row.right)
        println("LIVE_UI: real chat row geometry verified")
        return JSONObject().put("realContact", true).put("namePreviewTimeVisible", true)
            .put("rowHeightDp", row.height / density).put("avatarWidthDp", avatar.width / density)
            .put("nameWidthDp", name.width / density).put("previewWidthDp", preview.width / density)
    }

    private fun publish(file: File, text: String) {
        val atomic = android.util.AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }

    private fun await(label: String, timeoutMillis: Long = 60_000, ready: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMillis
        while (!ready() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        assertTrue("Timed out waiting for $label", ready())
    }
}
