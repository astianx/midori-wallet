// Copyright 2026 Midori. Licensed under the Apache License, Version 2.0.
package org.astianx.midori.one.wallet.p2p

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.astianx.midori.one.BuildConfig
import org.astianx.midori.one.PrimaryButton
import org.astianx.midori.one.R
import org.astianx.midori.one.ScreenHeader
import org.astianx.midori.one.wallet.QrCodeImage
import org.astianx.midori.one.wallet.WalletViewModel
import java.math.BigDecimal

internal fun isP2pVerificationReturn(value: String?): Boolean = value == "midori://p2p/verification"

@Composable
internal fun P2pKycScreen(wallet: WalletViewModel, country: String, back: () -> Unit) {
    val owner = wallet.state.collectAsState().value.ethAddress
    val context = LocalContext.current
    val vm: P2pKycViewModel = viewModel(key = "p2p-kyc:$owner", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val flow = runCatching {
                val gateway = HttpP2pKycGateway(BuildConfig.FIAT_BACKEND_URL)
                P2pKycInteractor(owner, gateway, KeystoreP2pKycStore(context), wallet.p2pWalletPort(),
                    { record, expires -> wallet.signP2pVerification(record, expires, gateway.origin) })
            }.getOrNull()
            return P2pKycViewModel(owner, country, flow) as T
        }
    })
    val s by vm.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.refresh() } }
    LaunchedEffect(s.launchUrl) { s.launchUrl?.let { url ->
        try { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url)); vm.launched() }
        catch (_: Exception) { vm.launchFailed() }
    } }
    LaunchedEffect(s.stage) { if (s.stage == "CONFIRMED") wallet.fetchBalance() }
    DisposableEffect(vm) { onDispose { vm.dismiss() } }
    P2pKycContent(s, back, vm::start, vm::reopen, vm::refresh, vm::prepare, vm::submit, vm::dismiss, vm::recover,
        vm::otherDevice, vm::hideShare, vm::startLiveness)
}

@Composable
internal fun P2pKycContent(s: P2pKycState, back: () -> Unit, start: () -> Unit, reopen: () -> Unit,
    refresh: () -> Unit, prepare: () -> Unit, submit: () -> Unit, dismiss: () -> Unit, recover: (String) -> Unit,
    otherDevice: () -> Unit = {}, hideShare: () -> Unit = {}, startLiveness: () -> Unit = {}) {
    BackHandler { if (s.review != null) dismiss() else back() }
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(stringResource(R.string.p2pk_title), back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SelectionContainer { Text(stringResource(R.string.p2pk_wallet, s.owner), style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.p2pk_market, java.util.Locale("", s.country).getDisplayCountry(locale)))
            Text(stringResource(R.string.p2pk_notice), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.p2pk_capture), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(kycStageLabel(s.stage)), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("p2pk-stage"))
            if (s.stage !in setOf("CONFIRMED", "ALREADY_VERIFIED")) s.expiresAt?.let { Text(stringResource(R.string.p2pk_expiry,
                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, locale).format(java.util.Date(it * 1000)))) }
            s.hash?.let { SelectionContainer { Text(stringResource(R.string.p2pk_hash, it), style = MaterialTheme.typography.bodySmall) } }
            s.account?.let { Text(stringResource(R.string.p2po_account_limit, p2pDecimal(it.buyLimit))) }
            if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            s.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("p2pk-error")) }
            if (s.stage in setOf("IDLE", "EXPIRED", "CANCELLED", "REVERTED", "INTERRUPTED")) {
                var consent by remember(s.stage) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(consent, enabled = !s.busy, role = Role.Checkbox, onValueChange = { consent = it }),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(consent, onCheckedChange = null, enabled = !s.busy)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.p2pk_consent), modifier = Modifier.weight(1f))
                }
                PrimaryButton(stringResource(R.string.p2pk_start), consent && !s.busy && s.capabilities?.enabled == true &&
                    s.country in s.capabilities.countries, start)
                if (s.capabilities?.enabled == false || s.capabilities?.countries?.contains(s.country) == false)
                    Text(stringResource(R.string.p2pk_unavailable))
                if (s.livenessDone) Text(stringResource(R.string.p2pk_liveness_active), modifier = Modifier.testTag("p2pk-liveness-active"))
                else if (s.capabilities?.livenessEnabled == true) {
                    OutlinedButton(startLiveness, enabled = consent && !s.busy, modifier = Modifier.fillMaxWidth().testTag("p2pk-start-liveness")) {
                        Text(stringResource(R.string.p2pk_start_liveness)) }
                    Text(stringResource(R.string.p2pk_liveness_note), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (s.stage == "WAITING") {
                OutlinedButton(reopen, enabled = !s.busy) { Text(stringResource(R.string.p2pk_reopen)) }
                if (s.shareUrl == null) OutlinedButton(otherDevice, enabled = !s.busy, modifier = Modifier.testTag("p2pk-other-device")) {
                    Text(stringResource(R.string.p2pk_other_device)) }
                s.shareUrl?.let { url ->
                    Surface(Modifier.fillMaxWidth().testTag("p2pk-share"), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.p2pk_other_device_hint))
                            QrCodeImage(url, 240)
                            OutlinedButton(hideShare) { Text(stringResource(R.string.p2pk_hide_code)) }
                        }
                    }
                }
            }
            if (s.stage == "READY" && s.review == null) PrimaryButton(stringResource(R.string.p2pk_prepare), !s.busy, prepare)
            s.review?.let { r ->
                OutlinedCard(Modifier.fillMaxWidth().testTag("p2pk-review"),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.p2pk_review_notice))
                    Text(stringResource(R.string.p2po_gas, BigDecimal(r.gas).movePointLeft(18).stripTrailingZeros().toPlainString()))
                    PrimaryButton(stringResource(R.string.p2pk_submit), !s.busy, submit)
                    OutlinedButton(dismiss, enabled = !s.busy) { Text(stringResource(R.string.p2pk_dismiss)) }
                } }
            }
            if (s.stage in setOf("SUBMITTING", "UNKNOWN") && s.hash == null) {
                var hash by remember { mutableStateOf("") }
                OutlinedTextField(hash, { if (it.length <= 66) hash = it }, label = { Text(stringResource(R.string.p2po_hash)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("p2pk-recovery-hash"))
                OutlinedButton({ recover(hash) }, enabled = !s.busy && hash.matches(Regex("0x[a-fA-F0-9]{64}"))) {
                    Text(stringResource(R.string.p2po_recover)) }
            }
            OutlinedButton(refresh, enabled = !s.busy, modifier = Modifier.testTag("p2pk-refresh")) { Text(stringResource(R.string.p2po_refresh)) }
            if (s.stage in setOf("CONFIRMED", "ALREADY_VERIFIED")) PrimaryButton(stringResource(R.string.p2pk_done), !s.busy, back)
        }
    }
}

internal fun kycStageLabel(stage: String) = when (stage) {
    "WAITING", "STARTING" -> R.string.p2pk_waiting
    "INTERRUPTED" -> R.string.p2pk_interrupted
    "READY" -> R.string.p2pk_ready
    "EXPIRED" -> R.string.p2pk_expired
    "REJECTED" -> R.string.p2pk_rejected
    "DUPLICATE_IDENTITY" -> R.string.p2pk_duplicate
    "CANCELLED" -> R.string.p2pk_cancelled
    "EXCHANGE_UNKNOWN" -> R.string.p2pk_exchange_unknown
    "PENDING", "SUBMITTING" -> R.string.p2pk_pending
    "UNKNOWN" -> R.string.p2po_unknown
    "CONFIRMED" -> R.string.p2pk_confirmed
    "ALREADY_VERIFIED" -> R.string.p2pk_already_verified
    "REVERTED" -> R.string.p2pk_reverted
    else -> R.string.p2pk_idle
}
