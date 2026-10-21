package org.astianx.midori.one.wallet

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.astianx.midori.one.R

@Composable
fun NetworkPill(label: String, icon: String, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.heightIn(min = 48.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    )
}

@Composable
fun MainnetBadge() {
    Text(
        stringResource(R.string.mainnet),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun SeedWordCell(index: Int, word: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .heightIn(min = 48.dp)
            .padding(horizontal = 10.dp, vertical = 12.dp)
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
    androidx.compose.material3.OutlinedTextField(
        value = word,
        onValueChange = onWordChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        label = { Text(index.toString(), style = MaterialTheme.typography.labelSmall) },
        placeholder = { Text(stringResource(R.string.word), style = MaterialTheme.typography.bodySmall) },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = wordLabel }
    )
}

@Composable
fun QrCodeImage(content: String, size: Int = 224) {
    val bitmap = remember(content, size) {
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
                .background(Color.White)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
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
            .clickable(role = Role.Button, onClick = onClick)
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
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun AssetRow(
    iconText: String?,
    iconColor: Color,
    name: String,
    network: String,
    amount: String,
    onClick: (() -> Unit)? = null
) {
    val stackedAmount = LocalDensity.current.fontScale >= 1.2f
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
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
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(network, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (stackedAmount) {
                    Spacer(Modifier.height(4.dp))
                    Text(amount, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (!stackedAmount) Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                Text(amount, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Medium, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Divider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f),
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

@Composable
fun WalletAction(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit, modifier: Modifier = Modifier,
    primary: Boolean = false) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
        .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(
            if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, modifier = Modifier.size(24.dp),
                tint = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
    }
}

@StringRes
internal fun Asset.descriptionResource(): Int = when (this) {
    Asset.USDC -> R.string.native_usdc
    Asset.USDT -> R.string.bridged_usdt
    Asset.BTC -> R.string.bitcoin_asset
    Asset.ETH -> R.string.eth_asset
}

@Composable
internal fun StablecoinBalance(asset: Asset, balance: String, visible: Boolean, onClick: () -> Unit) {
    val stackedAmount = LocalDensity.current.fontScale >= 1.2f
    val amount = if (visible) balance.substringBeforeLast(" ") else "••••"
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center) {
            Text(if (asset == Asset.USDC) "$" else "₮", fontSize = 25.sp,
                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(asset.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.network_base), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (stackedAmount) {
                Spacer(Modifier.height(4.dp))
                Text(amount, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (!stackedAmount) Text(amount, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.weight(1.6f), textAlign = androidx.compose.ui.text.style.TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface)
    }
}
