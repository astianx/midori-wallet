package org.astianx.midori.one

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.astianx.midori.one.chat.label
import org.astianx.midori.one.chat.ChatViewModel
import org.astianx.midori.one.wallet.Asset
import org.astianx.midori.one.wallet.WalletViewModel

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
fun MoreScreen(vm: WalletViewModel, chat: ChatViewModel) {
    val chatState by chat.state.collectAsState()
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        MainHeader(stringResource(R.string.more), onScan = { vm.openFeature(AppFeature.SCANNER) },
            onProfile = { vm.navigate(Screen.Profile) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { vm.navigate(Screen.Profile) }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))) {
                    org.astianx.midori.one.chat.ChatAvatar(chatState.profile.name.ifBlank { chatState.account.ifBlank { "M" } }, Modifier.fillMaxSize())
                    org.astianx.midori.one.chat.LocalArtwork(chatState.profile.photo, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(chatState.profile.name.ifBlank { stringResource(R.string.profile) }, style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(chatState.account.ifBlank { stringResource(R.string.chat_account_title) },
                        style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            SectionLabel(R.string.preferences)
            ShortcutRow(Icons.Outlined.Lock, stringResource(R.string.chat_privacy),
                stringResource(R.string.chat_encrypted), { vm.navigate(Screen.Messaging) })
            ShortcutRow(Icons.Outlined.Description, stringResource(R.string.chat_licenses),
                stringResource(R.string.app_name), { vm.navigate(Screen.Licenses) })
            ShortcutRow(Icons.Outlined.Palette, stringResource(R.string.style_title),
                stringResource(R.string.style_summary), { vm.navigate(Screen.Personalization) })
            ShortcutRow(Icons.Outlined.Wifi, stringResource(R.string.chat_background_title),
                stringResource(chatState.connection.label()), { vm.navigate(Screen.Messaging) })
            ShortcutRow(Icons.Outlined.NotificationsNone, stringResource(R.string.chat_notifications_title),
                stringResource(R.string.chat_notifications_summary), {
                    context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                })
            ShortcutRow(Icons.Outlined.Language, stringResource(R.string.app_language), stringResource(R.string.app_language_body), {
                context.startActivity(Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS,
                    Uri.parse("package:${context.packageName}")))
            })
            ShortcutRow(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.help_center),
                stringResource(R.string.help_shortcut_body), { vm.navigate(Screen.Help) })
            ShortcutRow(Icons.Outlined.Explore, stringResource(R.string.presentation),
                stringResource(R.string.onboarding_version, BuildConfig.VERSION_NAME), { vm.navigate(Screen.Welcome) })
            SectionLabel(R.string.explore_features)
            listOf(AppFeature.BUY, AppFeature.TRADE, AppFeature.DESKTOP).forEach { feature ->
                ShortcutRow(featureIcon(feature), stringResource(feature.title), stringResource(R.string.in_development),
                    { vm.openFeature(feature) })
            }
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
                OutlinedButton(onClick = { vm.navigate(Screen.Messaging) }) { Text(stringResource(R.string.chat_account_title)) }
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
fun LicensesScreen(vm: WalletViewModel) {
    val context = LocalContext.current
    val text = androidx.compose.runtime.remember { context.assets.open("messaging-notices.txt").bufferedReader().use { it.readText() } }
    BackHandler { vm.navigate(Screen.More) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.chat_licenses)) { vm.navigate(Screen.More) }
        Text(text, Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), fontSize = 13.sp, lineHeight = 20.sp)
    }
}
