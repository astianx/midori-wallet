package org.astianx.midori.one

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
            network = Network.ETH,
            toAddress = "0x0000000000000000000000000000000000000001",
            amount = "0.001",
            status = TxStatus.BROADCAST,
            hash = "0x" + "a".repeat(64)
        )
        assertTrue(tx.statusLabel.contains("confirmation not checked"))
        assertEquals("https://sepolia.etherscan.io/tx/${tx.hash}", tx.explorerUrl)
    }

    @Test
    fun usdcUsesSepoliaExplorerAndDistinctAsset() {
        val tx = PendingTx(
            network = Asset.USDC.network,
            asset = Asset.USDC,
            toAddress = "0x0000000000000000000000000000000000000001",
            amount = "1",
            status = TxStatus.BROADCAST,
            hash = "0x" + "b".repeat(64)
        )
        assertEquals(Asset.USDC, tx.asset)
        assertEquals("https://sepolia.etherscan.io/tx/${tx.hash}", tx.explorerUrl)
        assertTrue(tx.statusLabel.contains("confirmation not checked"))
    }
}
