package org.astianx.midori.one.chat

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal object ChatPlatform {
    fun captureFile(context: Context, extension: String): File {
        val folder = File(context.cacheDir, "chat-media/capture").apply { mkdirs() }
        return File(folder, "${UUID.randomUUID()}.$extension")
    }
    fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.chat.files", file)
    fun open(context: Context, path: String, mime: String): Boolean = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri(context, File(path)), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }.isSuccess
    fun preview(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 100_000_000) return null
        val sample = (maxOf(bounds.outWidth, bounds.outHeight) / 1200).coerceAtLeast(1)
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    fun preview(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 100_000_000) return null
        val sample = (maxOf(bounds.outWidth, bounds.outHeight) / 1200).coerceAtLeast(1)
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
    /** One foreground location request; no last-known or continuous tracking. Caller obtains permission. */
    @Suppress("MissingPermission")
    suspend fun location(context: Context): String? = withTimeoutOrNull(20_000) {
        val manager = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { manager.isProviderEnabled(it) }
        if (providers.isEmpty()) return@withTimeoutOrNull null
        suspendCancellableCoroutine { continuation ->
            val signals = providers.map { CancellationSignal() }
            var remaining = providers.size
            continuation.invokeOnCancellation { signals.forEach { it.cancel() } }
            fun received(location: android.location.Location?) {
                remaining--
                if (continuation.isActive && (location != null || remaining == 0)) {
                    signals.forEach { it.cancel() }
                    continuation.resume(location?.let { "geo:${it.latitude},${it.longitude}" })
                }
            }
            providers.forEachIndexed { index, provider ->
                try { manager.getCurrentLocation(provider, signals[index], context.mainExecutor, ::received) }
                catch (_: SecurityException) { received(null) }
                catch (_: IllegalArgumentException) { received(null) }
            }
        }
    }
}
