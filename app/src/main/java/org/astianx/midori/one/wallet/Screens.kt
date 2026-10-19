package org.astianx.midori.one.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.astianx.midori.one.AppFeature
import org.astianx.midori.one.AppSearchField
import org.astianx.midori.one.IntroSlide
import org.astianx.midori.one.MainHeader
import org.astianx.midori.one.MidoriPattern
import org.astianx.midori.one.OnboardingArtwork
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

    Column(modifier = Modifier.fillMaxSize()) {
        BackHandler { vm.navigate(if (s.hasEnteredApp) Screen.Home else Screen.Welcome) }
        ScreenHeader(stringResource(R.string.create_wallet)) { vm.navigate(if (s.hasEnteredApp) Screen.Home else Screen.Welcome) }

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                stringResource(R.string.seed_instructions),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.seed_warning), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .weight(1f)
        ) {
            itemsIndexed(s.seedPhrase) { index, word ->
                SeedWordCell(index + 1, word)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                stringResource(R.string.copy_clipboard),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.recovery_phrase), s.seedPhrase.joinToString(" ")))
                        vm.showToast(UiText(R.string.seed_copied))
                    }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(10.dp))
            CountryChoice(s.pendingCountry, !s.isLoading, vm::selectCountry)
            Spacer(Modifier.height(10.dp))
            PrimaryButton(stringResource(R.string.seed_saved), enabled = !s.isLoading && walletCountryValid(s.pendingCountry)) {
                vm.confirmSeedAndInitialize()
            }
        }
    }
}

// ===== IMPORT WALLET SCREEN =====

@Composable
fun ImportWalletScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        BackHandler { vm.navigate(if (s.hasEnteredApp) Screen.Home else Screen.Welcome) }
        ScreenHeader(stringResource(R.string.import_wallet)) { vm.navigate(if (s.hasEnteredApp) Screen.Home else Screen.Welcome) }

        Text(
            stringResource(R.string.import_instructions),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .weight(1f)
        ) {
            itemsIndexed(s.importWords) { index, word ->
                SeedInputCell(index + 1, word) { vm.updateImportWord(index, it) }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Text(
                if (s.importError) stringResource(R.string.import_all_words)
                else stringResource(R.string.import_hint),
                fontSize = 12.sp,
                color = if (s.importError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.paste),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.let { vm.pasteImportWords(it) }
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        CountryChoice(s.pendingCountry, !s.isLoading, vm::selectCountry, Modifier.padding(horizontal = 20.dp))

        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
            PrimaryButton(stringResource(R.string.import_wallet), enabled = !s.isLoading && walletCountryValid(s.pendingCountry)) { vm.doImport() }
        }

        Spacer(Modifier.height(24.dp))
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
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)) {
                        if (appearance.showTexture) MidoriPattern(Modifier.matchParentSize())
                        OnboardingArtwork(IntroSlide.ASSETS, Modifier.fillMaxWidth().height(210.dp))
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(stringResource(R.string.wallet_setup_title), style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.wallet_setup_body), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    PrimaryButton(stringResource(R.string.create_new_wallet)) { vm.createWallet() }
                    Spacer(Modifier.height(12.dp))
                    OutlineActionButton(stringResource(R.string.import_existing_wallet)) { vm.resetImport(); vm.navigate(Screen.Import) }
                    Spacer(Modifier.height(20.dp))
                    MainnetBadge()
                }
            } else {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primaryContainer)) {
                    if (appearance.showTexture) MidoriPattern(Modifier.matchParentSize())
                    Column(Modifier.padding(if (appearance.compact) 14.dp else 22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.digital_dollars), style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("${stringResource(R.string.network_base)} · ${stringResource(R.string.mainnet)}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f))
                            }
                            IconButton(onClick = { saveAppearance(appearance.copy(hideBalances = balancesVisible)) }) {
                                Icon(if (balancesVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    stringResource(if (balancesVisible) R.string.hide_balances else R.string.show_balances),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            IconButton(onClick = vm::fetchBalance, enabled = !s.refreshingBalances) {
                                if (s.refreshingBalances) CircularProgressIndicator(Modifier.size(20.dp)
                                    .then(Modifier.semantics { contentDescription = refreshingLabel }),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer, strokeWidth = 2.dp)
                                else Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh_balances), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(if (balancesVisible) vm.formattedDollarBalance else "••••••",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.testTag("dollar-account-balance"))
                        Text(stringResource(R.string.dollars_nominal_balance), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        TextButton(onClick = { showBreakdown = !showBreakdown }) {
                            Text(stringResource(if (showBreakdown) R.string.dollars_hide_breakdown else R.string.dollars_breakdown))
                        }
                        if (showBreakdown) {
                            StablecoinBalance(Asset.USDC, vm.formattedUsdcBalance, balancesVisible) {
                                vm.openReceive(Asset.USDC)
                            }
                            StablecoinBalance(Asset.USDT, vm.formattedUsdtBalance, balancesVisible) {
                                vm.openReceive(Asset.USDT)
                            }
                        }
                        Text(stringResource(R.string.dollars_balance_explanation), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .8f))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WalletAction(Icons.Default.ArrowUpward, R.string.send, {
                        vm.resetSend(); vm.updateSendAsset(appearance.preferredToken); vm.navigate(Screen.Send)
                    }, Modifier.weight(1f))
                    WalletAction(Icons.Default.ArrowDownward, R.string.wallet_deposit, {
                        vm.openFunding(org.astianx.midori.one.wallet.fiat.FiatOperation.BUY)
                    }, Modifier.weight(1f))
                    WalletAction(Icons.Outlined.Payments, R.string.wallet_withdraw, {
                        vm.openFunding(org.astianx.midori.one.wallet.fiat.FiatOperation.SELL)
                    }, Modifier.weight(1f))
                }
                SectionLabel(R.string.dollars_crypto_assets)
                val assets = if (appearance.bitcoinFirst) listOf(Asset.BTC, Asset.ETH) else listOf(Asset.ETH, Asset.BTC)
                assets.forEach { asset ->
                    if (asset == Asset.BTC) AssetRow("₿", Color(0xFFBD7016), stringResource(R.string.network_bitcoin), stringResource(R.string.mainnet),
                        if (balancesVisible) vm.formattedBtcBalance else "•••• BTC")
                    else AssetRow("Ξ", Color(0xFF626D91), stringResource(R.string.eth_gas), stringResource(R.string.network_base),
                        if (balancesVisible) vm.formattedEthBalance else "•••• ETH")
                }
                Text(stringResource(R.string.gas_explanation), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(22.dp))
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

    Column(modifier = Modifier.fillMaxSize()) {
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

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(stringResource(R.string.recipient_address), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    enabled = s.sendAsset.isAvailable && !s.isLoading,
                    value = s.sendAddress,
                    onValueChange = { vm.updateSendAddress(it) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    modifier = Modifier
                        .testTag("send-recipient")
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .padding(13.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (s.sendAddress.isEmpty()) {
                                Text(stringResource(R.string.address_hint), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.amount), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    enabled = s.sendAsset.isAvailable && !s.isLoading,
                    value = s.sendAmount,
                    onValueChange = { vm.updateSendAmount(it) },
                    textStyle = MaterialTheme.typography.headlineLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("send-amount"),
                    decorationBox = { innerTextField ->
                        Box {
                            if (s.sendAmount.isEmpty()) {
                                Text(stringResource(R.string.amount_hint), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            innerTextField()
                        }
                    }
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(vm.sendTokenLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    if (s.sendAsset.isToken) {
                        Text(
                            stringResource(R.string.max_amount),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { vm.setSendMax() }
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.fee), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(vm.sendFee.asString(), modifier = Modifier.weight(3f), textAlign = TextAlign.End, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
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
                    TxCard(tx, vm, context)
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
                Text(review.address, fontFamily = FontFamily.Monospace, modifier = Modifier.testTag("review-recipient"))
                Text(network)
                Text(fee)
                Text(estimate, style = MaterialTheme.typography.bodySmall)
                if (expired) Text(expiration, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = confirm, enabled = !busy && !expired) {
            Text(confirmLabel)
        } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text(editLabel) } })
}

@Composable
fun TxCard(tx: PendingTx, vm: WalletViewModel, context: Context) {
    val cardColor = when (tx.status) {
        TxStatus.PENDING -> MaterialTheme.colorScheme.tertiary
        TxStatus.BROADCAST -> MaterialTheme.colorScheme.secondary
        TxStatus.UNKNOWN -> MaterialTheme.colorScheme.tertiary
        TxStatus.FAILED_BEFORE_BROADCAST -> MaterialTheme.colorScheme.error
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cardColor.copy(alpha = 0.06f))
            .border(1.dp, cardColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tx.status == TxStatus.PENDING) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = cardColor)
            } else {
                Icon(
                    if (tx.hash != null) Icons.Default.Info else Icons.Default.Error,
                    contentDescription = null,
                    tint = cardColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(tx.statusLabel.asString(), modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cardColor)
            Spacer(Modifier.weight(1f))
            Text(tx.asset.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(4.dp))
        Row {
            Text(tx.amount, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.to_recipient), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(
                "${tx.toAddress.take(8)}...${tx.toAddress.takeLast(4)}",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        tx.error?.let { error ->
            Spacer(Modifier.height(4.dp))
            Text(error.asString(), fontSize = 12.sp, color = cardColor)
        }

        tx.hash?.let { hash ->
            Spacer(Modifier.height(4.dp))
            Text(hash, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.transaction_hash), hash))
                        vm.showToast(UiText(R.string.hash_copied), success = true)
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.copy), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
                tx.explorerUrl?.let { url ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.explorer), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ===== RECEIVE SCREEN =====

@Composable
fun ReceiveScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current

    BackHandler { vm.navigate(s.receiveReturnScreen) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        ScreenHeader(stringResource(R.string.receive)) { vm.navigate(s.receiveReturnScreen) }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {
        ReceiveAccountSelector(s.receiveAsset, vm::updateReceiveAsset)
        if (s.receiveAsset in DigitalDollarAccount.assets) {
            Text(stringResource(R.string.dollars_receive_title), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp))
            Text(stringResource(R.string.usdt_bridge_description), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp))
        }
        Text(if (s.receiveAsset in DigitalDollarAccount.assets) stringResource(R.string.dollars_receive_tokens)
            else vm.receiveLabel.asString(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        val request = vm.receiveQrContent
        if (request != null) {
            QrCodeImage(request)
            Spacer(Modifier.height(12.dp))
            Text(
                if (s.receiveAsset in DigitalDollarAccount.assets) stringResource(R.string.dollars_receive_warning)
                else if (s.receiveAsset == Asset.BTC)
                    stringResource(R.string.receive_btc_warning)
                else stringResource(R.string.receive_base_warning, s.receiveAsset.name),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(vm.receiveAddress, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.copy_address), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(vm.receiveLabel.resolve(context), vm.receiveAddress))
                    vm.showToast(UiText(R.string.address_copied, listOf(s.receiveAsset.network.label)))
                }.padding(12.dp))
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
    androidx.compose.foundation.layout.FlowRow(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NetworkPill(stringResource(R.string.digital_dollars), "$", selected in DigitalDollarAccount.assets) { select(Asset.USDC) }
        NetworkPill("ETH", "Ξ", selected == Asset.ETH) { select(Asset.ETH) }
        NetworkPill("BTC", "₿", selected == Asset.BTC) { select(Asset.BTC) }
    }
}

// ===== SIGN MESSAGE SCREEN =====

@Composable
fun SignMessageScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current

    BackHandler { vm.navigate(Screen.Home) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.sign_message)) { vm.navigate(Screen.Home) }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                stringResource(R.string.sign_description),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp)
            ) {
                NetworkPill(stringResource(R.string.network_base), "\u039E", s.signNetwork == Network.BASE) { vm.updateSignNetwork(Network.BASE) }
                NetworkPill(stringResource(R.string.network_bitcoin), "\u20BF", s.signNetwork == Network.BTC) { vm.updateSignNetwork(Network.BTC) }
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(stringResource(R.string.message_to_sign), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = s.signMessage,
                    onValueChange = { vm.updateSignMessage(it) },
                    textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (s.signMessage.isEmpty()) {
                                Text(stringResource(R.string.sign_message_hint), fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            innerTextField()
                        }
                    }
                )
            }

            s.signResult?.let { signature ->
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.signature), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.copy),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.signature), signature))
                                vm.showToast(UiText(R.string.signature_copied), success = true)
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(signature, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(Modifier.height(4.dp))

                val verifyColor = when (s.verifyResult) {
                    null -> MaterialTheme.colorScheme.primary
                    true -> MaterialTheme.colorScheme.primary
                    false -> MaterialTheme.colorScheme.error
                }
                val verifyText = when (s.verifyResult) {
                    null -> stringResource(R.string.verify_signature)
                    true -> stringResource(R.string.verified)
                    false -> stringResource(R.string.invalid_signature)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(verifyColor.copy(alpha = 0.08f))
                        .border(1.dp, verifyColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .clickable { vm.doVerify() }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Icon(when (s.verifyResult) {
                        true -> Icons.Default.CheckCircle
                        false -> Icons.Default.Error
                        null -> Icons.Default.FactCheck
                    }, contentDescription = null, tint = verifyColor, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(verifyText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = verifyColor)
                }
            }
        }

        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
            PrimaryButton(stringResource(R.string.sign_message), enabled = s.signMessage.isNotBlank() && !s.isLoading) { vm.doSign() }
        }
    }
}
