package org.astianx.midori.one

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astianx.midori.one.chat.ChatViewModel
import org.astianx.midori.one.wallet.LocalWalletAppearance
import org.astianx.midori.one.wallet.LocalWalletAppearanceChange

@Composable
internal fun PersonalizationScreen(chat: ChatViewModel, themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit,
    onStyleChange: (AppStyle) -> Unit, navigate: (Screen) -> Unit) {
    val context = LocalContext.current
    val app = LocalAppStyle.current
    val wallet = LocalWalletAppearance.current
    val changeWallet = LocalWalletAppearanceChange.current
    val chats by chat.state.collectAsState()
    val scope = rememberCoroutineScope()
    var launcher by remember { mutableStateOf(LauncherIcons.current(context)) }
    var message by remember { mutableStateOf<Int?>(null) }
    var working by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            working = true
            val bundle = ThemeBundle(app, chats.appearance, wallet).encode()
            message = try {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bundle.toByteArray()) } }
                R.string.style_exported
            } catch (_: Exception) { R.string.style_file_error }
            message?.let { android.widget.Toast.makeText(context, context.getString(it), android.widget.Toast.LENGTH_LONG).show() }
            working = false
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            working = true
            message = try {
                val bundle = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readNBytes(ThemeBundle.MAX_BYTES + 1) }
                    ThemeBundle.decode(bytes)
                }
                chat.clearError()
                chat.setAppearance(bundle.chats).join()
                if (chat.state.value.error != null) error("Cannot persist appearance")
                onStyleChange(bundle.app); changeWallet(bundle.wallet)
                R.string.style_imported
            } catch (_: Exception) { R.string.style_file_error }
            message?.let { android.widget.Toast.makeText(context, context.getString(it), android.widget.Toast.LENGTH_LONG).show() }
            working = false
        }
    }
    StylePage(R.string.style_title, { navigate(Screen.More) }) {
        Text(stringResource(R.string.style_intro), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.style_preview), style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.style_preview_body), style = MaterialTheme.typography.bodyLarge)
            }
        }
        ThemePicker(themeMode, onThemeChange)
        Text(stringResource(R.string.style_accent), style = MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AccentTone.entries.size) { index ->
                val tone = AccentTone.entries[index]
                FilterChip(app.accent == tone, { onStyleChange(app.copy(accent = tone)) },
                    label = { Text(stringResource(tone.label)) }, leadingIcon = {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(Color(tone.color))) })
            }
        }
        StyleChoices(R.string.style_typeface, AppTypeface.entries, app.typeface, { stringResource(it.label) }) { onStyleChange(app.copy(typeface = it)) }
        StyleSlider(R.string.style_text_scale, app.textScale, 90..130) { onStyleChange(app.copy(textScale = it)) }
        StyleToggle(R.string.style_black, app.pureBlack) { onStyleChange(app.copy(pureBlack = it)) }
        StyleChoices(R.string.style_icon, LauncherStyle.entries, launcher, { stringResource(it.label) }) { selected ->
            runCatching { LauncherIcons.select(context, selected) }.onSuccess { launcher = selected }
                .onFailure { message = R.string.style_icon_error }
        }
        Text(stringResource(R.string.style_icon_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        ShortcutRow(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.style_chats), stringResource(R.string.style_chats_summary), { navigate(Screen.ChatAppearance) })
        ShortcutRow(Icons.Outlined.AccountBalanceWallet, stringResource(R.string.style_wallet), stringResource(R.string.style_wallet_summary), { navigate(Screen.WalletAppearance) })
        HorizontalDivider()
        Text(stringResource(R.string.style_portable_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton({ export.launch("midori-theme.json") }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.style_export)) }
        OutlinedButton({ import.launch(arrayOf("application/json", "text/plain")) }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.style_import)) }
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
        TextButton({ confirmReset = true }) { Text(stringResource(R.string.style_reset)) }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text(stringResource(R.string.style_reset)) },
        text = { Text(stringResource(R.string.style_reset_body)) },
        confirmButton = { TextButton({ confirmReset = false; onStyleChange(AppStyle()); onThemeChange(ThemeMode.SYSTEM) }) { Text(stringResource(R.string.style_reset)) } },
        dismissButton = { TextButton({ confirmReset = false }) { Text(stringResource(R.string.cancel)) } })
}
