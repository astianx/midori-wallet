package to.tether.wdk.starter

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import kotlinx.coroutines.delay

// ===== WELCOME SCREEN =====

@Composable
fun WelcomeScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        Text("\uD83D\uDEE1", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("WDK Wallet", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            "Self custodial. Multi chain. Open source.",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        TestnetBadge()
        Spacer(Modifier.height(40.dp))

        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            PrimaryButton("Create new wallet", enabled = !s.isLoading) { vm.createWallet() }
            Spacer(Modifier.height(12.dp))
            OutlineActionButton("Import existing wallet") {
                vm.resetImport()
                vm.navigate(Screen.Import)
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            "Powered by Tether WDK",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
    }
}

// ===== CREATE WALLET SCREEN =====

@Composable
fun CreateWalletScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader("Create wallet") { vm.navigate(Screen.Welcome) }

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "Write down these 12 words in order. This is the only way to recover your wallet.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text("Never share your seed phrase with anyone", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF5350))
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
                "Copy to clipboard",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("seed", s.seedPhrase.joinToString(" ")))
                        vm.showToast("Seed phrase copied")
                    }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton("I have saved my seed phrase", enabled = !s.isLoading) {
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
        ScreenHeader("Import wallet") { vm.navigate(Screen.Welcome) }

        Text(
            "Enter your 12 word seed phrase to restore your wallet.",
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
                if (s.importError) "Please enter all 12 words to continue."
                else "Type each word or paste from clipboard.",
                fontSize = 12.sp,
                color = if (s.importError) Color(0xFFEF5350) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Paste",
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
            PrimaryButton("Import wallet") { vm.doImport() }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ===== HOME SCREEN =====

@Composable
fun HomeScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.fetchBalance()
        while (true) {
            delay(30_000)
            vm.fetchBalance()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text("\uD83D\uDEE1", fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text("WDK Wallet", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            TestnetBadge()
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { vm.fetchBalance() }
                .padding(vertical = 24.dp)
        ) {
            Text("Balances", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(vm.formattedEthBalance, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(vm.formattedBtcBalance, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            if (s.ethAddress.isNotEmpty()) {
                Text(
                    "ETH ${s.ethAddress.take(6)}...${s.ethAddress.takeLast(4)}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (s.btcAddress.isNotEmpty()) {
                Text(
                    "BTC ${s.btcAddress.take(6)}...${s.btcAddress.takeLast(4)}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 0.dp)
        ) {
            Box(Modifier.weight(1f)) {
                ActionCard(Icons.Default.ArrowUpward, "Send") {
                    vm.resetSend()
                    vm.fetchFeeQuotes()
                    vm.navigate(Screen.Send)
                }
            }
            Box(Modifier.weight(1f)) {
                ActionCard(Icons.Default.ArrowDownward, "Receive") {
                    vm.updateReceiveNetwork(Network.ETH)
                    vm.navigate(Screen.Receive)
                }
            }
            Box(Modifier.weight(1f)) {
                ActionCard(Icons.Default.Edit, "Sign") {
                    vm.resetSign()
                    vm.navigate(Screen.Sign)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "ASSETS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            AssetRow("\u20BF", Color(0xFFF7931A), "Bitcoin", "BTC Testnet", vm.formattedBtcBalance, "$0.00")
            AssetRow("\u039E", Color(0xFF627EEA), "Ethereum", "Sepolia", vm.formattedEthBalance, "$0.00")
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFEF5350).copy(alpha = 0.08f))
                    .border(1.dp, Color(0xFFEF5350).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .clickable { vm.deleteWallet() }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF5350), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete wallet", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEF5350))
            }
        }

        Divider(color = MaterialTheme.colorScheme.outline)
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("\uD83D\uDCB3", fontSize = 14.sp)
                Text("Wallet", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ===== SEND SCREEN =====

@Composable
fun SendScreen(vm: WalletViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current

    BackHandler { vm.clearCompletedTransactions(); vm.navigate(Screen.Home) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader("Send") {
            vm.clearCompletedTransactions()
            vm.navigate(Screen.Home)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Select network and enter details.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp)
            ) {
                NetworkPill("Sepolia", "\u039E", s.sendNetwork == Network.ETH) { vm.updateSendNetwork(Network.ETH) }
                NetworkPill("BTC Testnet", "\u20BF", s.sendNetwork == Network.BTC) { vm.updateSendNetwork(Network.BTC) }
            }

            Spacer(Modifier.height(16.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("Recipient address", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                BasicTextField(
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
                                Text("Enter wallet address...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
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
                Text("Amount", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
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
                                Text("0.00", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            }
                            innerTextField()
                        }
                    }
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(vm.sendTokenLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Max",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { vm.setSendMax() }
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("Estimated fee", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(vm.sendFee, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                Divider(color = MaterialTheme.colorScheme.outline)
                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("Network", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(vm.sendNetworkLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                PrimaryButton("Send") { vm.doSend() }
            }

            if (s.pendingTransactions.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "TRANSACTIONS",
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
        TxStatus.PENDING -> Color(0xFFFF9800)
        TxStatus.COMPLETED -> Color(0xFF4CAF50)
        TxStatus.FAILED -> Color(0xFFEF5350)
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
                    if (tx.hash != null) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = cardColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(tx.statusLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cardColor)
            Spacer(Modifier.weight(1f))
            Text(if (tx.network == Network.ETH) "ETH" else "BTC", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(4.dp))
        Row {
            Text(tx.amount, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(4.dp))
            Text("to", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(
                "${tx.toAddress.take(8)}...${tx.toAddress.takeLast(4)}",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                        clipboard.setPrimaryClip(ClipData.newPlainText("hash", hash))
                        vm.showToast("Tx hash copied", success = true)
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
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
                        Text("Explorer", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
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
        ScreenHeader("Receive") { vm.navigate(Screen.Home) }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        ) {
            NetworkPill("ETH", "\u039E", s.receiveNetwork == Network.ETH) { vm.updateReceiveNetwork(Network.ETH) }
            NetworkPill("BTC", "\u20BF", s.receiveNetwork == Network.BTC) { vm.updateReceiveNetwork(Network.BTC) }
        }

        QrCodeImage(vm.receiveAddress)
        Spacer(Modifier.height(20.dp))

        Text(vm.receiveLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        Text(
            vm.receiveAddress,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .padding(14.dp)
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Copy address",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("address", vm.receiveAddress))
                    vm.showToast("Address copied")
                }
                .padding(horizontal = 18.dp, vertical = 8.dp)
        )

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
        ScreenHeader("Sign message") { vm.navigate(Screen.Home) }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Sign a message with your private key to prove wallet ownership for dApp authentication.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp)
            ) {
                NetworkPill("Ethereum", "\u039E", s.signNetwork == Network.ETH) { vm.updateSignNetwork(Network.ETH) }
                NetworkPill("Bitcoin", "\u20BF", s.signNetwork == Network.BTC) { vm.updateSignNetwork(Network.BTC) }
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
                Text("Message to sign", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = s.signMessage,
                    onValueChange = { vm.updateSignMessage(it) },
                    textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
            }

            s.signResult?.let { signature ->
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF4CAF50).copy(alpha = 0.06f))
                        .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Signature", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Copy",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("sig", signature))
                                vm.showToast("Signature copied", success = true)
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(signature, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(Modifier.height(4.dp))

                val verifyColor = when (s.verifyResult) {
                    null -> MaterialTheme.colorScheme.primary
                    true -> Color(0xFF4CAF50)
                    false -> Color(0xFFEF5350)
                }
                val verifyText = when (s.verifyResult) {
                    null -> "Verify signature"
                    true -> "Verified"
                    false -> "Invalid"
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
                    Icon(Icons.Default.Shield, contentDescription = null, tint = verifyColor, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(verifyText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = verifyColor)
                }
            }
        }

        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
            PrimaryButton("Sign message") { vm.doSign() }
        }
    }
}
