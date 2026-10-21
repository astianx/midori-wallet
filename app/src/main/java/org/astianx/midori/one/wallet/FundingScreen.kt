package org.astianx.midori.one.wallet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.astianx.midori.one.R
import org.astianx.midori.one.Screen
import org.astianx.midori.one.ScreenHeader
import org.astianx.midori.one.ShortcutRow
import org.astianx.midori.one.wallet.fiat.FiatOperation

@Composable
fun FundingScreen(wallet: WalletViewModel) {
    val state by wallet.state.collectAsState()
    FundingContent(state.fiatOperation, { wallet.navigate(Screen.Home) }, wallet::openFunding,
        onCrypto = { if (state.fiatOperation == FiatOperation.BUY) wallet.openReceive(Asset.USDC, Screen.Funding)
            else { wallet.resetSend(); wallet.navigate(Screen.Send) } },
        onP2p = { wallet.navigate(Screen.P2p) }, onProviders = { wallet.openFiat(state.fiatOperation) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FundingContent(operation: FiatOperation, back: () -> Unit, choose: (FiatOperation) -> Unit,
    onCrypto: () -> Unit, onP2p: () -> Unit, onProviders: () -> Unit) {
    BackHandler(onBack = back)
    val deposit = operation == FiatOperation.BUY
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(if (deposit) R.string.wallet_deposit else R.string.wallet_withdraw), back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(if (deposit) R.string.funding_intro else R.string.funding_withdraw_intro),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FiatOperation.entries.forEach { value -> FilterChip(value == operation, onClick = { choose(value) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = { Text(stringResource(if (value == FiatOperation.BUY) R.string.wallet_deposit else R.string.wallet_withdraw)) }) }
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            ShortcutRow(Icons.Outlined.QrCode, stringResource(if (deposit) R.string.funding_crypto else R.string.funding_crypto_send),
                stringResource(if (deposit) R.string.funding_crypto_body else R.string.funding_crypto_send_body), onCrypto)
            ShortcutRow(Icons.Outlined.People, stringResource(R.string.funding_p2p),
                stringResource(R.string.funding_p2p_body), onP2p)
            ShortcutRow(Icons.Outlined.Payments, stringResource(R.string.funding_providers),
                stringResource(R.string.funding_providers_body), onProviders)
            Text(stringResource(R.string.funding_availability), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        }
    }
}
