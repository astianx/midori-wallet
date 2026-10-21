package org.astianx.midori.one.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.astianx.midori.one.*
import org.astianx.midori.one.R

@Composable
internal fun WalletAppearanceScreen(back: () -> Unit) {
    val style = LocalWalletAppearance.current
    val save = LocalWalletAppearanceChange.current
    StylePage(R.string.style_wallet, back) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Box {
                Column(Modifier.fillMaxWidth().padding(if (style.compact) 16.dp else 24.dp)) {
                    Text(stringResource(R.string.style_preview), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.digital_dollars), style = MaterialTheme.typography.titleMedium)
                    Text(if (style.hideBalances) "•••• ${style.preferredToken.name}" else style.preferredToken.name,
                        style = MaterialTheme.typography.headlineLarge)
                }
            }
        }
        StyleToggle(R.string.hide_balances, style.hideBalances) { save(style.copy(hideBalances = it)) }
        StyleToggle(R.string.style_compact, style.compact) { save(style.copy(compact = it)) }
        StyleToggle(R.string.style_btc_first, style.bitcoinFirst) { save(style.copy(bitcoinFirst = it)) }
        StyleToggle(R.string.style_help, style.showHelp) { save(style.copy(showHelp = it)) }
        StyleChoices(R.string.style_preferred_token, listOf(Asset.USDC, Asset.USDT), style.preferredToken, { it.name }) { save(style.copy(preferredToken = it)) }
        Text(stringResource(R.string.style_token_hint), style = MaterialTheme.typography.bodySmall)
        TextButton({ save(WalletAppearance()) }) { Text(stringResource(R.string.style_reset)) }
    }
}
