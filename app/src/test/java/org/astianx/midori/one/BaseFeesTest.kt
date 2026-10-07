package org.astianx.midori.one

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class BaseFeesTest {
    private val sender = "0x" + "1".repeat(40)
    private val recipient = "0x" + "2".repeat(40)

    private class Provider : BaseFeeProvider {
        var network = BigInteger.valueOf(8453)
        var failOracle = false
        var transaction: BaseTransaction? = null
        var size = 0
        var operatorGas = BigInteger.ZERO
        override suspend fun chainId() = network
        override suspend fun estimateGas(transaction: BaseTransaction): BigInteger {
            this.transaction = transaction
            return BigInteger.valueOf(65000)
        }
        override suspend fun l1FeeUpperBound(unsignedSize: Int): BigInteger {
            check(!failOracle) { "Provider unavailable" }
            size = unsignedSize
            return BigInteger.valueOf(200)
        }
        override suspend fun operatorFee(gas: BigInteger): BigInteger {
            operatorGas = gas
            return BigInteger.valueOf(3)
        }
    }

    @Test
    fun usdcQuoteSimulatesExactTokenRecipientAndAmountAndIncludesAllFees() = runBlocking {
        val provider = Provider()
        val tx = baseTransaction(Asset.USDC, sender, recipient, BigInteger.valueOf(1234567))
        assertEquals(BaseMainnet.USDC_CONTRACT, tx.to)
        assertEquals(BigInteger.ZERO, tx.value)
        assertEquals("0xa9059cbb" + "2".repeat(40).padStart(64, '0') + "12d687".padStart(64, '0'), tx.data)
        assertEquals(BigInteger.valueOf(303), quoteBaseTotalFee(tx, BigInteger.valueOf(100), provider))
        assertEquals(tx, provider.transaction)
        assertEquals(324, provider.size)
        assertEquals(BigInteger.valueOf(65000), provider.operatorGas)
    }

    @Test
    fun nativeEthKeepsTheNativeValueAndHasNoTokenCalldata() = runBlocking {
        val provider = Provider()
        val tx = baseTransaction(Asset.ETH, sender, recipient, BigInteger.TEN)
        assertEquals(recipient, tx.to)
        assertEquals(BigInteger.TEN, tx.value)
        assertEquals("0x", tx.data)
        quoteBaseTotalFee(tx, BigInteger.ONE, provider)
        assertEquals(256, provider.size)
    }

    @Test
    fun wrongChainAndMissingL1QuoteFailInsteadOfUsingPartialFees() {
        val tx = baseTransaction(Asset.USDC, sender, recipient, BigInteger.ONE)
        val wrongChain = Provider().apply { network = BigInteger.valueOf(84532) }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { quoteBaseTotalFee(tx, BigInteger.ONE, wrongChain) }
        }
        assertNull(wrongChain.transaction)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { quoteBaseTotalFee(tx, BigInteger.ONE, Provider().apply { failOracle = true }) }
        }
    }

    @Test
    fun usdtUsesItsOwnContractAndUint256OverflowIsRejected() {
        assertEquals(BaseMainnet.USDT_CONTRACT, baseTransaction(Asset.USDT, sender, recipient, BigInteger.ONE).to)
        assertThrows(IllegalArgumentException::class.java) {
            baseTransaction(Asset.USDC, sender, recipient, BigInteger.ONE.shiftLeft(256))
        }
    }
}
