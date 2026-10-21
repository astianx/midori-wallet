package org.astianx.midori.one.wallet.swap

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.astianx.midori.one.PrimaryButton
import org.astianx.midori.one.R
import org.astianx.midori.one.Screen
import org.astianx.midori.one.ScreenHeader
import org.astianx.midori.one.wallet.Asset
import org.astianx.midori.one.wallet.NetworkPill
import org.astianx.midori.one.wallet.WalletViewModel

@Composable
fun InvestmentScreen(wallet: WalletViewModel, vm: InvestmentViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    DisposableEffect(vm) { onDispose { vm.cancel() } }
    InvestmentContent(s, { wallet.navigate(Screen.Home) }, vm::source, vm::target, vm::amount, vm::quote)
}

@Composable
internal fun InvestmentContent(s: InvestmentState, back: () -> Unit, source: (Asset) -> Unit,
                               target: (Asset) -> Unit, amount: (String) -> Unit, quote: () -> Unit) {
    val focus = LocalFocusManager.current
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(stringResource(R.string.dollars_invest), back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.invest_intro), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NetworkPill("ETH", "Ξ", s.target == Asset.ETH) { target(Asset.ETH) }
                NetworkPill("BTC", "₿", s.target == Asset.BTC) { target(Asset.BTC) }
            }
            if (s.target == Asset.BTC) {
                Text(stringResource(R.string.invest_btc_pending), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(stringResource(R.string.invest_source), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NetworkPill("USDC", "$", s.source == Asset.USDC) { source(Asset.USDC) }
                    NetworkPill("USDT", "₮", s.source == Asset.USDT) { source(Asset.USDT) }
                }
                OutlinedTextField(s.amount, amount, label = { Text(stringResource(R.string.amount)) },
                    suffix = { Text(s.source.name) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("investment-amount"))
                PrimaryButton(stringResource(R.string.invest_quote_action), enabled = !s.loading && s.amount.isNotBlank()) {
                    focus.clearFocus()
                    quote()
                }
                if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                s.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
                s.quote?.let { result ->
                    var expired by remember(result) { mutableStateOf(System.currentTimeMillis() >= result.expiresAt) }
                    LaunchedEffect(result) {
                        delay((result.expiresAt - System.currentTimeMillis()).coerceAtLeast(0))
                        expired = true
                    }
                    if (expired) Text(stringResource(R.string.invest_quote_expired), color = MaterialTheme.colorScheme.error)
                    else Surface(Modifier.fillMaxWidth().testTag("investment-quote"), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(stringResource(R.string.invest_estimated_receive), style = MaterialTheme.typography.labelLarge)
                            Text("${result.ethAmount} ETH", style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"))
                            Text(stringResource(R.string.invest_gas_reference, result.indicativeGasUsd))
                            Text(stringResource(R.string.invest_provider), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.invest_quote_reference), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Text(stringResource(R.string.invest_execution_pending), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.invest_risk), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
