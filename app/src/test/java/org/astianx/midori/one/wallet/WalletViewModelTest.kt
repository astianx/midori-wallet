package org.astianx.midori.one.wallet

import org.astianx.midori.one.R
import org.astianx.midori.one.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletViewModelTest {
    @Test
    fun acceptsPrimitiveBooleanResults() {
        assertTrue(isValidVerificationResult(true))
        assertFalse(isValidVerificationResult(false))
    }

    @Test
    fun rejectsStringAndStructuredResults() {
        assertFalse(isValidVerificationResult("true"))
        assertFalse(isValidVerificationResult("1"))
        assertFalse(isValidVerificationResult(mapOf("valid" to true)))
    }

    @Test
    fun amountConversionRejectsTruncationZeroAndNegativeValues() {
        assertEquals("1000000000000000", toSmallestUnit("0.001", 18))
        assertEquals("1", toSmallestUnit("0.00000001", 8))
        assertNull(toSmallestUnit("0.000000001", 8))
        assertNull(toSmallestUnit("0", 8))
        assertNull(toSmallestUnit("-1", 18))
        assertEquals("1234567", toSmallestUnit("1.234567", Asset.USDC.decimals))
        assertNull(toSmallestUnit("1.2345678", Asset.USDC.decimals))
    }

    @Test
    fun broadcastDoesNotClaimConfirmation() {
        val tx = PendingTx(
            network = Network.BASE,
            toAddress = "0x0000000000000000000000000000000000000001",
            amount = "0.001",
            status = TxStatus.BROADCAST,
            hash = "0x" + "a".repeat(64)
        )
        assertEquals(UiText(R.string.tx_broadcast), tx.statusLabel)
        assertEquals("https://basescan.org/tx/${tx.hash}", tx.explorerUrl)
    }

    @Test
    fun usdcUsesBaseExplorerAndDistinctAsset() {
        val tx = PendingTx(
            network = Asset.USDC.network,
            asset = Asset.USDC,
            toAddress = "0x0000000000000000000000000000000000000001",
            amount = "1",
            status = TxStatus.BROADCAST,
            hash = "0x" + "b".repeat(64)
        )
        assertEquals(Asset.USDC, tx.asset)
        assertEquals("https://basescan.org/tx/${tx.hash}", tx.explorerUrl)
        assertEquals(UiText(R.string.tx_broadcast), tx.statusLabel)
    }

    @Test
    fun receiveQrUsesAnExchangeCompatibleAddressForBothBaseTokens() {
        val recipient = "0x" + "1".repeat(40)
        assertEquals(
            recipient,
            receiveRequest(Asset.USDC, recipient)
        )
        assertEquals(recipient, receiveRequest(Asset.ETH, recipient))
        assertNull(receiveRequest(Asset.USDC, ""))
        assertNull(receiveRequest(Asset.USDC, "not-an-address"))
    }

    @Test
    fun mainnetUsdtHasItsOwnContractAndReceiveRequest() {
        assertEquals(listOf(Asset.USDC, Asset.USDT, Asset.BTC, Asset.ETH), Asset.entries)
        assertTrue(Asset.USDT.isAvailable)
        assertEquals("0xfde4C96c8593536E31F229EA8f37b2ADa2699bb2", Asset.USDT.contract)
        val recipient = "0x" + "1".repeat(40)
        assertEquals(recipient, receiveRequest(Asset.USDT, recipient))
        assertEquals(receiveRequest(Asset.USDC, recipient), receiveRequest(Asset.USDT, recipient))
        val config = org.json.JSONObject(BaseMainnet.wdkConfig).getJSONObject("networks")
        assertEquals(8453, config.getJSONObject("base").getJSONObject("config").getInt("chainId"))
        assertEquals("bitcoin", config.getJSONObject("bitcoin").getJSONObject("config").getString("network"))
        assertFalse(BaseMainnet.wdkConfig.contains("sepolia"))
        assertFalse(BaseMainnet.wdkConfig.contains("testnet"))
    }

    @Test
    fun failedBalanceReadIsNotPresentedAsZero() {
        val state = WalletUiState()
        assertNull(state.usdcBalance)
        assertNull(state.ethBalance)
        assertNull(state.btcBalance)
    }

    @Test
    fun bitcoinUsesMainnetAddressesAndItsOwnExplorer() {
        val tx = PendingTx(network = Network.BTC, toAddress = "bc1q" + "q".repeat(38), amount = "0.001", hash = "a".repeat(64))
        assertEquals("https://blockbook.btc.zelcore.io/tx/${tx.hash}", tx.explorerUrl)
        assertEquals(tx.toAddress, receiveRequest(Asset.BTC, tx.toAddress))
        assertNull(receiveRequest(Asset.BTC, "tb1q" + "q".repeat(38)))
        assertNull(receiveRequest(Asset.BTC, "bcrt1q" + "q".repeat(38)))
        assertNull(receiveRequest(Asset.BTC, "mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn"))
    }
}
