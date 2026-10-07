package org.astianx.midori.one

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun BrandSplashScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MidoriLogo(Modifier.width(180.dp))
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.brand_name), color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
    }
}

// ===== WELCOME SCREEN =====

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(vm: WalletViewModel, themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    val s by vm.state.collectAsState()
    val slides = IntroSlide.entries
    val pager = rememberPagerState(initialPage = s.introductionPage) { slides.size }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = s.hasEnteredApp) { vm.navigate(Screen.More) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { vm.updateIntroductionPage(it) }
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val shortWindow = maxHeight < 520.dp
        val artworkHeight = if (shortWindow) 230.dp else (maxHeight * 0.34f).coerceIn(160.dp, 320.dp)
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxSize()
                .then(if (shortWindow) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MidoriLogo(Modifier.width(38.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.app_name), fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                ThemePicker(themeMode, onThemeChange)
            }

            HorizontalPager(
                state = pager,
                userScrollEnabled = !s.isLoading,
                verticalAlignment = Alignment.Top,
                modifier = if (shortWindow) Modifier.height(460.dp) else Modifier.weight(1f)
            ) { page ->
                val slide = slides[page]
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OnboardingArtwork(slide, Modifier.fillMaxWidth().height(artworkHeight))
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(if (slide == IntroSlide.MESSAGING) R.string.in_development else slide.tag),
                            color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(slide.title), fontSize = 30.sp, lineHeight = 35.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp,
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(slide.body), fontSize = 15.sp, lineHeight = 22.sp,
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (slide == IntroSlide.MESSAGING) {
                            TextButton(onClick = { vm.navigate(Screen.Messaging) }, enabled = !s.isLoading) {
                                Text(stringResource(R.string.view_messaging), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            OnboardingControls(slides, pager.currentPage, enabled = !pager.isScrollInProgress && !s.isLoading) { page ->
                scope.launch { pager.animateScrollToPage(page) }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = vm::enterApp,
                    enabled = !s.isLoading, shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                ) {
                    Text(stringResource(if (s.hasEnteredApp) R.string.go_to_chats else R.string.get_started),
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = {
                        if (s.hasEnteredApp) vm.navigate(Screen.Home)
                        else { vm.resetImport(); vm.navigate(Screen.Import) }
                    },
                    enabled = !s.isLoading, shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                ) {
                    Text(stringResource(if (s.hasEnteredApp) R.string.open_wallet else R.string.import_existing_wallet),
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.onboarding_version, BuildConfig.VERSION_NAME),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MessagingScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    val returnScreen = s.messagingReturnScreen
    BackHandler { vm.navigate(returnScreen) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.messaging)) { vm.navigate(returnScreen) }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OnboardingArtwork(IntroSlide.MESSAGING, Modifier.fillMaxWidth().height(180.dp))
            Text(stringResource(R.string.in_development), fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.messaging_title), fontSize = 28.sp, lineHeight = 34.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.messaging_description), textAlign = TextAlign.Center,
                fontSize = 15.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.messaging_status), fontSize = 13.sp, lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp))
            Spacer(Modifier.height(16.dp))
            MessagingFeature(Icons.Outlined.People, R.string.messaging_contacts_title, R.string.messaging_contacts_body)
            MessagingFeature(Icons.AutoMirrored.Outlined.Chat, R.string.messaging_chat_title, R.string.messaging_chat_body)
            MessagingFeature(Icons.Outlined.AccountBalanceWallet, R.string.messaging_payments_title, R.string.messaging_payments_body)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://xmpp.org/about/"))) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(24.dp)
            ) {
                Text(stringResource(R.string.about_xmpp))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            TextButton(onClick = { vm.navigate(if (s.hasEnteredApp) Screen.Chats else Screen.Welcome) }) {
                Text(stringResource(if (s.hasEnteredApp) R.string.go_to_chats else R.string.back_to_intro))
            }
        }

    }
}

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
            PrimaryButton(stringResource(R.string.seed_saved), enabled = !s.isLoading) {
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

        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
            PrimaryButton(stringResource(R.string.import_wallet)) { vm.doImport() }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ===== MAIN DESTINATIONS =====

@Composable
fun ChatsScreen(vm: WalletViewModel) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        MainHeader(stringResource(R.string.app_name), onSearch = { searching = !searching; query = "" },
            onScan = { vm.openFeature(AppFeature.SCANNER) }, onProfile = { vm.openFeature(AppFeature.PROFILE) })
        if (searching) AppSearchField(query, { query = it }, R.string.search_chats)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.xmpp_not_connected), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().clickable { vm.navigate(Screen.Messaging) }
                    .padding(horizontal = 20.dp, vertical = 16.dp))
            if (query.isBlank()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, null, modifier = Modifier.size(46.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.chats_empty_title), fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.chats_empty_body), fontSize = 14.sp, lineHeight = 21.sp,
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(18.dp))
                    FilledTonalButton(onClick = { vm.openFeature(AppFeature.CONTACTS) }) {
                        Icon(Icons.Outlined.AddComment, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.new_chat))
                    }
                }
            }
            SectionLabel(R.string.quick_access)
            val rows = listOf(
                Triple(R.string.wallet, R.string.wallet_shortcut_body, Screen.Home),
                Triple(R.string.messaging_title, R.string.xmpp_shortcut_body, Screen.Messaging),
                Triple(R.string.help_center, R.string.help_shortcut_body, Screen.Help)
            )
            var matches = 0
            rows.forEach { (title, description, screen) ->
                val label = stringResource(title)
                val subtitle = stringResource(description)
                if (query.isBlank() || "$label $subtitle".contains(query.trim(), ignoreCase = true)) {
                    matches++
                    ShortcutRow(when (screen) {
                        Screen.Home -> Icons.Outlined.AccountBalanceWallet
                        Screen.Help -> Icons.AutoMirrored.Outlined.HelpOutline
                        else -> Icons.AutoMirrored.Outlined.Chat
                    }, label, subtitle, { vm.navigate(screen) })
                }
            }
            if (matches == 0) Text(stringResource(R.string.no_results), modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MarketsScreen(vm: WalletViewModel) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        MainHeader(stringResource(R.string.markets), onSearch = { searching = !searching; query = "" },
            onScan = { vm.openFeature(AppFeature.SCANNER) }, onProfile = { vm.openFeature(AppFeature.PROFILE) })
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
fun MoreScreen(vm: WalletViewModel, themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        MainHeader(stringResource(R.string.more), onScan = { vm.openFeature(AppFeature.SCANNER) },
            onProfile = { vm.openFeature(AppFeature.PROFILE) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ShortcutRow(Icons.Outlined.AccountCircle, stringResource(R.string.profile),
                stringResource(R.string.xmpp_not_connected), { vm.openFeature(AppFeature.PROFILE) })
            SectionLabel(R.string.explore_features)
            listOf(AppFeature.BUY, AppFeature.TRADE, AppFeature.DESKTOP).forEach { feature ->
                ShortcutRow(featureIcon(feature), stringResource(feature.title), stringResource(R.string.in_development),
                    { vm.openFeature(feature) })
            }
            SectionLabel(R.string.preferences)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.appearance), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                ThemePicker(themeMode, onThemeChange)
            }
            ShortcutRow(Icons.Outlined.Language, stringResource(R.string.app_language), stringResource(R.string.app_language_body), {
                context.startActivity(Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS,
                    Uri.parse("package:${context.packageName}")))
            })
            ShortcutRow(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.help_center),
                stringResource(R.string.help_shortcut_body), { vm.navigate(Screen.Help) })
            ShortcutRow(Icons.Outlined.Explore, stringResource(R.string.presentation),
                stringResource(R.string.onboarding_version, BuildConfig.VERSION_NAME), { vm.navigate(Screen.Welcome) })
            if (s.isWdkInitialized) {
                SectionLabel(R.string.local_wallet)
                ShortcutRow(Icons.Outlined.Draw, stringResource(R.string.sign), stringResource(R.string.sign_message), {
                    vm.resetSign(); vm.navigate(Screen.Sign)
                })
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_wallet), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_wallet)) },
        text = { Text(stringResource(R.string.delete_wallet_confirmation)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteWallet() }) {
            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
        } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun FeatureScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    BackHandler { vm.navigate(s.detailReturnScreen) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(s.feature.title)) { vm.navigate(s.detailReturnScreen) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Icon(featureIcon(s.feature), null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.in_development), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(s.feature.description), fontSize = 18.sp, lineHeight = 28.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(24.dp))
            if (s.feature in setOf(AppFeature.CONTACTS, AppFeature.PROFILE)) {
                OutlinedButton(onClick = { vm.navigate(Screen.Messaging) }) { Text(stringResource(R.string.about_xmpp)) }
            } else if (s.feature == AppFeature.SCANNER && s.isWdkInitialized) {
                OutlinedButton(onClick = { vm.updateReceiveAsset(Asset.USDC); vm.navigate(Screen.Receive) }) {
                    Text(stringResource(R.string.receive))
                }
            }
        }
    }
}

@Composable
fun HelpScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    BackHandler { vm.navigate(s.detailReturnScreen) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.help_center)) { vm.navigate(s.detailReturnScreen) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            listOf(R.string.help_recovery_title to R.string.help_recovery_body,
                R.string.help_networks_title to R.string.help_networks_body,
                R.string.help_status_title to R.string.help_status_body).forEach { (title, body) ->
                Text(stringResource(title), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(body), fontSize = 15.sp, lineHeight = 23.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun HomeScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    LaunchedEffect(s.isWdkInitialized) {
        if (s.isWdkInitialized) while (true) { vm.fetchBalance(); delay(30_000) }
    }
    Column(Modifier.fillMaxSize()) {
        MainHeader(stringResource(R.string.wallet), onScan = { vm.openFeature(AppFeature.SCANNER) },
            onProfile = { vm.navigate(Screen.More) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (!s.isWdkInitialized) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    OnboardingArtwork(IntroSlide.ASSETS, Modifier.fillMaxWidth().height(180.dp))
                    Text(stringResource(R.string.wallet_setup_title), fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.wallet_setup_body), textAlign = TextAlign.Center, lineHeight = 23.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    PrimaryButton(stringResource(R.string.create_new_wallet)) { vm.createWallet() }
                    Spacer(Modifier.height(12.dp))
                    OutlineActionButton(stringResource(R.string.import_existing_wallet)) { vm.resetImport(); vm.navigate(Screen.Import) }
                }
            } else {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.digital_dollars), fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        IconButton(onClick = vm::fetchBalance) {
                            Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh_balances), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(vm.formattedUsdcBalance, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(vm.formattedUsdtBalance, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.stablecoin_types), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WalletAction(Icons.Outlined.Payments, R.string.buy, { vm.openFeature(AppFeature.BUY) }, Modifier.weight(1f))
                        WalletAction(Icons.Default.ArrowDownward, R.string.receive, {
                            vm.updateReceiveAsset(Asset.USDC); vm.navigate(Screen.Receive)
                        }, Modifier.weight(1f))
                        WalletAction(Icons.Default.ArrowUpward, R.string.send, {
                            vm.resetSend(); vm.navigate(Screen.Send)
                        }, Modifier.weight(1f))
                        WalletAction(Icons.Outlined.SwapHoriz, R.string.trade, { vm.openFeature(AppFeature.TRADE) }, Modifier.weight(1f))
                    }
                }
                SectionLabel(R.string.other_assets)
                AssetRow("₿", Color(0xFFF7931A), stringResource(R.string.network_bitcoin), stringResource(R.string.mainnet), vm.formattedBtcBalance)
                AssetRow("Ξ", Color(0xFF627EEA), stringResource(R.string.eth_gas), stringResource(R.string.network_base), vm.formattedEthBalance)
                Text(stringResource(R.string.gas_explanation), fontSize = 12.sp, lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                ShortcutRow(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.help_center),
                    stringResource(R.string.help_shortcut_body), { vm.navigate(Screen.Help) })
            }
        }
    }
}

// ===== SEND SCREEN =====

@Composable
fun SendScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current

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
                    textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    modifier = Modifier
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
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.amount), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    enabled = s.sendAsset.isAvailable && !s.isLoading,
                    value = s.sendAmount,
                    onValueChange = { vm.updateSendAmount(it) },
                    textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
                PrimaryButton(stringResource(R.string.send), enabled = s.sendAsset.isAvailable && !s.isLoading) { vm.doSend() }
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

    BackHandler { vm.navigate(Screen.Home) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        ScreenHeader(stringResource(R.string.receive)) { vm.navigate(Screen.Home) }

        AssetSelector(s.receiveAsset, vm::updateReceiveAsset)
        if (s.receiveAsset == Asset.USDT) {
            Text(stringResource(R.string.usdt_bridge_description), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp))
        }
        Text(vm.receiveLabel.asString(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        val request = vm.receiveQrContent
        if (request != null) {
            QrCodeImage(request)
            Spacer(Modifier.height(12.dp))
            Text(
                if (s.receiveAsset == Asset.BTC)
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

        Spacer(Modifier.weight(1f))
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
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
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
