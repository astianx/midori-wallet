package org.astianx.midori.one.wallet.p2p

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.astianx.midori.one.BuildConfig
import org.astianx.midori.one.PrimaryButton
import org.astianx.midori.one.R
import org.astianx.midori.one.ScreenHeader
import org.astianx.midori.one.wallet.WalletViewModel
import org.astianx.midori.one.wallet.fiat.FiatOperation

@Composable
internal fun P2pScreen(wallet: WalletViewModel) {
    P2pOrdersScreen(wallet)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun P2pContent(state: P2pState, back: () -> Unit, operation: (FiatOperation) -> Unit,
    country: (String) -> Unit, rates: () -> Unit, retry: () -> Unit) {
    BackHandler(onBack = back)
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.funding_p2p), back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.p2p_intro), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { FiatOperation.entries.forEach { value ->
                FilterChip(value == state.operation, onClick = { operation(value) }, modifier = Modifier.heightIn(min = 48.dp), label = {
                    Text(stringResource(if (value == FiatOperation.BUY) R.string.wallet_deposit else R.string.wallet_withdraw)) }) } }
            Text(stringResource(R.string.p2p_network), style = MaterialTheme.typography.labelLarge)
            var expanded by remember { mutableStateOf(false) }
            val selected = state.available.find { it.country == state.country }
            Box {
                OutlinedButton(onClick = { expanded = true }, enabled = state.available.isNotEmpty() && !state.busy,
                    shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("p2p-country")) {
                    Text(selected?.let { "${java.util.Locale("", it.country).getDisplayCountry(locale)} · ${it.fiat}" }
                        ?: stringResource(R.string.fiat_select_country))
                }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) { state.available.forEach { row ->
                    DropdownMenuItem(text = { Text("${java.util.Locale("", row.country).getDisplayCountry(locale)} · ${row.fiat}") },
                        onClick = { expanded = false; country(row.country) }) } }
            }
            selected?.let { Text(stringResource(R.string.p2p_payment_method, stringResource(paymentMethodLabel(it.paymentMethod))),
                style = MaterialTheme.typography.labelLarge) }
            if (selected?.alpha == true) Text(stringResource(R.string.p2p_alpha), style = MaterialTheme.typography.bodySmall)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            if (state.configured && state.countries.isEmpty() && state.error != null) OutlinedButton(retry, enabled = !state.busy) { Text(stringResource(R.string.fiat_retry)) }
            PrimaryButton(stringResource(R.string.p2p_check_rate), selected != null && !state.busy, rates)
            state.rate?.let { rate ->
                val price = if (state.operation == FiatOperation.BUY) rate.buyPrice else rate.sellPrice
                Surface(Modifier.fillMaxWidth().testTag("p2p-rate"), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(R.string.p2p_rate, p2pDecimal(price), rate.country.fiat), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.p2p_reference), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.p2p_fees), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.p2p_provider), style = MaterialTheme.typography.labelMedium)
                } }
            }
            Text(stringResource(R.string.p2p_pending), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun paymentMethodLabel(method: String): Int = when (method) {
    "upi" -> R.string.p2p_method_upi
    "pix" -> R.string.p2p_method_pix
    "qris" -> R.string.p2p_method_qris
    "pago_movil" -> R.string.p2p_method_pago_movil
    "alias_cbu" -> R.string.p2p_method_alias
    "qr_simple" -> R.string.p2p_method_qr_simple
    "nip" -> R.string.p2p_method_nip
    "transfermovil" -> R.string.p2p_method_transfermovil
    "yape_plin_cci" -> R.string.p2p_method_yape
    "instapay" -> R.string.p2p_method_instapay
    "mpesa" -> R.string.p2p_method_mpesa
    "bank_transfer" -> R.string.fiat_method_bank
    else -> R.string.fiat_method_other
}
