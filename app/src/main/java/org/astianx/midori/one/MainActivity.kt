package org.astianx.midori.one

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { ThemePreferences(applicationContext) }
            var themeMode by remember { mutableStateOf(preferences.load()) }
            val darkTheme = themeMode.isDark(isSystemInDarkTheme())
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(0xE6FAFAF8.toInt(), 0x80121612.toInt()) { darkTheme }
                )
            }
            MidoriTheme(darkTheme) {
                val vm: WalletViewModel = viewModel(
                    factory = WalletViewModel.Factory(applicationContext)
                )
                var showBrandSplash by rememberSaveable { mutableStateOf(true) }
                LaunchedEffect(showBrandSplash) {
                    if (showBrandSplash) {
                        delay(1_000)
                        showBrandSplash = false
                    }
                }
                if (showBrandSplash) BrandSplashScreen() else WdkApp(vm, themeMode) { selected ->
                    preferences.save(selected)
                    themeMode = selected
                }
            }
        }
    }
}

@Composable
fun WdkApp(vm: WalletViewModel, themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    val s by vm.state.collectAsState()

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
                    Screen.Sign -> SignMessageScreen(vm)
                    Screen.Messaging -> MessagingScreen(vm)
                    Screen.Chats -> ChatsScreen(vm)
                    Screen.Markets -> MarketsScreen(vm)
                    Screen.More -> MoreScreen(vm, themeMode, onThemeChange)
                    Screen.Feature -> FeatureScreen(vm)
                    Screen.MarketAsset -> MarketAssetScreen(vm)
                    Screen.Help -> HelpScreen(vm)
                }
            }
            if (isMainScreen) AppNavigation(s.currentScreen, vm::navigate)
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
