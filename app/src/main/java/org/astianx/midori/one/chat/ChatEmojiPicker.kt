/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.emoji2.emojipicker.RecentEmojiProvider
import org.astianx.midori.one.LocalDarkTheme

/** Selection history stays in this conversation session, never in plaintext preferences. */
internal class SessionEmojiHistory : RecentEmojiProvider {
    private val recent = ArrayList<String>()
    override suspend fun getRecentEmojiList(): List<String> = synchronized(recent) { recent.toList() }
    override fun recordSelection(emoji: String) {
        synchronized(recent) {
            recent.remove(emoji)
            recent.add(0, emoji)
            if (recent.size > 27) recent.removeAt(recent.lastIndex)
        }
    }
}

@Composable
internal fun ChatEmojiTray(history: SessionEmojiHistory, onSelect: (String) -> Unit) {
    val callback = rememberUpdatedState(onSelect)
    val dark = LocalDarkTheme.current
    val surface = MaterialTheme.colorScheme.surface.toArgb()
    val configuration = LocalConfiguration.current
    val height = (configuration.screenHeightDp * .38f).coerceIn(180f, 320f).dp
    AndroidView(modifier = Modifier.fillMaxWidth().height(height), factory = { context ->
        val configured = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val themed = ContextThemeWrapper(context.createConfigurationContext(configured),
            if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        EmojiPickerView(themed).apply {
            emojiGridColumns = 8
            setRecentEmojiProvider(history)
            setOnEmojiPickedListener { callback.value(it.emoji) }
            setBackgroundColor(surface)
        }
    }, update = { it.setBackgroundColor(surface) })
}
