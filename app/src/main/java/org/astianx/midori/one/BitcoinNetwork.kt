package org.astianx.midori.one

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal fun interface BitcoinNetworkVerifier {
    suspend fun verify()
}

/** Check the configured indexer before handing a mainnet transaction to WDK. */
internal class HttpBitcoinNetworkVerifier : BitcoinNetworkVerifier {
    override suspend fun verify() = withContext(Dispatchers.IO) {
        val status = read("")
        walletRequire(status.getJSONObject("backend").getString("chain") == "main", R.string.error_wrong_bitcoin)
        walletRequire(read("/v2/block-index/0").getString("blockHash") == BitcoinMainnet.GENESIS_HASH, R.string.error_wrong_bitcoin)
    }

    private fun read(path: String): JSONObject {
        val connection = URL(BitcoinMainnet.API_URL + path).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "Midori-network-check/0.1")
            walletRequire(connection.responseCode in 200..299, R.string.error_bitcoin_provider)
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }
}
