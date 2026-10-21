package org.astianx.midori.one.wallet.fiat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import org.astianx.midori.one.BuildConfig
import org.astianx.midori.one.PrimaryButton
import org.astianx.midori.one.R
import org.astianx.midori.one.Screen
import org.astianx.midori.one.ScreenHeader
import org.astianx.midori.one.wallet.Asset
import org.astianx.midori.one.wallet.WalletCountryGate
import org.astianx.midori.one.wallet.WalletViewModel

@Composable
internal fun FiatScreen(wallet: WalletViewModel) {
    val account by wallet.state.collectAsState()
    val identity = account.ethAddress
    val bitcoin = account.btcAddress
    if (account.country.isBlank()) {
        WalletCountryGate(wallet) { wallet.openFunding(account.fiatOperation) }
        return
    }
    val vm: FiatViewModel = viewModel(key = "fiat:$identity:$bitcoin:${account.fiatOperation}:${account.country}", factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = FiatViewModel(account.fiatOperation,
                runCatching { HttpFiatGateway(BuildConfig.FIAT_BACKEND_URL) }.getOrNull(), identity, bitcoin,
                { wallet.state.value.let { it.isWdkInitialized && it.ethAddress == identity && it.btcAddress == bitcoin } },
                wallet::signFiatChallenge, account.country) as T
        })
    val state by vm.state.collectAsState()
    LaunchedEffect(state.offers, state.busy) {
        state.offers.minOfOrNull { it.expiresAt }?.let {
            kotlinx.coroutines.delay((it * 1000 - System.currentTimeMillis()).coerceAtLeast(0) + 100)
            vm.expireOffers()
        }
    }
    val context = LocalContext.current
    LaunchedEffect(vm, account.fiatOperation) { vm.enter(account.fiatOperation) }
    DisposableEffect(vm) { onDispose { vm.cancelCheckout() } }
    var openedCheckout by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && openedCheckout) {
                openedCheckout = false
                wallet.fetchBalance() // Returning from the browser is never proof of payment.
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val back = { if (account.fiatReturnScreen == Screen.Funding) wallet.openFunding(state.operation)
        else wallet.navigate(account.fiatReturnScreen) }
    BackHandler(onBack = back)
    LaunchedEffect(state.widgetUrl) {
        state.widgetUrl?.let { url ->
            vm.consumeWidget()
            try {
                val checkedUrl = validateFiatCheckoutUrl(url, fiatBackendOrigin(BuildConfig.FIAT_BACKEND_URL))
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkedUrl)))
                openedCheckout = true
            }
            catch (_: Exception) { vm.browserFailed() }
        }
    }
    FiatContent(state, if (state.asset == Asset.BTC) bitcoin else identity,
        onBack = back, onOperation = vm::operation, onAsset = vm::asset,
        onFiat = vm::fiat, onAmount = vm::amount, onRetry = vm::reload, onContinue = vm::checkout,
        onCountry = vm::country, onCompare = vm::compare, onOffer = vm::chooseOffer)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FiatContent(state: FiatUiState, address: String, onBack: () -> Unit,
    onOperation: (FiatOperation) -> Unit, onAsset: (Asset) -> Unit, onFiat: (String) -> Unit,
    onAmount: (String) -> Unit, onRetry: () -> Unit, onContinue: () -> Unit,
    onCountry: (String) -> Unit, onCompare: () -> Unit, onOffer: (String) -> Unit) {
    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(stringResource(R.string.fiat_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.fiat_intro), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FiatOperation.entries.forEach { operation ->
                    FilterChip(selected = state.operation == operation, enabled = !state.busy,
                        onClick = { onOperation(operation) }, modifier = Modifier.heightIn(min = 48.dp), label = {
                            Text(stringResource(if (operation == FiatOperation.BUY) R.string.buy else R.string.sell)) })
                }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.capabilities != null) {
                if (state.assets.isEmpty() || state.fiats.isEmpty()) {
                    Text(stringResource(R.string.fiat_no_assets), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ChoiceField(stringResource(R.string.fiat_asset), state.asset.name, state.assets.map { it.name },
                        !state.busy) { onAsset(Asset.valueOf(it)) }
                    Text(stringResource(R.string.fiat_network, state.asset.network.label),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChoiceField(stringResource(R.string.fiat_currency), state.fiat, state.fiats.map { it.code },
                        !state.busy, onFiat)
                    val locale = LocalConfiguration.current.locales[0]
                    val countries = state.countries.associate { it.code to java.util.Locale("", it.code).getDisplayCountry(locale) }
                    if (state.locked) {
                        val name = java.util.Locale("", state.country).getDisplayCountry(locale)
                        Text(stringResource(R.string.wallet_country_locked, name), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag("fiat-country"))
                        if (state.country !in countries.keys) Text(stringResource(R.string.wallet_country_unavailable_fiat, name),
                            color = MaterialTheme.colorScheme.error)
                    } else ChoiceField(stringResource(R.string.fiat_country), countries[state.country] ?: stringResource(R.string.fiat_select_country),
                        countries.values.sorted(), !state.busy) { name -> onCountry(countries.entries.first { it.value == name }.key) }
                    OutlinedTextField(value = state.amount, onValueChange = onAmount, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.fiat_amount,
                            if (state.operation == FiatOperation.BUY) state.fiat else state.asset.name)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.amount.isNotBlank() && state.amountUnits == null)
                    if (state.amount.isNotBlank() && state.amountUnits == null) Text(stringResource(R.string.error_amount),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (state.operation == FiatOperation.BUY) {
                        Text(stringResource(R.string.fiat_destination), style = MaterialTheme.typography.labelLarge)
                        SelectionContainer { Text(address, style = MaterialTheme.typography.bodyMedium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
                    } else Text(stringResource(R.string.fiat_sell_body), style = MaterialTheme.typography.bodyMedium)
                    if (state.asset == Asset.USDT) Text(stringResource(R.string.usdt_bridge_description),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            state.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
            if (state.error != null && state.capabilities == null && state.configured) {
                OutlinedButton(onClick = onRetry, enabled = !state.busy) { Text(stringResource(R.string.fiat_retry)) }
            }
            if (state.offers.isNotEmpty()) {
                Text(stringResource(R.string.fiat_options), style = MaterialTheme.typography.titleMedium)
                val fiatDecimals = state.fiats.first { it.code == state.fiat }.decimals
                state.offers.forEach { offer ->
                    OfferRow(offer, state.selectedOfferId == offer.id, fiatDecimals, !state.busy) { onOffer(offer.id) }
                }
                Text(stringResource(R.string.fiat_estimates_notice), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.configured) {
                if (state.offers.isEmpty()) PrimaryButton(stringResource(R.string.fiat_compare), state.canCompare, onCompare)
                else {
                    PrimaryButton(stringResource(R.string.fiat_continue), state.canContinue, onContinue)
                    TextButton(onClick = onCompare, enabled = state.canCompare) { Text(stringResource(R.string.fiat_refresh_offers)) }
                }
                Text(stringResource(R.string.fiat_provider_notice), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun OfferRow(offer: FiatOffer, selected: Boolean, fiatDecimals: Int, enabled: Boolean, onSelect: () -> Unit) {
    fun amount(units: String, decimals: Int) = java.math.BigDecimal(units).movePointLeft(decimals).stripTrailingZeros().toPlainString()
    val received = if (offer.selection.operation == FiatOperation.BUY)
        "${amount(offer.cryptoAmountUnits, offer.selection.asset.decimals)} ${offer.selection.asset}" else
        "${amount(offer.fiatAmountUnits, fiatDecimals)} ${offer.selection.fiat}"
    val method = when (offer.paymentMethod) {
        "credit_debit_card" -> R.string.fiat_method_card
        "sepa_bank_transfer", "gbp_bank_transfer", "bank_transfer", "ach_bank_transfer" -> R.string.fiat_method_bank
        "apple_pay" -> R.string.fiat_method_apple
        "google_pay" -> R.string.fiat_method_google
        else -> R.string.fiat_method_other
    }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected, onClick = null, enabled = enabled)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.fiat_receive_estimate, received), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.fiat_offer_fee, "${amount(offer.feeUnits, fiatDecimals)} ${offer.selection.fiat}"),
                    style = MaterialTheme.typography.bodySmall)
                Text("${offer.providerName} · ${stringResource(method)}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ChoiceField(label: String, value: String, choices: List<String>, enabled: Boolean,
    onChoice: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(value, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                choices.forEach { choice -> DropdownMenuItem(text = { Text(choice) },
                    onClick = { expanded = false; onChoice(choice) }) }
            }
        }
    }
}
