package org.astianx.midori.one

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            Text(stringResource(mode.labelRes))
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
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
        .heightIn(min = 56.dp).padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
        }
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
    }
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
            .heightIn(min = 54.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun OutlineActionButton(title: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
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
    val icon = when (slide) {
        IntroSlide.INTRO -> Icons.AutoMirrored.Outlined.Chat
        IntroSlide.ASSETS -> Icons.Outlined.AccountBalanceWallet
        IntroSlide.RECOVERY -> Icons.Outlined.Key
        IntroSlide.MESSAGING -> Icons.Outlined.Lock
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.size(104.dp).clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
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
fun AppNavigation(selected: Screen, onNavigate: (Screen) -> Unit, unread: Int = 0) {
    val destinations = listOf(
        Triple(Screen.Chats, R.string.chats, Icons.AutoMirrored.Outlined.Chat),
        Triple(Screen.Home, R.string.wallet, Icons.Outlined.AccountBalanceWallet),
        Triple(Screen.Markets, R.string.markets, Icons.Outlined.BarChart),
        Triple(Screen.More, R.string.more, Icons.Outlined.MoreHoriz)
    )
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
        NavigationBar(containerColor = Color.Transparent,
            windowInsets = WindowInsets(0, 0, 0, 0), tonalElevation = 0.dp) {
            destinations.forEach { (screen, label, icon) ->
                NavigationBarItem(selected = selected == screen, onClick = { onNavigate(screen) },
                    colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent,
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    icon = {
                        androidx.compose.material3.BadgedBox(badge = {
                            if (screen == Screen.Chats && unread > 0) androidx.compose.material3.Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary) {
                                Text(if (unread > 99) "99+" else unread.toString())
                            }
                        }) { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) }
                    }, label = { Text(stringResource(label), style = MaterialTheme.typography.labelMedium,
                        color = if (selected == screen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) })
            }
        }
    }
}

@Composable
fun MainHeader(title: String, onSearch: (() -> Unit)? = null, onScan: () -> Unit, onProfile: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface)
        if (onSearch != null) IconButton(onClick = onSearch) {
            Icon(Icons.Outlined.Search, stringResource(R.string.search))
        }
        IconButton(onClick = onScan) {
            Icon(Icons.Outlined.QrCodeScanner, stringResource(R.string.scan_qr), Modifier.size(23.dp))
        }
        IconButton(onClick = onProfile) {
            Icon(Icons.Outlined.PersonOutline, stringResource(R.string.profile), Modifier.size(23.dp))
        }
    }
}

@Composable
fun AppSearchField(query: String, onChange: (String) -> Unit, @StringRes label: Int) {
    androidx.compose.material3.TextField(value = query, onValueChange = onChange,
        placeholder = { Text(stringResource(label), style = MaterialTheme.typography.bodyMedium) },
        singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(21.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) {
                Icon(Icons.Default.Close, stringResource(R.string.clear_search))
            }
        }, colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ,
        textStyle = MaterialTheme.typography.bodyLarge)
}

@Composable
fun ShortcutRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit,
    trailing: String? = null) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
    Text(stringResource(text), style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp))
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

/** Small leaf and orbit motifs; drawn locally and shared by feature surfaces. */
@Composable
internal fun MidoriPattern(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.primary.copy(alpha = if (LocalDarkTheme.current) .10f else .075f)
    androidx.compose.foundation.Canvas(modifier) {
        val step = 92.dp.toPx()
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
        for (row in 0..(size.height / step).toInt()) for (col in 0..(size.width / step).toInt()) {
            val x = col * step + if (row % 2 == 0) 12.dp.toPx() else 50.dp.toPx()
            val y = row * step + 20.dp.toPx()
            val leaf = androidx.compose.ui.graphics.Path().apply {
                moveTo(x, y + 22.dp.toPx())
                cubicTo(x - 3.dp.toPx(), y, x + 13.dp.toPx(), y - 5.dp.toPx(), x + 26.dp.toPx(), y)
                cubicTo(x + 29.dp.toPx(), y + 17.dp.toPx(), x + 16.dp.toPx(), y + 29.dp.toPx(), x, y + 22.dp.toPx())
            }
            drawPath(leaf, ink, style = stroke)
            drawLine(ink, androidx.compose.ui.geometry.Offset(x, y + 23.dp.toPx()),
                androidx.compose.ui.geometry.Offset(x + 17.dp.toPx(), y + 7.dp.toPx()), strokeWidth = 1.dp.toPx())
            drawCircle(ink, 9.dp.toPx(), androidx.compose.ui.geometry.Offset(x + 46.dp.toPx(), y + 50.dp.toPx()), style = stroke)
            drawCircle(ink, 2.dp.toPx(), androidx.compose.ui.geometry.Offset(x + 10.dp.toPx(), y + 65.dp.toPx()))
        }
    }
}
