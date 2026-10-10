package org.astianx.midori.one.wallet.p2p

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.astianx.midori.one.BuildConfig
import org.astianx.midori.one.PrimaryButton
import org.astianx.midori.one.R
import org.astianx.midori.one.ScreenHeader
import org.astianx.midori.one.wallet.WalletViewModel
import org.astianx.midori.one.wallet.fiat.FiatOperation
import java.math.BigDecimal

@Composable
internal fun P2pOrdersScreen(wallet: WalletViewModel) {
    val account by wallet.state.collectAsState()
    val context = LocalContext.current.applicationContext
    // One lifecycle per wallet, shared across deposit/withdraw navigation. No wallet replacement.
    val vm: P2pOrderViewModel = viewModel(key = "p2p-orders:${account.ethAddress}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val catalog = runCatching { HttpP2pGateway(BuildConfig.FIAT_BACKEND_URL) }.getOrNull()
            val interactor = runCatching { P2pOrderInteractor(account.ethAddress, HttpP2pOrderGateway(BuildConfig.FIAT_BACKEND_URL),
                KeystoreP2pOrderStore(context), P2pEcies(), wallet.p2pWalletPort()) }.getOrNull()
            return P2pOrderViewModel(account.fiatOperation, catalog, interactor) as T
        }
    })
    val state by vm.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { delay(15_000); vm.refreshPending() }
        }
    }
    LaunchedEffect(state.records) { if (state.records.isNotEmpty()) wallet.fetchBalance() }
    LaunchedEffect(vm, account.fiatOperation) { vm.enter(account.fiatOperation) }
    LaunchedEffect(state.review) { state.review?.let {
        delay((it.expiresAt * 1000 - System.currentTimeMillis()).coerceAtLeast(0) + 100); vm.expire()
    } }
    DisposableEffect(vm) { onDispose { vm.dismiss() } }
    P2pOrderContent(state, { wallet.openFunding(state.operation) }, vm::operation, vm::country, vm::amount,
        vm::destination, vm::quote, vm::reload, vm::action, vm::refresh, vm::instructions, vm::recover,
        vm::submit, vm::dismiss)
}

@Composable
internal fun P2pOrderContent(s: P2pOrderState, back: () -> Unit, operation: (FiatOperation) -> Unit,
    country: (String) -> Unit, amount: (String) -> Unit, destination: (String) -> Unit, quote: () -> Unit,
    reload: () -> Unit, action: (String, P2pAction, String?) -> Unit, refresh: (String) -> Unit,
    instructions: (String) -> Unit, recover: (String, String) -> Unit, submit: (Boolean) -> Unit, dismiss: () -> Unit) {
    BackHandler { if (s.review != null) dismiss() else back() }
    val locale = LocalConfiguration.current.locales[0]
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.funding_p2p), back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.p2po_intro), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { FiatOperation.entries.forEach { value ->
                FilterChip(value == s.operation, enabled = !s.busy, onClick = { operation(value) }, label = {
                    Text(stringResource(if (value == FiatOperation.BUY) R.string.wallet_deposit else R.string.wallet_withdraw)) }) } }
            Text(stringResource(R.string.p2p_network), style = MaterialTheme.typography.labelLarge)
            var expanded by remember { mutableStateOf(false) }
            val selected = s.available.find { it.country == s.country }
            Box {
                OutlinedButton({ expanded = true }, enabled = !s.busy && s.available.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("p2po-country")) {
                    Text(selected?.let { "${java.util.Locale("", it.country).getDisplayCountry(locale)} · ${it.fiat}" }
                        ?: stringResource(R.string.fiat_select_country))
                }
                DropdownMenu(expanded, { expanded = false }) { s.available.forEach { row ->
                    DropdownMenuItem(text = { Text("${java.util.Locale("", row.country).getDisplayCountry(locale)} · ${row.fiat}") },
                        onClick = { expanded = false; country(row.country) }) } }
            }
            if (selected?.alpha == true) Text(stringResource(R.string.p2p_alpha), style = MaterialTheme.typography.bodySmall)
            s.account?.let { a ->
                Card(Modifier.fillMaxWidth().testTag("p2po-account")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.p2po_account_limit, p2pDecimal(a.limit(s.operation))))
                        if (s.operation == FiatOperation.SELL) Text(stringResource(R.string.p2po_account_balance, p2pDecimal(a.usdcBalance)))
                        if (a.limit(s.operation) == "0") Text(stringResource(R.string.p2po_account_disabled))
                    }
                }
            }
            OutlinedTextField(s.amount, amount, enabled = !s.busy, label = { Text(stringResource(R.string.p2po_amount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("p2po-amount"))
            if (s.operation == FiatOperation.SELL && selected != null) {
                Text(stringResource(R.string.p2po_destination_label), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.p2po_destination_hint), style = MaterialTheme.typography.bodySmall)
                var inputs by remember(selected.country) { mutableStateOf(
                    P2pPaymentDetails.fields(selected.country).zip(s.destination.split('|')).toMap()) }
                LaunchedEffect(s.destination) { if (s.destination.isEmpty()) inputs = emptyMap() }
                P2pPaymentDetails.fields(selected.country).forEach { field ->
                    OutlinedTextField(inputs[field].orEmpty(), { value ->
                        if (value.length <= 128 && !value.contains('|')) {
                            inputs = inputs + (field to value)
                            destination(P2pPaymentDetails.pack(selected.country, inputs))
                        }
                    }, enabled = !s.busy, label = { Text(stringResource(paymentFieldLabel(field))) },
                        supportingText = { if(P2pPaymentDetails.optional(selected.country)) Text(stringResource(R.string.p2po_optional)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("p2po-destination-$field"))
                }
            }
            if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            s.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("p2po-error")) }
            if (!s.busy && s.error == null && s.availability?.enabled == false) Text(stringResource(R.string.p2po_unavailable))
            PrimaryButton(stringResource(R.string.p2po_review), !s.busy && s.availability?.enabled == true && selected != null && s.amount.isNotBlank() &&
                s.account?.limit(s.operation)?.let { p2pInteger(it, true) } == true &&
                (s.operation == FiatOperation.BUY || P2pPaymentDetails.valid(s.country, s.destination))) { focus.clearFocus(); quote() }
            OutlinedButton(reload, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.p2po_refresh)) }
            s.review?.let { r ->
                Card(Modifier.fillMaxWidth().testTag("p2po-review")) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(actionLabel(r.action)), style = MaterialTheme.typography.titleLarge)
                    val q = r.quote; val o = r.prepared?.order
                    Text(stringResource(R.string.p2po_usdc, p2pDecimal(q?.amount ?: checkNotNull(o).amount)))
                    q?.let {
                        Text(stringResource(R.string.p2po_fiat_estimate, p2pDecimal(it.estimatedFiatTotal), it.fiat))
                        Text(stringResource(R.string.p2po_fee, p2pDecimal(it.fee)))
                    }
                    if (r.action in setOf(P2pAction.APPROVE, P2pAction.HANDOFF)) Text(stringResource(R.string.p2po_total_usdc, p2pDecimal(checkNotNull(r.prepared).approval)))
                    o?.let { Text(stringResource(R.string.p2po_fiat_actual, p2pDecimal(it.actualFiat), p2pFiatCode(it.currency))) }
                    Text(stringResource(R.string.p2po_gas, BigDecimal(r.gas).movePointLeft(18).stripTrailingZeros().toPlainString()))
                    r.paymentDestination?.let { details ->
                        Text(stringResource(R.string.p2po_destination_label))
                        SelectionContainer { Text(details) }
                    }
                    Text(stringResource(R.string.p2po_review_notice), style = MaterialTheme.typography.bodySmall)
                    var confirmed by remember(r) { mutableStateOf(false) }
                    if (r.action == P2pAction.PAID) Row {
                        Checkbox(confirmed, { confirmed = it }, enabled = !s.busy)
                        Text(stringResource(R.string.p2po_paid_confirmation))
                    }
                    PrimaryButton(stringResource(R.string.p2po_confirm), !s.busy && (r.action != P2pAction.PAID || confirmed)) { focus.clearFocus(); submit(confirmed) }
                    TextButton(dismiss, enabled = !s.busy) { Text(stringResource(R.string.p2po_close)) }
                } }
            }
            if (s.records.isNotEmpty()) Text(stringResource(R.string.p2po_orders), style = MaterialTheme.typography.titleLarge)
            s.records.forEach { record ->
                Card(Modifier.fillMaxWidth().testTag("p2po-order")) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.p2po_usdc, p2pDecimal(record.amount)), style = MaterialTheme.typography.titleMedium)
                    record.orderId?.let { Text(stringResource(R.string.p2po_order_id, it)) }
                    Text(stringResource(statusLabel(record)))
                    record.hash?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall) } }
                    OutlinedButton({ refresh(record.localId) }, enabled = !s.busy) { Text(stringResource(R.string.p2po_refresh)) }
                    val uncertain = record.submission in setOf(P2pSubmission.UNKNOWN, P2pSubmission.SUBMITTING)
                    if (uncertain && record.hash == null) {
                        Text(stringResource(R.string.p2po_unknown))
                        var hash by remember(record.localId) { mutableStateOf("") }
                        OutlinedTextField(hash, { if (it.length <= 66) hash = it }, label = { Text(stringResource(R.string.p2po_hash)) }, singleLine = true)
                        TextButton({ recover(record.localId, hash) }, enabled = !s.busy && hash.matches(Regex("0x[a-fA-F0-9]{64}"))) { Text(stringResource(R.string.p2po_recover)) }
                    }
                    val o = record.order
                    if (o != null && record.submission in setOf(P2pSubmission.CONFIRMED, P2pSubmission.REVERTED) && o.status in setOf("placed", "accepted", "paid")) {
                        Text(stringResource(R.string.p2po_fiat_actual, p2pDecimal(o.actualFiat), p2pFiatCode(o.currency)))
                        if (record.operation == FiatOperation.BUY && o.status == "accepted") {
                            TextButton({ instructions(record.localId) }, enabled = !s.busy) { Text(stringResource(R.string.p2po_payment_details)) }
                            if (s.payment?.first == record.localId) {
                                SelectionContainer { Text(checkNotNull(s.payment).second) }
                                TextButton({ action(record.localId, P2pAction.PAID, null) }, enabled = !s.busy) { Text(stringResource(R.string.p2po_paid)) }
                            }
                        }
                        if (record.operation == FiatOperation.SELL && o.status in setOf("placed", "accepted")) {
                            TextButton({ action(record.localId, P2pAction.APPROVE, null) }, enabled = !s.busy) { Text(stringResource(R.string.p2po_approve)) }
                            if (o.status == "accepted") TextButton({ action(record.localId, P2pAction.HANDOFF, null) }, enabled = !s.busy) { Text(stringResource(R.string.p2po_handoff)) }
                        }
                        TextButton({ action(record.localId, P2pAction.CANCEL, null) }, enabled = !s.busy) { Text(stringResource(R.string.p2po_cancel)) }
                        if (o.status == "paid") {
                            var evidence by remember(record.localId) { mutableStateOf("") }
                            OutlinedTextField(evidence, { if (it.length <= 78) evidence = it }, label = { Text(stringResource(R.string.p2po_evidence)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            TextButton({ action(record.localId, P2pAction.DISPUTE, evidence) }, enabled = !s.busy && p2pInteger(evidence, true)) { Text(stringResource(R.string.p2po_dispute)) }
                        }
                    }
                } }
            }
            val historical = s.history.filter { h -> s.records.none { it.orderId == h.id } }
            if (historical.isNotEmpty()) {
                Text(stringResource(R.string.p2po_history), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.p2po_history_notice), style = MaterialTheme.typography.bodySmall)
                historical.forEach { Text(stringResource(R.string.p2po_order_id, it.id) + " · " + p2pDecimal(it.amount) + " USDC") }
            }
        }
    }
}
internal fun actionLabel(action: P2pAction) = when (action) {
    P2pAction.PLACE -> R.string.p2po_place; P2pAction.APPROVE -> R.string.p2po_approve
    P2pAction.HANDOFF -> R.string.p2po_handoff; P2pAction.PAID -> R.string.p2po_paid
    P2pAction.CANCEL -> R.string.p2po_cancel; P2pAction.DISPUTE -> R.string.p2po_dispute
}
internal fun statusLabel(record: P2pPublicRecord): Int = when {
    record.submission in setOf(P2pSubmission.UNKNOWN, P2pSubmission.SUBMITTING) -> R.string.p2po_unknown
    record.submission == P2pSubmission.PENDING -> R.string.p2po_pending
    record.submission == P2pSubmission.REVERTED -> R.string.p2po_reverted
    record.order?.status == "completed" -> R.string.p2po_completed
    record.order?.status == "cancelled" -> R.string.p2po_cancelled
    record.order?.dispute == "open" -> R.string.p2po_disputed
    record.order?.status == "paid" -> R.string.p2po_wait_payment
    record.order?.status == "accepted" -> R.string.p2po_accepted
    else -> R.string.p2po_placed
}

internal fun paymentFieldLabel(field: String) = when(field) {
    "upi" -> R.string.p2po_field_upi; "pix" -> R.string.p2po_field_pix; "phone" -> R.string.p2po_field_phone
    "alias" -> R.string.p2po_field_alias; "rif" -> R.string.p2po_field_rif; "bank" -> R.string.p2po_field_bank
    "account" -> R.string.p2po_field_account; "name" -> R.string.p2po_field_name; "card" -> R.string.p2po_field_card
    "accountType" -> R.string.p2po_field_account_type; "cedula" -> R.string.p2po_field_cedula
    "cci" -> R.string.p2po_field_cci; "till" -> R.string.p2po_field_till; "paybill" -> R.string.p2po_field_paybill
    else -> R.string.p2po_field_paybill_account
}
