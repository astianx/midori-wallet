package org.astianx.midori.one

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Original MW paths with a transparent background and a palette for each app theme. */
@Composable
fun MidoriLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(if (LocalDarkTheme.current) R.drawable.midori_mw_dark else R.drawable.midori_mw),
        contentDescription = stringResource(R.string.mw_logo),
        modifier = modifier.aspectRatio(696f / 313f),
        contentScale = ContentScale.Fit
    )
}

@Composable
fun ThemePicker(mode: ThemeMode, onChange: (ThemeMode) -> Unit, compact: Boolean = false) {
    var isOpen by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.change_theme, stringResource(mode.labelRes))
    if (compact) {
        IconButton(onClick = { isOpen = true }) {
            Icon(Icons.Default.Brightness6, contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        TextButton(onClick = { isOpen = true }) {
            Icon(Icons.Default.Brightness6, contentDescription = description, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.theme))
        }
    }
    if (isOpen) {
        AlertDialog(
            onDismissRequest = { isOpen = false },
            title = { Text(stringResource(R.string.appearance)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .selectable(selected = mode == option, role = Role.RadioButton, onClick = {
                                    onChange(option)
                                    isOpen = false
                                })
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = mode == option, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(stringResource(option.labelRes), color = MaterialTheme.colorScheme.onSurface)
                                if (option == ThemeMode.SYSTEM) {
                                    Text(stringResource(R.string.theme_system_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isOpen = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(34.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            ) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Divider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun NetworkPill(label: String, icon: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline
    val textColor = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(icon, fontSize = 12.sp, color = textColor)
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

@Composable
fun MainnetBadge() {
    Text(
        stringResource(R.string.mainnet),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
fun PrimaryButton(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OutlineActionButton(title: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SeedWordCell(index: Int, word: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Text(
            "$index",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(word, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SeedInputCell(index: Int, word: String, onWordChange: (String) -> Unit) {
    val wordLabel = stringResource(R.string.recovery_word, index)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Text(
            "$index",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = word,
            onValueChange = onWordChange,
            textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            modifier = Modifier.weight(1f).semantics { contentDescription = wordLabel },
            decorationBox = { innerTextField ->
                Box {
                    if (word.isEmpty()) {
                        Text(stringResource(R.string.word), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
fun QrCodeImage(content: String, size: Int = 200) {
    val bitmap = remember(content) {
        if (content.isEmpty()) null
        else try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        } catch (_: Exception) { null }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.qr_code),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.qr_label), fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AssetRow(
    iconText: String?,
    iconColor: Color,
    name: String,
    network: String,
    amount: String
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    iconText ?: name.first().toString(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(network, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Divider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 74.dp)
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AssetSelector(selected: Asset, onSelect: (Asset) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Asset.entries.forEach { asset ->
            NetworkPill(asset.name, if (asset.isToken) "$" else if (asset == Asset.BTC) "₿" else "Ξ",
                selected == asset) { onSelect(asset) }
        }
    }
}

internal enum class IntroSlide(
    @DrawableRes val artwork: Int,
    @StringRes val imageDescription: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val tag: Int
) {
    INTRO(R.drawable.onboarding_intro, R.string.onboarding_intro_image,
        R.string.onboarding_intro_title, R.string.onboarding_intro_body, R.string.brand_name),
    ASSETS(R.drawable.onboarding_assets, R.string.onboarding_assets_image,
        R.string.onboarding_assets_title, R.string.onboarding_assets_body, R.string.wallet),
    RECOVERY(R.drawable.onboarding_recovery, R.string.onboarding_recovery_image,
        R.string.onboarding_recovery_title, R.string.onboarding_recovery_body, R.string.recovery_phrase),
    MESSAGING(R.drawable.onboarding_messaging, R.string.onboarding_messaging_image,
        R.string.onboarding_messaging_title, R.string.onboarding_messaging_body, R.string.messaging)
}

@Composable
internal fun OnboardingArtwork(slide: IntroSlide, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(slide.artwork),
            contentDescription = stringResource(slide.imageDescription),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 8.dp)
        )
    }
}

@Composable
internal fun OnboardingControls(slides: List<IntroSlide>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val arrowColors = IconButtonDefaults.iconButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurface,
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = { onSelect(selected - 1) }, enabled = enabled && selected > 0, colors = arrowColors) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.onboarding_previous))
        }
        Row(Modifier.selectableGroup()) {
            slides.forEachIndexed { index, slide ->
                val description = stringResource(R.string.onboarding_page, index + 1, slides.size, stringResource(slide.title))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .selectable(selected = index == selected, enabled = enabled, role = Role.Tab,
                            onClick = { onSelect(index) })
                        .semantics { contentDescription = description }
                ) {
                    Box(Modifier.height(7.dp).width(if (selected == index) 24.dp else 7.dp)
                        .clip(CircleShape).background(if (selected == index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant))
                }
            }
        }
        IconButton(onClick = { onSelect(selected + 1) }, enabled = enabled && selected < slides.lastIndex, colors = arrowColors) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.onboarding_next))
        }
    }
}

@Composable
internal fun MessagingFeature(icon: ImageVector, @StringRes title: Int, @StringRes description: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp).size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(description), fontSize = 13.sp, lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AppNavigation(selected: Screen, onNavigate: (Screen) -> Unit) {
    val destinations = listOf(
        Triple(Screen.Chats, R.string.chats, Icons.AutoMirrored.Outlined.Chat),
        Triple(Screen.Home, R.string.wallet, Icons.Outlined.AccountBalanceWallet),
        Triple(Screen.Markets, R.string.markets, Icons.Outlined.BarChart),
        Triple(Screen.More, R.string.more, Icons.Outlined.GridView)
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.background,
        windowInsets = WindowInsets(0, 0, 0, 0), tonalElevation = 0.dp) {
        destinations.forEach { (screen, label, icon) ->
            NavigationBarItem(selected = selected == screen, onClick = { onNavigate(screen) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(stringResource(label), maxLines = 1) })
        }
    }
}

@Composable
fun MainHeader(title: String, onSearch: (() -> Unit)? = null, onScan: () -> Unit, onProfile: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (onSearch != null) IconButton(onClick = onSearch) {
            Icon(Icons.Outlined.Search, stringResource(R.string.search), tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = onScan) {
            Icon(Icons.Outlined.QrCodeScanner, stringResource(R.string.scan_qr), tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = onProfile) {
            Icon(Icons.Outlined.AccountCircle, stringResource(R.string.profile), tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
fun AppSearchField(query: String, onChange: (String) -> Unit, @StringRes label: Int) {
    OutlinedTextField(value = query, onValueChange = onChange, label = { Text(stringResource(label)) },
        singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) {
                Icon(Icons.Default.Close, stringResource(R.string.clear_search))
            }
        }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
}

@Composable
fun ShortcutRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit,
    trailing: String? = null) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (trailing != null) Text(trailing, fontSize = 11.sp, textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 82.dp))
        else Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SectionLabel(@StringRes text: Int) {
    Text(stringResource(text), fontSize = 13.sp, fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp))
}

@Composable
fun WalletAction(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
        .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(label), fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
    }
}

internal fun featureIcon(feature: AppFeature): ImageVector = when (feature) {
    AppFeature.CONTACTS -> Icons.Outlined.People
    AppFeature.PROFILE -> Icons.Outlined.AccountCircle
    AppFeature.SCANNER -> Icons.Outlined.QrCodeScanner
    AppFeature.BUY -> Icons.Outlined.Payments
    AppFeature.TRADE -> Icons.Outlined.SwapHoriz
    AppFeature.ALERTS -> Icons.Outlined.NotificationsNone
    AppFeature.DESKTOP -> Icons.Outlined.Computer
}

@StringRes
internal fun Asset.descriptionResource(): Int = when (this) {
    Asset.USDC -> R.string.native_usdc
    Asset.USDT -> R.string.bridged_usdt
    Asset.BTC -> R.string.bitcoin_asset
    Asset.ETH -> R.string.eth_asset
}
