/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import android.Manifest
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.astianx.midori.one.R
import org.json.JSONObject
import org.junit.Assert.*

/** Opt-in helper for the real MainActivity/ejabberd test, never a fabricated conversation. */
internal fun verifyLiveMediaUi(compose: ComposeTestRule, chat: ChatViewModel, peer: String,
    capture: (String) -> Unit): JSONObject {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
    instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
    check(chat.state.value.selectedPeer == peer)
    check(chat.state.value.pendingMedia == null)
    val before = chat.state.value.messages.map { it.id }.toSet()
    val draftBefore = chat.state.value.drafts[peer]
    fun waitFor(label: String, seconds: Int = 30, predicate: () -> Boolean) {
        val end = System.nanoTime() + seconds * 1_000_000_000L
        while (!predicate() && System.nanoTime() < end) Thread.sleep(100)
        assertTrue("Timed out waiting for $label", predicate())
    }
    fun hasTag(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    fun duration() = compose.onNodeWithTag("chat-video-duration").fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString("") { it.text }
    fun openRecorder() {
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithContentDescription(context.getString(R.string.chat_attach))
                .assertIsDisplayed().assertIsEnabled() }.isSuccess
        }
        compose.onNodeWithContentDescription(context.getString(R.string.chat_attach)).performClick()
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithText(context.getString(R.string.chat_video_note))
                .assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText(context.getString(R.string.chat_video_note)).performClick()
        compose.waitUntil(20_000) {
            runCatching { compose.onNodeWithTag("chat-video-record")
                .assertIsDisplayed().assertIsEnabled() }.isSuccess
        }
    }

    // Capture the emulator camera through the production Camera2/MediaRecorder stack.
    // An emulator camera may show its configured virtual scene; no fixture replaces it.
    openRecorder()
    capture("video-note-camera")
    compose.onNodeWithTag("chat-video-record").performClick()
    compose.waitUntil(15_000) { hasTag("chat-video-duration") && !duration().startsWith("0:00") }
    compose.onNodeWithTag("chat-video-pause-resume").performClick()
    compose.waitUntil(5_000) {
        compose.onAllNodesWithContentDescription(context.getString(R.string.chat_record_resume)).fetchSemanticsNodes().isNotEmpty()
    }
    Thread.sleep(250)
    val pausedAt = duration()
    Thread.sleep(1_200)
    assertEquals("A paused recording must not count elapsed wall time", pausedAt, duration())
    capture("video-note-paused")
    compose.onNodeWithTag("chat-video-pause-resume").performClick()
    compose.waitUntil(10_000) { duration() != pausedAt }
    compose.onNodeWithTag("chat-video-discard").performClick()
    compose.onNodeWithTag("chat-video-record").assertDoesNotExist()
    assertEquals("Discard must not send a message", before, chat.state.value.messages.map { it.id }.toSet())
    assertEquals(draftBefore, chat.state.value.drafts[peer])
    println("LIVE_UI: camera recording pause, resume and discard verified")

    openRecorder()
    compose.onNodeWithTag("chat-video-record").performClick()
    compose.waitUntil(15_000) {
        hasTag("chat-video-duration") && duration().substringBefore(" / ").split(':').let { clock ->
            clock.size == 2 && clock[0].toInt() * 60 + clock[1].toInt() >= 5
        }
    }
    compose.onNodeWithTag("chat-video-finish").performClick()
    compose.waitUntil(15_000) { hasTag("chat-video-note-send") }
    val pending = requireNotNull(chat.state.value.pendingMedia)
    assertTrue(pending.videoNote)
    assertEquals("video/mp4", pending.mime)
    assertTrue(requireNotNull(pending.durationMs) in 700..ChatMediaPolicy.VIDEO_NOTE_METADATA_MAX_MS)
    capture("video-note-review")
    compose.onNodeWithTag("chat-video-note-send").performClick()
    waitFor("real video message upload and send", 90) { chat.state.value.messages.any {
        it.id !in before && it.peer == peer && it.outgoing && it.attachment?.videoNote == true
    } }
    val note = chat.state.value.messages.last { it.id !in before && it.peer == peer && it.outgoing && it.attachment?.videoNote == true }
    waitFor("companion authenticated video decryption", 90) { chat.state.value.messages.any {
        !it.outgoing && it.peer == peer && it.protection == MessageProtection.ENCRYPTED && it.body == "Video recibido y descifrado · ${note.id}"
    } }
    waitFor("local video preview") { note.mediaKey in chat.state.value.mediaFiles }
    assertEquals(draftBefore, chat.state.value.drafts[peer])
    val prefix = "chat-video-note-${note.id}"
    // The normal conversation must reveal the newly sent note itself. Forcing a test-only
    // scroll here would hide a broken auto-scroll or an oversized message container.
    compose.waitUntil(15_000) {
        runCatching { compose.onNodeWithTag(prefix, useUnmergedTree = true).assertIsDisplayed() }.isSuccess
    }
    val circle = compose.onNodeWithTag(prefix, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val messageBounds = compose.onNodeWithTag("$prefix-message", useUnmergedTree = true)
        .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
    val density = context.resources.displayMetrics.density
    assertEquals("A video message must retain its circular viewport", circle.width, circle.height, 1f)
    assertTrue("The video message must not become a full-width media card", circle.width < viewport.width * 0.8f)
    assertTrue("The message wrapper must follow the circle instead of filling a rectangular card",
        messageBounds.width <= circle.width + 2f * density)
    assertTrue("The circular video must stay large enough to watch", circle.width / density >= 180f)
    capture("video-note-delivered")
    val toggle = compose.onNodeWithTag("$prefix-toggle")
    toggle.assertIsDisplayed().assertContentDescriptionEquals(context.getString(R.string.chat_play)).performClick()
    compose.waitUntil(15_000) {
        runCatching { toggle.assertContentDescriptionEquals(context.getString(R.string.chat_pause)) }.isSuccess
    }
    fun playbackTime() = compose.onNodeWithTag("$prefix-duration", useUnmergedTree = true)
        .assertIsDisplayed().fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
    val startedAt = playbackTime()
    compose.waitUntil(5_000) { playbackTime() != startedAt }
    capture("video-note-playing")
    toggle.performClick()
    compose.waitUntil(5_000) {
        runCatching { toggle.assertContentDescriptionEquals(context.getString(R.string.chat_play)) }.isSuccess
    }
    Thread.sleep(250)
    val pausedPlayback = playbackTime()
    Thread.sleep(1_200)
    assertEquals("Paused playback must stop the visible elapsed time", pausedPlayback, playbackTime())
    capture("video-note-playback-paused")
    println("LIVE_UI: circular video layout and real local playback verified")

    // Use a real frame from the just-recorded and sent camera video. This exercises the HD
    // photo pipeline without importing stock imagery, invented people or a generated fixture.
    val recordedPath = requireNotNull(chat.state.value.mediaFiles[note.mediaKey])
    val frame = MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(recordedPath)
        requireNotNull(retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC))
    }
    val source = ChatPlatform.captureFile(context, "jpg")
    val width = frame.width
    val height = frame.height
    try {
        source.outputStream().use { assertTrue(frame.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        runBlocking { chat.selectMedia(Uri.fromFile(source)).join() }
        compose.waitUntil(15_000) { hasTag("chat-photo-hd") }
        // The photo editor owns a separate window, like the contact sheet. Activate
        // its accessible control instead of injecting coordinates into another root.
        compose.onNodeWithTag("chat-photo-hd").assertIsDisplayed().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { action -> assertTrue(action()) }
        compose.onNodeWithTag("chat-photo-hd").assertIsSelected()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("chat-image-send") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        capture("photo-hd-review")
        val beforePhoto = chat.state.value.messages.map { it.id }.toSet()
        // Activate the real editor's accessible button. Injecting screen coordinates
        // can target the underlying activity while this separate window settles.
        compose.onNodeWithTag("chat-image-send").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { action -> assertTrue(action()) }
        waitFor("real HD photo upload and send", 90) { chat.state.value.messages.any {
            it.id !in beforePhoto && it.peer == peer && it.outgoing && it.attachment?.mime?.startsWith("image/") == true
        } }
        val photo = chat.state.value.messages.last { it.id !in beforePhoto && it.peer == peer && it.outgoing && it.attachment?.mime?.startsWith("image/") == true }
        waitFor("companion authenticated image decryption", 90) { chat.state.value.messages.any {
            !it.outgoing && it.peer == peer && it.protection == MessageProtection.ENCRYPTED && it.body == "Imagen recibida y descifrada · ${photo.id}"
        } }
        waitFor("local exported HD preview") { photo.mediaKey in chat.state.value.mediaFiles }
        val decoded = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(chat.state.value.mediaFiles[photo.mediaKey], decoded)
        assertEquals(PhotoQuality.HD.dimensions(width, height), decoded.outWidth to decoded.outHeight)
        capture("photo-hd-delivered")
        println("LIVE_UI: actual camera frame sent in HD and decrypted by companion")
        val noteAttachment = requireNotNull(note.attachment)
        return JSONObject().put("camera2Capture", true).put("videoPauseResume", true).put("videoDiscard", true)
            .put("videoMessageId", note.id).put("videoDurationMs", noteAttachment.durationMs)
            .put("videoBytes", noteAttachment.size).put("videoCompanionDecryption", true)
            .put("videoCircularViewport", true).put("videoViewportWidthDp", circle.width / density)
            .put("videoMessageWidthDp", messageBounds.width / density)
            .put("videoVisibleWithoutForcedScroll", true).put("videoPlaybackStarted", true)
            .put("videoPlaybackPaused", true).put("videoDurationDisplayed", true)
            .put("photoMessageId", photo.id).put("photoQualitySelected", "HD")
            .put("photoSource", "frame extracted from the real just-recorded emulator camera video")
            .put("photoSourceWidth", width).put("photoSourceHeight", height)
            .put("photoExportWidth", decoded.outWidth).put("photoExportHeight", decoded.outHeight)
            .put("photoCompanionDecryption", true).put("artificialUpscaling", false)
    } finally { frame.recycle(); source.delete() }
}
