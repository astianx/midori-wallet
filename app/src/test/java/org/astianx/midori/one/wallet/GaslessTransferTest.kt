package org.astianx.midori.one.wallet

import java.math.BigInteger
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GaslessTransferTest {
    private val recipient = "0x" + "2".repeat(40)
    private val bundleHash = "0x" + "c".repeat(64)

    @Test
    fun feeIsChargedInTheSentTokenFirstAndThenTheOtherStablecoin() {
        assertEquals(listOf(Asset.USDC, Asset.USDT), gaslessFeeCandidates(Asset.USDC))
        assertEquals(listOf(Asset.USDT, Asset.USDC), gaslessFeeCandidates(Asset.USDT))
    }

    @Test
    fun aFeeInTheSentTokenMustFitOnTopOfTheAmountButAnotherTokenOnlyNeedsItself() {
        val balances = mapOf(Asset.USDC to BigInteger.valueOf(1_000_000), Asset.USDT to BigInteger.valueOf(5_000))
        val fee = BigInteger.valueOf(3_000)
        assertFalse(gaslessCovers(Asset.USDC, BigInteger.valueOf(1_000_000), Asset.USDC, fee, balances))
        assertTrue(gaslessCovers(Asset.USDC, BigInteger.valueOf(997_000), Asset.USDC, fee, balances))
        assertTrue(gaslessCovers(Asset.USDC, BigInteger.valueOf(1_000_000), Asset.USDT, fee, balances))
        assertFalse(gaslessCovers(Asset.USDC, BigInteger.ONE, Asset.USDT, BigInteger.valueOf(5_001), balances))
        assertFalse("An unreadable balance never counts as enough",
            gaslessCovers(Asset.USDC, BigInteger.ONE, Asset.USDT, fee, mapOf(Asset.USDC to BigInteger.TEN)))
    }

    @Test
    fun argumentsBindTheExactTokenRecipientAmountAndFeeToken() {
        val args = JSONArray(gaslessArgs(Asset.USDT, recipient, "1234567", Asset.USDC))
        val transfer = args.getJSONObject(0)
        assertEquals(BaseMainnet.USDT_CONTRACT, transfer.getString("token"))
        assertEquals(recipient, transfer.getString("recipient"))
        assertEquals("1234567", transfer.getString("amount"))
        val config = args.getJSONObject(1)
        assertFalse(config.getBoolean("isSponsored"))
        assertEquals(BaseMainnet.USDC_CONTRACT, config.getJSONObject("paymasterToken").getString("address"))
    }

    @Test
    fun theWalletConfigurationPinsTheAuditedDelegateEntryPointAndPaymaster() {
        val networks = JSONObject(BaseMainnet.wdkConfig).getJSONObject("networks")
        val gasless = networks.getJSONObject("base-gasless")
        assertEquals("base-gasless", gasless.getString("blockchain"))
        val config = gasless.getJSONObject("config")
        assertEquals(8453, config.getInt("chainId"))
        assertEquals(BaseMainnet.RPC_URL, config.getString("provider"))
        assertEquals("0.8", config.getString("entryPointVersion"))
        assertEquals("https://api.candide.dev/public/v3/8453", config.getString("bundlerUrl"))
        assertEquals("0xe6Cae83BdE06E4c305530e199D7217f42808555B", config.getString("delegationAddress"))
        assertEquals("0xa8151918eac3818deb713d3dbbb7930329fe86ed", config.getString("paymasterAddress"))
        assertEquals(BaseMainnet.USDC_CONTRACT, config.getJSONObject("paymasterToken").getString("address"))
        assertEquals(BaseMainnet.GASLESS_MAX_FEE, config.getLong("transferMaxFee"))
        assertFalse("Sponsorship needs an account the app does not have", config.has("isSponsored"))
        assertEquals(8453, networks.getJSONObject("base").getJSONObject("config").getInt("chainId"))
    }

    @Test
    fun receiptParsingRequiresASuccessFlagAndAWellFormedBundleHash() {
        val included = parseUserOperationReceipt("""{"success":true,"receipt":{"transactionHash":"$bundleHash"}}""")
        assertEquals(UserOperationOutcome(bundleHash, true), included)
        assertEquals(UserOperationOutcome(bundleHash, false),
            parseUserOperationReceipt(JSONObject("""{"success":false,"receipt":{"transactionHash":"$bundleHash"}}""")))
        assertNull("Not yet included", parseUserOperationReceipt("null"))
        assertNull(parseUserOperationReceipt(JSONObject.NULL))
        assertNull(parseUserOperationReceipt(null))
        assertNull(parseUserOperationReceipt("""{"receipt":{"transactionHash":"$bundleHash"}}"""))
        assertNull(parseUserOperationReceipt("""{"success":true,"receipt":{"transactionHash":"0x12"}}"""))
        assertNull(parseUserOperationReceipt("""{"success":true}"""))
        assertNull(parseUserOperationReceipt("not json"))
    }

    @Test
    fun feeDisplayUsesTheFeeTokenInsteadOfEth() {
        fun review(feeAsset: Asset, asset: Asset, fee: String) =
            SendReview(asset, recipient, "1", "1000000", fee, 0, feeAsset)
        assertEquals("0.003 USDC", review(Asset.USDC, Asset.USDC, "3000").feeDisplay)
        assertEquals("0.012345 USDT", review(Asset.USDT, Asset.USDC, "12345").feeDisplay)
    }
}
