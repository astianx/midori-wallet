package org.astianx.midori.one.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astianx.midori.one.R

@Composable
internal fun LocalArtwork(name: String?, modifier: Modifier, alpha: Float = 1f) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, name) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching { name?.let { android.graphics.BitmapFactory.decodeFile(ChatArtwork(context).file(it).path)?.asImageBitmap() } }.getOrNull()
        }
    }
    bitmap?.let { Image(it, null, modifier, contentScale = ContentScale.Crop, alpha = alpha) }
}

@Composable
internal fun ChatWallpaper(modifier: Modifier = Modifier) {
    val style = LocalChatAppearance.current
    val opacity = style.wallpaperOpacity / 100f
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = opacity * .4f)
    when (style.wallpaper) {
        ChatWallpaperStyle.PLAIN -> Unit
        ChatWallpaperStyle.PHOTO -> LocalArtwork(style.photo, modifier, opacity)
        ChatWallpaperStyle.BOTANICAL -> Image(painterResource(R.drawable.chat_wallpaper), null, modifier, contentScale = ContentScale.Crop, alpha = opacity)
        ChatWallpaperStyle.DOTS, ChatWallpaperStyle.GRID -> Canvas(modifier) {
            val step = 28.dp.toPx()
            for (x in 0..(size.width / step).toInt()) {
                if (style.wallpaper == ChatWallpaperStyle.GRID) drawLine(color, Offset(x * step, 0f), Offset(x * step, size.height), .6.dp.toPx())
                for (y in 0..(size.height / step).toInt()) {
                    if (style.wallpaper == ChatWallpaperStyle.DOTS) drawCircle(color, 1.2.dp.toPx(), Offset(x * step, y * step))
                    else if (x == 0) drawLine(color, Offset(0f, y * step), Offset(size.width, y * step), .6.dp.toPx())
                }
            }
        }
    }
}
