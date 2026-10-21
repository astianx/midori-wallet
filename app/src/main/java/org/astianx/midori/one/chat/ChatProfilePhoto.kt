/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

/** Re-encode a private user-selected image: small public avatar with no original EXIF/location. */
internal fun publicProfilePhoto(file: File): String {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, options)
    require(options.outWidth in 1..1600 && options.outHeight in 1..1600)
    val original = requireNotNull(BitmapFactory.decodeFile(file.path))
    val ratio = minOf(1.0, 320.0 / maxOf(original.width, original.height))
    val scaled = Bitmap.createScaledBitmap(original, maxOf(1, (original.width * ratio).toInt()), maxOf(1, (original.height * ratio).toInt()), true)
    try {
        val bytes = ByteArrayOutputStream().use { output ->
            check(scaled.compress(Bitmap.CompressFormat.JPEG, 82, output))
            output.toByteArray()
        }
        require(bytes.size <= PublishedChatProfile.MAX_AVATAR_BYTES)
        return java.util.Base64.getEncoder().encodeToString(bytes)
    } finally { if (scaled !== original) scaled.recycle(); original.recycle() }
}

internal fun decodeProfilePhoto(value: PublishedChatProfile): Bitmap? = runCatching {
    value.validate()
    val encoded = value.photoBase64 ?: return null
    val bytes = java.util.Base64.getDecoder().decode(encoded)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth in 1..1024 && bounds.outHeight in 1..1024)
    val options = BitmapFactory.Options().apply {
        inSampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 320) inSampleSize *= 2
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}.getOrNull()
