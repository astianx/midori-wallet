package org.astianx.midori.one.wallet

import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astianx.midori.one.R
import org.json.JSONArray
import org.json.JSONObject

internal data class BaseTransaction(val from: String, val to: String, val value: BigInteger, val data: String)

internal fun baseTransaction(asset: Asset, from: String, recipient: String, amount: BigInteger): BaseTransaction {
    require(asset.isToken && asset.isAvailable)
    require(from.matches(Regex("0x[a-fA-F0-9]{40}")) && recipient.matches(Regex("0x[a-fA-F0-9]{40}")))
    require(amount.signum() > 0 && amount.bitLength() <= 256)
    return BaseTransaction(from, requireNotNull(asset.contract), BigInteger.ZERO,
        "0xa9059cbb" + recipient.removePrefix("0x").padStart(64, '0') + amount.toString(16).padStart(64, '0'))
}

internal interface BaseFeeProvider {
    suspend fun chainId(): BigInteger
    suspend fun estimateGas(transaction: BaseTransaction): BigInteger
    suspend fun l1FeeUpperBound(unsignedSize: Int): BigInteger
    suspend fun operatorFee(gas: BigInteger): BigInteger
}

/** For legacy/type-2 ERC-20 and contract transactions (no access list or authorization list). */
internal suspend fun quoteBaseTotalFee(
    transaction: BaseTransaction,
    executionFee: BigInteger,
    provider: BaseFeeProvider
): BigInteger {
    walletRequire(provider.chainId() == BaseMainnet.CHAIN_ID.toBigInteger(), R.string.error_wrong_base)
    walletRequire(executionFee.signum() > 0, R.string.error_gas_quote)
    val gas = provider.estimateGas(transaction)
    walletRequire(gas.signum() > 0, R.string.error_gas_quote)
    // A simple unsigned legacy/type-2 envelope fits in 256 bytes even with uint256
    // scalar fields; add calldata separately. The oracle itself accounts for the
    // signature. This deliberately conservative estimate is not a guaranteed fee cap.
    val unsignedSize = 256 + (transaction.data.length - 2) / 2
    val l1Fee = provider.l1FeeUpperBound(unsignedSize)
    val operatorFee = provider.operatorFee(gas)
    walletRequire(l1Fee.signum() >= 0 && operatorFee.signum() >= 0, R.string.error_invalid_base_response)
    return executionFee + l1Fee + operatorFee
}

/** Public read-only RPC; signing and broadcasting remain exclusively inside WDK. */
internal class HttpBaseFeeProvider : BaseFeeProvider {
    override suspend fun chainId() = rpc("eth_chainId", JSONArray())

    override suspend fun estimateGas(transaction: BaseTransaction) = rpc("eth_estimateGas", JSONArray().put(
        JSONObject().put("from", transaction.from).put("to", transaction.to)
            .put("value", "0x${transaction.value.toString(16)}").put("data", transaction.data)
    ))

    override suspend fun l1FeeUpperBound(unsignedSize: Int) = oracle("f1c7a58b", unsignedSize.toBigInteger())
    override suspend fun operatorFee(gas: BigInteger) = oracle("275aedd2", gas)

    private suspend fun oracle(selector: String, argument: BigInteger) = rpc("eth_call", JSONArray().put(
        JSONObject().put("to", "0x420000000000000000000000000000000000000F")
            .put("data", "0x$selector${argument.toString(16).padStart(64, '0')}")
    ).put("latest"))

    private suspend fun rpc(method: String, params: JSONArray): BigInteger = withContext(Dispatchers.IO) {
        val connection = URL(BaseMainnet.RPC_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params)
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            walletRequire(connection.responseCode == 200, R.string.error_gas_quote)
            val response = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            walletRequire(!response.has("error") && response.optInt("id") == 1, R.string.error_gas_quote)
            val result = response.getString("result")
            walletRequire(result.matches(Regex("0x[0-9a-fA-F]+")), R.string.error_invalid_base_response)
            BigInteger(result.removePrefix("0x"), 16)
        } finally {
            connection.disconnect()
        }
    }
}
