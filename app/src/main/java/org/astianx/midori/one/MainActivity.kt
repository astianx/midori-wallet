package org.astianx.midori.one

import android.os.Bundle
import androidx.compose.runtime.CompositionLocalProvider
import org.astianx.midori.one.chat.*
import org.astianx.midori.one.wallet.*
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.astianx.midori.one.chat.ChatNotifications
import org.astianx.midori.one.chat.ChatSecurityScreen
import androidx.lifecycle.repeatOnLifecycle
import org.astianx.midori.one.chat.ChatViewModel
import org.astianx.midori.one.chat.ChatsScreen
import org.astianx.midori.one.chat.ConversationScreen
import org.astianx.midori.one.chat.GroupDetailsScreen
import org.astianx.midori.one.chat.MessagingScreen
import org.astianx.midori.one.wallet.CreateWalletScreen
import org.astianx.midori.one.wallet.HomeScreen
import org.astianx.midori.one.wallet.ImportWalletScreen
import org.astianx.midori.one.wallet.MarketAssetScreen
import org.astianx.midori.one.wallet.MarketsScreen
import org.astianx.midori.one.wallet.ReceiveScreen
import org.astianx.midori.one.wallet.RestoreErrorScreen
import org.astianx.midori.one.wallet.SendScreen
import org.astianx.midori.one.wallet.SignMessageScreen
import org.astianx.midori.one.wallet.WalletViewModel

class MainActivity : ComponentActivity() {
    private var notificationPeer by mutableStateOf<String?>(null)
    private var openConnectionSettings by mutableStateOf(false)
    private var verificationReturn by mutableStateOf(false)
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        verificationReturn = org.astianx.midori.one.wallet.p2p.isP2pVerificationReturn(intent.dataString)
        notificationPeer = intent.getStringExtra(ChatNotifications.PEER_EXTRA)
        openConnectionSettings = intent.action == org.astianx.midori.one.chat.ChatConnectionService.OPEN_CONNECTION
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        verificationReturn = org.astianx.midori.one.wallet.p2p.isP2pVerificationReturn(intent.dataString)
        notificationPeer = intent.getStringExtra(ChatNotifications.PEER_EXTRA)
        openConnectionSettings = intent.action == org.astianx.midori.one.chat.ChatConnectionService.OPEN_CONNECTION
        setContent {
            val preferences = remember { ThemePreferences(applicationContext) }
            val styleStore = remember { PersonalizationStore(applicationContext) }
            val walletStyleStore = remember { WalletAppearanceStore(applicationContext) }
            val appStyle by styleStore.state.collectAsState()
            val walletStyle by walletStyleStore.state.collectAsState()
            var themeMode by remember { mutableStateOf(preferences.load()) }
            val darkTheme = themeMode.isDark(isSystemInDarkTheme())
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(0xFFFFFFFF.toInt(), 0xFF111518.toInt()) { darkTheme }
                )
            }
            MidoriTheme(darkTheme, appStyle) {
                val vm: WalletViewModel = viewModel(
                    factory = WalletViewModel.Factory(applicationContext)
                )
                val chat = androidx.compose.runtime.remember { org.astianx.midori.one.chat.ChatSession.get(applicationContext) }
                val chatStatus by chat.state.collectAsState()
                val walletStatus by vm.state.collectAsState()
                LaunchedEffect(verificationReturn, walletStatus.isWdkInitialized, walletStatus.isLoading) {
                    if (verificationReturn && walletStatus.isWdkInitialized && !walletStatus.isLoading) {
                        vm.openP2pVerification(); verificationReturn = false
                    }
                }
                LaunchedEffect(openConnectionSettings, walletStatus.isLoading) {
                    if (openConnectionSettings && !walletStatus.isLoading) {
                        vm.navigate(Screen.Messaging)
                        openConnectionSettings = false
                    }
                }
                LaunchedEffect(notificationPeer, chatStatus.account, walletStatus.isLoading) {
                    notificationPeer?.let { peer ->
                        if (chatStatus.account.isNotBlank() && !walletStatus.isLoading) {
                            chat.open(peer).join()
                            if (chat.state.value.selectedPeer != null) vm.navigate(Screen.Conversation)
                            notificationPeer = null
                        }
                    }
                }
                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                LaunchedEffect(chatStatus.account, chatStatus.backgroundConnection, chatStatus.restoringSession, lifecycleOwner) {
                    if (chatStatus.restoringSession) return@LaunchedEffect
                    lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                        if (chatStatus.account.isNotBlank() && chatStatus.backgroundConnection) {
                            org.astianx.midori.one.chat.ChatConnectionService.start(applicationContext)
                        } else org.astianx.midori.one.chat.ChatConnectionService.stop(applicationContext)
                    }
                }
                androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                    chat.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_START) chat.setForeground(true)
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) chat.setForeground(false)
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                CompositionLocalProvider(LocalChatAppearance provides chatStatus.appearance,
                    LocalWalletAppearance provides walletStyle, LocalWalletAppearanceChange provides walletStyleStore::save) {
                WdkApp(vm, chat, themeMode, styleStore::save) { selected ->
                    preferences.save(selected)
                    themeMode = selected
                }
                }
            }
        }
    }
}

@Composable
fun WdkApp(vm: WalletViewModel, chat: ChatViewModel, themeMode: ThemeMode, onStyleChange: (AppStyle) -> Unit, onThemeChange: (ThemeMode) -> Unit) {
    val s by vm.state.collectAsState()
    val chatState by chat.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val isMainScreen = s.currentScreen in mainScreens
        BackHandler(enabled = isMainScreen && s.currentScreen != Screen.Chats) { vm.navigate(Screen.Chats) }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (s.currentScreen) {
                    Screen.Welcome -> WelcomeScreen(vm, themeMode, onThemeChange)
                    Screen.RestoreError -> RestoreErrorScreen(vm)
                    Screen.Create -> CreateWalletScreen(vm)
                    Screen.Import -> ImportWalletScreen(vm)
                    Screen.Home -> HomeScreen(vm)
                    Screen.Send -> SendScreen(vm)
                    Screen.Receive -> ReceiveScreen(vm)
                    Screen.Funding -> org.astianx.midori.one.wallet.FundingScreen(vm)
                    Screen.Fiat -> org.astianx.midori.one.wallet.fiat.FiatScreen(vm)
                    Screen.P2p -> org.astianx.midori.one.wallet.p2p.P2pScreen(vm)
                    Screen.Invest -> org.astianx.midori.one.wallet.swap.InvestmentScreen(vm)
                    Screen.Sign -> SignMessageScreen(vm)
                    Screen.Messaging -> MessagingScreen(chat, vm::navigate, s.messagingReturnScreen)
                    Screen.Chats -> ChatsScreen(chat, vm::navigate, { vm.openFeature(AppFeature.SCANNER) })
                    Screen.Markets -> MarketsScreen(vm)
                    Screen.More -> MoreScreen(vm, chat)
                    Screen.Feature -> FeatureScreen(vm)
                    Screen.MarketAsset -> MarketAssetScreen(vm)
                    Screen.Help -> HelpScreen(vm)
                    Screen.Conversation -> CompositionLocalProvider(LocalChatAppearance provides chatState.appearanceFor(chatState.selectedPeer)) { ConversationScreen(chat, vm::navigate) }
                    Screen.ChatSecurity -> ChatSecurityScreen(chat, vm::navigate)
                    Screen.Licenses -> LicensesScreen(vm)
                    Screen.GroupDetails -> GroupDetailsScreen(chat, vm::navigate)
                    Screen.Personalization -> PersonalizationScreen(chat, themeMode, onThemeChange, onStyleChange, vm::navigate)
                    Screen.ChatAppearance -> ChatAppearanceScreen(chat) { vm.navigate(Screen.Personalization) }
                    Screen.ConversationAppearance -> ChatAppearanceScreen(chat, chatState.selectedPeer) { vm.navigate(Screen.Conversation) }
                    Screen.WalletAppearance -> WalletAppearanceScreen { vm.navigate(Screen.Personalization) }
                    Screen.Profile -> ProfileScreen(chat, vm::navigate)
                }
            }
            if (isMainScreen) AppNavigation(s.currentScreen, vm::navigate, chatState.unreadCount)
        }

        if (s.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(32.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    s.statusText?.let { status ->
                        Spacer(Modifier.size(16.dp))
                        Text(
                            status.asString(),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        s.toastMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter)
            ) {
                Text(
                    text = msg.asString(),
                    color = if (s.toastIsSuccess) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (s.toastIsSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}
