/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.astianx.midori.one.R

@Composable
internal fun ChatPhotoQualitySelector(quality: PhotoQuality, enabled: Boolean, onSelect: (PhotoQuality) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = quality == PhotoQuality.STANDARD, onClick = { onSelect(PhotoQuality.STANDARD) },
                enabled = enabled, modifier = Modifier.testTag("chat-photo-standard"), label = { Text(stringResource(R.string.chat_photo_standard)) })
            FilterChip(selected = quality == PhotoQuality.HD, onClick = { onSelect(PhotoQuality.HD) },
                enabled = enabled, modifier = Modifier.testTag("chat-photo-hd"), label = { Text(stringResource(R.string.chat_photo_hd)) })
        }
        Text(stringResource(if (quality == PhotoQuality.HD) R.string.chat_photo_hd_hint else R.string.chat_photo_standard_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.chat_photo_original_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
    }
}
