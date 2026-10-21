package org.astianx.midori.one.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import java.io.File
import kotlin.math.roundToInt

internal data class PhotoPoint(val x: Float, val y: Float) : java.io.Serializable
internal data class PhotoRect(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) : java.io.Serializable {
    val width get() = right - left
    val height get() = bottom - top
    fun point(x: Float, y: Float) = PhotoPoint(left + x.coerceIn(0f, 1f) * width, top + y.coerceIn(0f, 1f) * height)
    companion object {
        fun between(a: PhotoPoint, b: PhotoPoint): PhotoRect? {
            val rect = PhotoRect(minOf(a.x, b.x).coerceIn(0f, 1f), minOf(a.y, b.y).coerceIn(0f, 1f),
                maxOf(a.x, b.x).coerceIn(0f, 1f), maxOf(a.y, b.y).coerceIn(0f, 1f))
            return rect.takeIf { it.width >= .08f && it.height >= .08f }
        }
    }
}
internal sealed interface PhotoMark : java.io.Serializable {
    data class Stroke(val points: List<PhotoPoint>, val color: Int) : PhotoMark
    data class Label(val text: String, val point: PhotoPoint, val color: Int) : PhotoMark
}
internal data class PhotoEdits(val crop: PhotoRect = PhotoRect(), val marks: List<PhotoMark> = emptyList()) : java.io.Serializable

/** Shared renderer for on-screen review and export, so the sent pixels match the preview. */
internal object ChatPhotoEditor {
    fun decode(context: Context, uri: Uri, maxSide: Int = 2048): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val (width, height) = fitPhotoDimensions(info.size.width, info.size.height, maxSide)
            decoder.setTargetSize(width, height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    fun draw(canvas: Canvas, source: Bitmap, edits: PhotoEdits, width: Float, height: Float) {
        val crop = edits.crop
        canvas.save()
        canvas.clipRect(0f, 0f, width, height)
        canvas.scale(width / (source.width * crop.width), height / (source.height * crop.height))
        canvas.translate(-crop.left * source.width, -crop.top * source.height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, 0f, 0f, paint)
        edits.marks.forEach { mark ->
            when (mark) {
                is PhotoMark.Stroke -> {
                    paint.color = mark.color; paint.style = Paint.Style.STROKE
                    paint.strokeWidth = source.width * .008f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
                    val first = mark.points.firstOrNull() ?: return@forEach
                    if (mark.points.size == 1) canvas.drawPoint(first.x * source.width, first.y * source.height, paint)
                    else {
                        val path = Path().apply { moveTo(first.x * source.width, first.y * source.height)
                            mark.points.drop(1).forEach { lineTo(it.x * source.width, it.y * source.height) } }
                        canvas.drawPath(path, paint)
                    }
                }
                is PhotoMark.Label -> {
                    paint.style = Paint.Style.FILL; paint.color = mark.color; paint.textAlign = Paint.Align.CENTER
                    paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                    paint.textSize = source.width * .045f
                    paint.setShadowLayer(source.width * .002f, 0f, source.width * .001f, Color.BLACK)
                    mark.text.lines().forEachIndexed { index, line ->
                        canvas.drawText(line, mark.point.x * source.width, mark.point.y * source.height + index * paint.textSize * 1.2f, paint)
                    }
                    paint.clearShadowLayer()
                }
            }
        }
        canvas.restore()
    }

    fun render(source: Bitmap, edits: PhotoEdits): Bitmap {
        val target = Bitmap.createBitmap((source.width * edits.crop.width).roundToInt().coerceAtLeast(1),
            (source.height * edits.crop.height).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        target.setHasAlpha(source.hasAlpha())
        draw(Canvas(target), source, edits, target.width.toFloat(), target.height.toFloat())
        return target
    }
    fun export(context: Context, source: Bitmap, edits: PhotoEdits, quality: PhotoQuality = PhotoQuality.STANDARD): File {
        val bitmap = render(source, edits)
        val file = ChatPlatform.captureFile(context, if (source.hasAlpha()) "png" else "jpg")
        try {
            file.outputStream().use { require(bitmap.compress(if (source.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality.jpegQuality, it)) }
            if (file.length() !in 1..ChatFileCrypto.MAX_BYTES) throw ChatException(org.astianx.midori.one.UiText(org.astianx.midori.one.R.string.chat_file_too_large))
            return file
        } catch (e: Exception) { file.delete(); throw e }
        finally { bitmap.recycle() }
    }
}
