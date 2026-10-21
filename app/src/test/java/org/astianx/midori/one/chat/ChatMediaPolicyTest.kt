/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ChatMediaPolicyTest {
    @Test fun qualityKeepsAspectRatioWithoutInventingResolution() {
        assertEquals(1280 to 960, PhotoQuality.STANDARD.dimensions(4000, 3000))
        assertEquals(2560 to 1920, PhotoQuality.HD.dimensions(4000, 3000))
        assertEquals(960 to 1280, PhotoQuality.STANDARD.dimensions(3000, 4000))
        assertEquals(320 to 240, PhotoQuality.HD.dimensions(320, 240))
        assertTrue(PhotoQuality.HD.jpegQuality > PhotoQuality.STANDARD.jpegQuality)
        assertThrows(IllegalArgumentException::class.java) { PhotoQuality.HD.dimensions(0, 20) }
        assertThrows(IllegalArgumentException::class.java) { PhotoQuality.HD.dimensions(100_000, 100_000) }
    }

    private fun metadata() = JSONObject().put("url", "https://example.org/random.bin")
        .put("key", "a".repeat(64)).put("iv", "b".repeat(24)).put("sha256", "c".repeat(64))
        .put("name", "video.mp4").put("mime", "video/mp4").put("size", 100)

    @Test fun oldFilesRemainReadableAndNotesRoundTripInsideEncryptedEnvelope() {
        val old = ChatPayload.readAttachment(metadata())
        assertFalse(old.videoNote)
        assertNull(old.durationMs)
        val note = old.copy(videoNote = true, durationMs = 50_000)
        val message = ChatMessage("note", "peer@example.org", "", true, 1, attachment = note)
        assertEquals(message, ChatPayload.decode(message.copy(body = ChatPayload.encode(message), attachment = null)))
        assertEquals(note, ChatPayload.readAttachment(ChatPayload.attachmentJson(note)))
    }

    @Test fun malformedNotesCannotPretendToBeAudioOrUnboundedVideo() {
        listOf(metadata().put("videoNote", true),
            metadata().put("videoNote", true).put("durationMs", 0),
            metadata().put("videoNote", true).put("durationMs", 61_000),
            metadata().put("videoNote", true).put("durationMs", 1_000).put("mime", "audio/mp4"),
            metadata().put("durationMs", -1)).forEach { json ->
            assertThrows(IllegalArgumentException::class.java) { ChatPayload.readAttachment(json) }
        }
        assertTrue(ChatPayload.readAttachment(metadata().put("videoNote", true).put("durationMs", 60_033)).videoNote)
        assertThrows(IllegalArgumentException::class.java) {
            ChatPayload.readAttachment(metadata().put("size", ChatFileCrypto.MAX_BYTES + 1))
        }
    }
}
