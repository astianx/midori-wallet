/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import android.graphics.Bitmap
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
            compose.onNodeWithText(second.substringBefore('@')).performClick()
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
            compose.onNodeWithText(config.getString("reply")).assertIsDisplayed()
            capture("conversation")
            compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
            capture("chats")

            // These are the user's normal screens and genuine wallet state. No wallet
            // action (create/import/delete/sign/submit), clipboard or recovery word access.
            compose.onNode(hasText(context.getString(R.string.wallet)) and isSelectable()).performClick()
            compose.waitForIdle()
            capture("wallet")
            compose.onNode(hasText(context.getString(R.string.more)) and isSelectable()).performClick()
            compose.waitForIdle()
            capture("settings")
            compose.onNode(hasText(context.getString(R.string.chats)) and isSelectable()).performClick()
            compose.onNodeWithText(second.substringBefore('@')).performClick()
            compose.onNodeWithText(config.getString("reply")).assertIsDisplayed()

            val directory = File(context.cacheDir, "live-ui").apply { mkdirs() }
            publish(reportFile, JSONObject()
                .put("status", "body_passed").put("realActivity", MainActivity::class.java.name)
                .put("server", "localhost:5222 via STARTTLS with inspected pin")
                .put("account", first).put("peer", second).put("outgoingId", outgoing.id)
                .put("encryptedReply", true).put("deliveryReceipt", true)
                .put("wallet", "normal UI read only; no credentials or financial actions accessed by test")
                .put("accountRetained", true).toString(2))
        } finally {
            // Credentials and messages remain in the app's encrypted chat vault. The runner
            // removes only its plaintext provisioning file and reopens the normal activity.
            scenario.close()
        }
        // A completed round trip is not a completed test if real activity destruction fails.
        publish(reportFile, JSONObject(reportFile.readText()).put("status", "passed")
            .put("activityDestroyed", true).toString(2))
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = File(context.cacheDir, "live-ui").apply { check(mkdirs() || isDirectory) }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
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

    private fun await(label: String, ready: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 60_000
        while (!ready() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        assertTrue("Timed out waiting for $label", ready())
    }
}
