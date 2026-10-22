package org.astianx.midori.one.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.astianx.midori.one.AppFeature
import org.astianx.midori.one.AppSearchField
import org.astianx.midori.one.MainHeader
import org.astianx.midori.one.OutlineActionButton
import org.astianx.midori.one.PrimaryButton
import org.astianx.midori.one.R
import org.astianx.midori.one.Screen
import org.astianx.midori.one.ScreenHeader
import org.astianx.midori.one.SectionLabel
import org.astianx.midori.one.ShortcutRow
import org.astianx.midori.one.UiText

@Composable
fun RestoreErrorScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    BackHandler { vm.enterApp() }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.restore_title), fontSize = 24.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.restore_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        s.restoreError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it.asString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(28.dp))
        PrimaryButton(stringResource(R.string.retry_open_wallet), enabled = !s.isLoading) { vm.restoreWallet() }
        TextButton(onClick = vm::enterApp) { Text(stringResource(R.string.go_to_chats)) }
        Spacer(Modifier.height(12.dp))
        OutlineActionButton(stringResource(R.string.import_recovery_phrase)) {
            vm.resetImport()
            vm.navigate(Screen.Import)
        }
    }
}

// ===== CREATE WALLET SCREEN =====

@Composable
fun CreateWalletScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    BackHandler { vm.navigate(if (s.hasEnteredApp) Screen.Home else Screen.Welcome) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.create_wallet)) { vm.navigate(if (s.hasEnteredApp) Screen.Home else Screen.Welcome) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (LocalDensity.current.fontScale >= 1.2f) 2 else 3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            modifier = Modifier.weight(1f)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.seed_instructions), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.seed_warning), style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                }
            }
            itemsIndexed(s.seedPhrase) { index, word -> SeedWordCell(index + 1, word) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.recovery_phrase), s.seedPhrase.joinToString(" ")))
                        vm.showToast(UiText(R.string.seed_copied))
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.copy_clipboard)) }
                    CountryChoice(s.pendingCountry, !s.isLoading, vm::selectCountry)
                    PrimaryButton(stringResource(R.string.seed_saved), enabled = !s.isLoading && walletCountryValid(s.pendingCountry)) {
                        vm.confirmSeedAndInitialize()
                    }
                }
            }
        }
    }
}

// ===== IMPORT WALLET SCREEN =====

@Composable
fun ImportWalletScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    BackHandler { vm.navigate(if (s.hasEnteredApp) Screen.Home else Screen.Welcome) }
    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(stringResource(R.string.import_wallet)) { vm.navigate(if (s.hasEnteredApp) Screen.Home else Screen.Welcome) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (LocalDensity.current.fontScale >= 1.2f) 2 else 3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            modifier = Modifier.weight(1f)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.import_instructions), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
            }
            itemsIndexed(s.importWords) { index, word -> SeedInputCell(index + 1, word) { vm.updateImportWord(index, it) } }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (s.importError) stringResource(R.string.import_all_words) else stringResource(R.string.import_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (s.importError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.let(vm::pasteImportWords)
                        }) { Text(stringResource(R.string.paste)) }
                    }
                    CountryChoice(s.pendingCountry, !s.isLoading, vm::selectCountry)
                    PrimaryButton(stringResource(R.string.import_wallet), enabled = !s.isLoading && walletCountryValid(s.pendingCountry)) { vm.doImport() }
                }
            }
        }
    }
}

// ===== MAIN DESTINATIONS =====

@Composable
fun MarketsScreen(vm: WalletViewModel) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        MainHeader(stringResource(R.string.markets), onSearch = { searching = !searching; query = "" },
            onScan = { vm.openFeature(AppFeature.SCANNER) }, onProfile = { vm.navigate(Screen.Messaging) })
        if (searching) AppSearchField(query, { query = it }, R.string.search_assets)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.market_status), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.market_description), fontSize = 14.sp, lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionLabel(R.string.assets)
            var matches = 0
            Asset.entries.forEach { asset ->
                val description = stringResource(asset.descriptionResource())
                if (query.isBlank() || "${asset.name} $description".contains(query.trim(), ignoreCase = true)) {
                    matches++
                    ShortcutRow(Icons.Outlined.Toll, asset.name, description, { vm.openMarketAsset(asset) },
                        stringResource(R.string.no_quote))
                }
            }
            if (matches == 0) Text(stringResource(R.string.no_results), modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ShortcutRow(Icons.Outlined.NotificationsNone, stringResource(R.string.price_alerts),
                stringResource(R.string.in_development), { vm.openFeature(AppFeature.ALERTS) })
        }
    }
}

@Composable
fun MarketAssetScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    val asset = s.marketAsset
    BackHandler { vm.navigate(Screen.Markets) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(asset.name) { vm.navigate(Screen.Markets) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text(stringResource(asset.descriptionResource()), fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(16.dp))
            Text("${asset.network.label} · ${stringResource(R.string.mainnet)}", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.market_description), lineHeight = 23.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            asset.contract?.let { contract ->
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.asset_details), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                SelectionContainer { Text(contract, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = {
                val url = asset.contract?.let { "${asset.network.explorer}/token/$it" } ?: asset.network.explorer
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }) { Text(stringResource(R.string.explorer)) }
            TextButton(onClick = { vm.navigate(Screen.Home) }) { Text(stringResource(R.string.open_wallet)) }
        }
    }
}

@Composable
fun HomeScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    val appearance = LocalWalletAppearance.current
    val saveAppearance = LocalWalletAppearanceChange.current
    val balancesVisible = !appearance.hideBalances
    val refreshingLabel = stringResource(R.string.wallet_refreshing)
    var showBreakdown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(s.isWdkInitialized) {
        if (s.isWdkInitialized) while (true) { vm.fetchBalance(); delay(30_000) }
    }
    Column(Modifier.fillMaxSize()) {
        MainHeader(stringResource(R.string.wallet), onScan = { vm.openFeature(AppFeature.SCANNER) },
            onProfile = { vm.navigate(Screen.More) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (!s.isWdkInitialized) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp)) {
                    Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null,
                        modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.wallet_setup_title), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.wallet_setup_body), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(32.dp))
                    PrimaryButton(stringResource(R.string.create_new_wallet)) { vm.createWallet() }
                    Spacer(Modifier.height(12.dp))
                    OutlineActionButton(stringResource(R.string.import_existing_wallet)) { vm.resetImport(); vm.navigate(Screen.Import) }
                    Spacer(Modifier.height(24.dp))
                    Text("${stringResource(R.string.network_base)} · ${stringResource(R.string.network_bitcoin)} · ${stringResource(R.string.mainnet)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (appearance.compact) 8.dp else 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.digital_dollars), style = MaterialTheme.typography.titleMedium)
                            Text("${stringResource(R.string.network_base)} · ${stringResource(R.string.mainnet)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { saveAppearance(appearance.copy(hideBalances = balancesVisible)) }) {
                            Icon(if (balancesVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                stringResource(if (balancesVisible) R.string.hide_balances else R.string.show_balances),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = vm::fetchBalance, enabled = !s.refreshingBalances) {
                            if (s.refreshingBalances) CircularProgressIndicator(Modifier.size(20.dp)
                                .semantics { contentDescription = refreshingLabel }, strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh_balances),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(if (appearance.compact) 12.dp else 20.dp))
                    Text(if (balancesVisible) vm.formattedDollarBalance else "••••••",
                        style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("dollar-account-balance"))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.dollars_nominal_balance), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { showBreakdown = !showBreakdown },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                        Text(stringResource(if (showBreakdown) R.string.dollars_hide_breakdown else R.string.dollars_breakdown))
                        Spacer(Modifier.width(4.dp))
                        Icon(if (showBreakdown) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    if (showBreakdown) {
                        StablecoinBalance(Asset.USDC, vm.formattedUsdcBalance, balancesVisible) { vm.openReceive(Asset.USDC) }
                        StablecoinBalance(Asset.USDT, vm.formattedUsdtBalance, balancesVisible) { vm.openReceive(Asset.USDT) }
                        Text(stringResource(R.string.dollars_balance_explanation), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WalletAction(Icons.Default.ArrowUpward, R.string.send, {
                        vm.resetSend(); vm.updateSendAsset(appearance.preferredToken); vm.navigate(Screen.Send)
                    }, Modifier.weight(1f), primary = true)
                    WalletAction(Icons.Default.ArrowDownward, R.string.wallet_deposit, {
                        vm.openFunding(org.astianx.midori.one.wallet.fiat.FiatOperation.BUY)
                    }, Modifier.weight(1f))
                    WalletAction(Icons.Outlined.Payments, R.string.wallet_withdraw, {
                        vm.openFunding(org.astianx.midori.one.wallet.fiat.FiatOperation.SELL)
                    }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                SectionLabel(R.string.dollars_crypto_assets)
                val assets = if (appearance.bitcoinFirst) listOf(Asset.BTC, Asset.ETH) else listOf(Asset.ETH, Asset.BTC)
                assets.forEach { asset ->
                    if (asset == Asset.BTC) AssetRow("₿", Color(0xFFB36D19), stringResource(R.string.network_bitcoin), stringResource(R.string.mainnet),
                        if (balancesVisible) vm.formattedBtcBalance else "•••• BTC") { vm.openReceive(Asset.BTC) }
                    else AssetRow("Ξ", Color(0xFF5C6787), stringResource(R.string.eth_gas), stringResource(R.string.network_base),
                        if (balancesVisible) vm.formattedEthBalance else "•••• ETH") { vm.openReceive(Asset.ETH) }
                }
                Text(stringResource(R.string.gas_explanation), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                SectionLabel(R.string.wallet_activity_title)
                if (s.pendingTransactions.isEmpty()) {
                    Text(stringResource(R.string.wallet_activity_empty), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                } else {
                    s.pendingTransactions.reversed().forEach { tx -> TxCard(tx, vm, context, balancesVisible) }
                }
                Text(stringResource(R.string.wallet_activity_session), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                Spacer(Modifier.height(8.dp))
                ShortcutRow(Icons.Outlined.SwapHoriz, stringResource(R.string.dollars_invest),
                    stringResource(R.string.dollars_invest_pending), { vm.openFeature(AppFeature.TRADE) })
                if (appearance.showHelp) ShortcutRow(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.help_center),
                    stringResource(R.string.help_shortcut_body), { vm.navigate(Screen.Help) })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SendScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val hideBalances = LocalWalletAppearance.current.hideBalances

    BackHandler { vm.navigate(Screen.Home) }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(stringResource(R.string.send)) {
            vm.navigate(Screen.Home)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                stringResource(R.string.send_explanation),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            AssetSelector(s.sendAsset, vm::updateSendAsset)
            if (s.sendAsset in DigitalDollarAccount.assets) {
                Text(stringResource(R.string.dollars_destination_asset), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                Text(stringResource(R.string.dollars_send_balance, if (hideBalances) "••••" else vm.formattedDollarBalance,
                    if (hideBalances) "•••• ${s.sendAsset.name}" else if (s.sendAsset == Asset.USDC) vm.formattedUsdcBalance else vm.formattedUsdtBalance),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            s.sendAsset.unavailableReason?.let { reason ->
                Text(reason.asString(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }

            Spacer(Modifier.height(16.dp))

            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                OutlinedTextField(
                    enabled = s.sendAsset.isAvailable && !s.isLoading,
                    value = s.sendAddress,
                    onValueChange = vm::updateSendAddress,
                    label = { Text(stringResource(R.string.recipient_address)) },
                    placeholder = { Text(stringResource(R.string.address_hint)) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(enabled = s.sendAsset.isAvailable && !s.isLoading, onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.let(vm::updateSendAddress)
                        }) { Icon(Icons.Outlined.ContentPaste, stringResource(R.string.paste)) }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("send-recipient")
                )
                OutlinedTextField(
                    enabled = s.sendAsset.isAvailable && !s.isLoading,
                    value = s.sendAmount,
                    onValueChange = vm::updateSendAmount,
                    label = { Text(stringResource(R.string.amount)) },
                    placeholder = { Text(stringResource(R.string.amount_hint)) },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    suffix = { Text(vm.sendTokenLabel, style = MaterialTheme.typography.labelLarge) },
                    trailingIcon = if (s.sendAsset.isToken) {
                        { TextButton(onClick = vm::setSendMax, enabled = !s.isLoading) {
                            Text(stringResource(R.string.max_amount))
                        } }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("send-amount")
                )
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.fee), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(vm.sendFee.asString(), modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                Divider(color = MaterialTheme.colorScheme.outline)
                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.network), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(vm.sendNetworkLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                PrimaryButton(stringResource(R.string.send_review_action), enabled = s.sendAsset.isAvailable && !s.isLoading) {
                    focus.clearFocus()
                    vm.prepareSend()
                }
            }

            if (s.pendingTransactions.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.transactions),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(8.dp))
                s.pendingTransactions.reversed().forEach { tx ->
                    TxCard(tx, vm, context, balancesVisible = !hideBalances)
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
    s.sendReview?.let { review -> SendReviewDialog(review, s.isLoading, vm::dismissSendReview, vm::doSend) }
}

@Composable
private fun SendReviewDialog(review: SendReview, busy: Boolean, dismiss: () -> Unit, confirm: () -> Unit) {
    var expired by androidx.compose.runtime.remember(review) { mutableStateOf(System.currentTimeMillis() >= review.expiresAt) }
    val title = stringResource(R.string.send_review_title)
    val network = "${review.asset.network.label} · ${stringResource(R.string.mainnet)}"
    val fee = stringResource(R.string.send_review_fee, review.feeDisplay)
    val estimate = stringResource(R.string.send_review_estimate)
    val expiration = stringResource(R.string.send_review_expired)
    val confirmLabel = stringResource(R.string.send_confirm_action)
    val editLabel = stringResource(R.string.send_edit_action)
    LaunchedEffect(review) {
        delay((review.expiresAt - System.currentTimeMillis()).coerceAtLeast(0))
        expired = true
    }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${review.amount} ${review.asset.name}", style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.recipient_address), style = MaterialTheme.typography.labelMedium)
                SelectionContainer { Text(review.address, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("review-recipient")) }
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(network)
                Text(fee)
                if (review.feeAsset != null) Text(stringResource(R.string.send_review_gasless),
                    style = MaterialTheme.typography.bodySmall)
                Text(estimate, style = MaterialTheme.typography.bodySmall)
                if (expired) Text(expiration, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = confirm, enabled = !busy && !expired) {
            Text(confirmLabel)
        } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text(editLabel) } })
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TxCard(tx: PendingTx, vm: WalletViewModel, context: Context, balancesVisible: Boolean = true) {
    val statusColor = if (tx.status == TxStatus.FAILED_BEFORE_BROADCAST || tx.status == TxStatus.FAILED_ON_CHAIN)
        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowUpward, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(if (balancesVisible) "${tx.amount} ${tx.asset.name}" else "•••• ${tx.asset.name}",
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.weight(1f))
            if (tx.status == TxStatus.PENDING) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(tx.statusLabel.asString(), style = MaterialTheme.typography.bodyMedium, color = statusColor)
        Spacer(Modifier.height(4.dp))
        Text("${stringResource(R.string.to_recipient)} ${tx.toAddress.take(8)}…${tx.toAddress.takeLast(6)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        tx.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(error.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        tx.hash?.let { hash ->
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(hash, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.transaction_hash), hash))
                    vm.showToast(UiText(R.string.hash_copied), success = true)
                }) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.copy))
                }
                tx.explorerUrl?.let { url ->
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.explorer))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// ===== RECEIVE SCREEN =====

@Composable
fun ReceiveScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    BackHandler { vm.navigate(s.receiveReturnScreen) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.receive)) { vm.navigate(s.receiveReturnScreen) }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {
            ReceiveAccountSelector(s.receiveAsset, vm::updateReceiveAsset)
            Text(if (s.receiveAsset in DigitalDollarAccount.assets) stringResource(R.string.dollars_receive_title)
                else vm.receiveLabel.asString(), style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(6.dp))
            Text("${s.receiveAsset.network.label} · ${stringResource(R.string.mainnet)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.receiveAsset in DigitalDollarAccount.assets) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.dollars_receive_tokens), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            val request = vm.receiveQrContent
            if (request != null) {
                QrCodeImage(request)
                Spacer(Modifier.height(24.dp))
                SelectionContainer {
                    Text(vm.receiveAddress, style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp))
                }
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    PrimaryButton(stringResource(R.string.copy_address)) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(vm.receiveLabel.resolve(context), vm.receiveAddress))
                        vm.showToast(UiText(R.string.address_copied, listOf(s.receiveAsset.network.label)))
                    }
                }
                Text(
                    if (s.receiveAsset in DigitalDollarAccount.assets) stringResource(R.string.dollars_receive_warning)
                    else if (s.receiveAsset == Asset.BTC) stringResource(R.string.receive_btc_warning)
                    else stringResource(R.string.receive_base_warning, s.receiveAsset.name),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
                if (s.receiveAsset in DigitalDollarAccount.assets) {
                    Text(stringResource(R.string.usdt_bridge_description), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                }
            } else {
                Text(s.receiveAsset.unavailableReason?.asString() ?: stringResource(R.string.address_unavailable),
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReceiveAccountSelector(selected: Asset, select: (Asset) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NetworkPill(stringResource(R.string.digital_dollars), "$", selected in DigitalDollarAccount.assets) { select(Asset.USDC) }
        NetworkPill("ETH", "Ξ", selected == Asset.ETH) { select(Asset.ETH) }
        NetworkPill("BTC", "₿", selected == Asset.BTC) { select(Asset.BTC) }
    }
}

// ===== SIGN MESSAGE SCREEN =====

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SignMessageScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    BackHandler { vm.navigate(Screen.Home) }
    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(stringResource(R.string.sign_message)) { vm.navigate(Screen.Home) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.sign_description), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NetworkPill(stringResource(R.string.network_base), "Ξ", s.signNetwork == Network.BASE) { vm.updateSignNetwork(Network.BASE) }
                NetworkPill(stringResource(R.string.network_bitcoin), "₿", s.signNetwork == Network.BTC) { vm.updateSignNetwork(Network.BTC) }
            }
            OutlinedTextField(
                value = s.signMessage,
                onValueChange = vm::updateSignMessage,
                label = { Text(stringResource(R.string.message_to_sign)) },
                placeholder = { Text(stringResource(R.string.sign_message_hint)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(), minLines = 4
            )
            PrimaryButton(stringResource(R.string.sign_message), enabled = s.signMessage.isNotBlank() && !s.isLoading) { vm.doSign() }
            s.signResult?.let { signature ->
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.signature), style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.signature), signature))
                        vm.showToast(UiText(R.string.signature_copied), success = true)
                    }) { Text(stringResource(R.string.copy)) }
                }
                SelectionContainer { Text(signature, style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedButton(onClick = vm::doVerify, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(when (s.verifyResult) {
                        true -> Icons.Default.CheckCircle
                        false -> Icons.Default.Error
                        null -> Icons.Default.FactCheck
                    }, contentDescription = null, modifier = Modifier.size(20.dp),
                        tint = if (s.verifyResult == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(when (s.verifyResult) {
                        true -> R.string.verified
                        false -> R.string.invalid_signature
                        null -> R.string.verify_signature
                    }))
                }
            }
        }
    }
}
