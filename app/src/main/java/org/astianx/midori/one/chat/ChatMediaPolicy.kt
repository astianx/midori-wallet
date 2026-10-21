/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import kotlin.math.roundToInt

/** Bounds are enforced before encoding/upload, not merely suggested by the interface. */
internal enum class PhotoQuality(val maxSide: Int, val jpegQuality: Int) {
    STANDARD(1280, 80), HD(2560, 92);

    fun dimensions(width: Int, height: Int) = fitPhotoDimensions(width, height, maxSide)
}

internal fun fitPhotoDimensions(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
    require(width > 0 && height > 0 && maxSide > 0 && width.toLong() * height <= 100_000_000)
    val scale = minOf(1.0, maxSide.toDouble() / maxOf(width, height))
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}

internal object ChatMediaPolicy {
    const val VIDEO_NOTE_MAX_MS = 60_000L
    // Containers can round their duration up by one encoded frame at the recording boundary.
    const val VIDEO_NOTE_METADATA_MAX_MS = VIDEO_NOTE_MAX_MS + 250
    fun validateVideoMetadata(mime: String, videoNote: Boolean, durationMs: Long?) {
        if (durationMs != null) require(mime.startsWith("video/") && durationMs in 1..86_400_000L)
        if (videoNote) require(mime == "video/mp4" && durationMs != null && durationMs in 1..VIDEO_NOTE_METADATA_MAX_MS)
    }
}
