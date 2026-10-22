package org.astianx.midori.one.wallet

import java.math.BigInteger
import org.astianx.midori.one.R
import org.astianx.midori.one.UiText
import org.json.JSONArray
import org.json.JSONObject

/** The fee is charged in a stablecoin by the paymaster; both supported tokens use 6 decimals. */
internal data class GaslessQuote(val feeAsset: Asset, val fee: BigInteger)

internal data class UserOperationOutcome(val transactionHash: String, val success: Boolean)

internal fun gaslessFeeCandidates(sent: Asset): List<Asset> =
    listOf(sent) + DigitalDollarAccount.assets.filter { it != sent }

/** A fee paid in the token being sent must fit on top of the amount. */
internal fun gaslessCovers(
    sent: Asset, amount: BigInteger, feeAsset: Asset, fee: BigInteger, balances: Map<Asset, BigInteger>
): Boolean {
    val balance = balances[feeAsset] ?: return false
    return balance >= fee + if (feeAsset == sent) amount else BigInteger.ZERO
}

internal fun gaslessArgs(asset: Asset, recipient: String, units: String, feeAsset: Asset): String = JSONArray()
    .put(JSONObject().put("token", requireNotNull(asset.contract)).put("recipient", recipient).put("amount", units))
    .put(JSONObject().put("isSponsored", false)
        .put("paymasterToken", JSONObject().put("address", requireNotNull(feeAsset.contract))))
    .toString()

/** The gasless module must derive exactly the EOA that holds the funds; anything else fails closed. */
internal suspend fun verifyGaslessAccount(client: WalletClient, owner: String) {
    val address = client.getAddress(BaseMainnet.GASLESS_NETWORK)
    walletRequire(address.equals(owner, ignoreCase = true), R.string.error_gasless_account)
}

internal suspend fun freshStableBalances(client: WalletClient): Map<Asset, BigInteger> {
    val balances = mutableMapOf<Asset, BigInteger>()
    for (asset in DigitalDollarAccount.assets) {
        val balance = try {
            client.callMethod("getTokenBalance", Network.BASE.wdkName, 0,
                JSONObject.quote(requireNotNull(asset.contract))).toString().toBigIntegerOrNull()
        } catch (_: Exception) { null }
        if (balance != null && balance.signum() >= 0) balances[asset] = balance
    }
    return balances
}

/** The whole balance was requested but the fee needs room: carries the largest amount that still fits it. */
internal class GaslessAmountAdjustment(val maxAmount: BigInteger) : Exception()

private suspend fun gaslessFee(
    client: WalletClient, asset: Asset, recipient: String, units: BigInteger, feeAsset: Asset
): BigInteger {
    val quoted = client.callMethod("quoteTransfer", BaseMainnet.GASLESS_NETWORK, 0,
        gaslessArgs(asset, recipient, units.toString(), feeAsset))
    val fee = quotedFee(quoted)?.toBigIntegerOrNull() ?: throw WalletException(UiText(R.string.error_gas_quote))
    walletRequire(fee.signum() > 0, R.string.error_gas_quote)
    walletRequire(fee < BaseMainnet.GASLESS_MAX_FEE.toBigInteger(), R.string.error_fee_too_high)
    return fee
}

/**
 * Quotes the fee in the sent token when it still fits, otherwise in the other stablecoin. A reviewed
 * [feeAsset] is never switched silently. Sending the entire balance of a token that must also pay the
 * fee raises [GaslessAmountAdjustment] instead of changing the amount behind the user's back.
 */
internal suspend fun quoteGaslessTransfer(
    client: WalletClient,
    asset: Asset,
    recipient: String,
    units: BigInteger,
    balances: Map<Asset, BigInteger>,
    feeAsset: Asset? = null
): GaslessQuote {
    require(asset in DigitalDollarAccount.assets)
    for (candidate in feeAsset?.let(::listOf) ?: gaslessFeeCandidates(asset)) {
        val balance = balances[candidate] ?: continue
        if (balance <= if (candidate == asset) units else BigInteger.ZERO) continue
        val fee = gaslessFee(client, asset, recipient, units, candidate)
        if (gaslessCovers(asset, units, candidate, fee, balances)) return GaslessQuote(candidate, fee)
    }
    val balance = balances[asset]
    if (feeAsset == null && balance != null && units >= balance) {
        // The fee does not depend on the amount, so a smaller probe amount reveals it.
        val reserve = BaseMainnet.GASLESS_MAX_FEE.toBigInteger()
        val probe = if (balance > reserve) balance - reserve else balance / BigInteger.TWO
        if (probe.signum() > 0) {
            val fee = gaslessFee(client, asset, recipient, probe, asset)
            val withMargin = fee + (fee * BigInteger.valueOf(15) + BigInteger.valueOf(99)) / BigInteger.valueOf(100)
            if (balance > withMargin) throw GaslessAmountAdjustment(balance - withMargin)
        }
    }
    throw WalletException(UiText(R.string.error_insufficient_fee_token))
}

/** Pending (or unreadable) receipts return null so the caller keeps waiting instead of guessing. */
internal fun parseUserOperationReceipt(result: Any?): UserOperationOutcome? = try {
    val receipt = when (result) {
        is JSONObject -> result
        is String -> result.trim().takeIf { it.startsWith("{") }?.let(::JSONObject)
        else -> null
    }
    val hash = receipt?.optJSONObject("receipt")?.optString("transactionHash").orEmpty()
    if (receipt != null && receipt.has("success") && hash.matches(Regex("0x[a-fA-F0-9]{64}")))
        UserOperationOutcome(hash, receipt.optBoolean("success", false)) else null
} catch (_: Exception) { null }
