package org.astianx.midori.one.wallet.swap

import java.math.BigDecimal
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.astianx.midori.one.wallet.Asset
import org.astianx.midori.one.wallet.BaseMainnet
import org.astianx.midori.one.wallet.DigitalDollarAccount
import org.json.JSONObject

internal const val BASE_NATIVE_ETH = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"

data class InvestmentRequest(val source: Asset, val amountUnits: String) {
    init {
        require(source in DigitalDollarAccount.assets)
        require(amountUnits.matches(Regex("[0-9]{1,78}")))
        require(amountUnits.toBigInteger().signum() > 0 && amountUnits.toBigInteger().bitLength() <= 256)
    }
}

data class InvestmentQuote(
    val request: InvestmentRequest,
    val outputUnits: String,
    val indicativeGasUsd: String,
    val block: Long,
    val expiresAt: Long
) {
    val ethAmount: String get() = BigDecimal(outputUnits).movePointLeft(18).stripTrailingZeros().toPlainString()
}

internal fun interface InvestmentQuoteGateway {
    suspend fun quote(request: InvestmentRequest): InvestmentQuote
}

/** Read-only Market API: this adapter cannot build transactions, approve or sign. */
internal class HttpInvestmentQuoteGateway : InvestmentQuoteGateway {
    companion object {
        private val requests = Mutex()
        private var lastRequestNanos = 0L
    }

    override suspend fun quote(request: InvestmentRequest): InvestmentQuote = withContext(Dispatchers.IO) {
        requests.withLock {
            val elapsedMs = (System.nanoTime() - lastRequestNanos) / 1_000_000
            if (elapsedMs < 1050) delay(1050 - elapsedMs)
            lastRequestNanos = System.nanoTime()
            val params = mapOf("network" to "8453", "version" to "6.2", "srcToken" to request.source.contract!!,
                "destToken" to BASE_NATIVE_ETH, "srcDecimals" to "6", "destDecimals" to "18",
                "amount" to request.amountUnits, "side" to "SELL")
            val query = params.entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
            val connection = URL("https://api.paraswap.io/prices?$query").openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/json")
                check(connection.responseCode == 200)
                val bytes = connection.inputStream.use { it.readNBytes(256 * 1024 + 1) }
                check(bytes.size <= 256 * 1024)
                parseInvestmentQuote(JSONObject(String(bytes, Charsets.UTF_8)), request, System.currentTimeMillis())
            } finally { connection.disconnect() }
        }
    }
}

internal fun parseInvestmentQuote(json: JSONObject, request: InvestmentRequest, now: Long): InvestmentQuote {
    val route = json.getJSONObject("priceRoute")
    require(route.get("network").toString() == BaseMainnet.CHAIN_ID.toString() && route.getString("version") == "6.2")
    require(route.getString("srcToken").equals(request.source.contract, ignoreCase = true))
    require(route.getString("destToken").equals(BASE_NATIVE_ETH, ignoreCase = true))
    require(route.get("srcDecimals").toString() == "6" && route.get("destDecimals").toString() == "18")
    require(route.getString("side") == "SELL" && route.getString("srcAmount") == request.amountUnits)
    require(route.getJSONArray("bestRoute").length() > 0)
    val output = route.getString("destAmount")
    require(output.matches(Regex("[0-9]{1,78}")) && BigInteger(output).signum() > 0 && BigInteger(output).bitLength() <= 256)
    val gas = route.getString("gasCostUSD")
    require(gas.matches(Regex("[0-9]{1,12}(\\.[0-9]{1,18})?")))
    val block = route.getLong("blockNumber")
    require(block > 0)
    return InvestmentQuote(request, output, gas, block, now + 60_000)
}
