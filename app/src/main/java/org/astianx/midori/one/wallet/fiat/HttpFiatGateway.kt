package org.astianx.midori.one.wallet.fiat

import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astianx.midori.one.wallet.Asset
import org.json.JSONObject

/** Talks only to our backend. No partner key, secret, token or client-supplied IP in the APK. */
internal class FiatHttpFailure(val code: String) : Exception(code)

internal class FiatHttpClient(backendUrl: String) {
    val origin = fiatBackendOrigin(backendUrl)
    suspend fun request(path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        require(path in setOf("/v1/fiat/capabilities", "/v1/fiat/offers", "/v1/fiat/challenge", "/v1/fiat/session",
            "/v1/p2p/catalog", "/v1/p2p/rates", "/v1/p2p/operations", "/v1/p2p/account", "/v1/p2p/quote",
            "/v1/p2p/prepare", "/v1/p2p/order", "/v1/p2p/history", "/v1/p2p/action", "/v1/p2p/receipt"))
        val connection = URL("$origin$path").openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val bytes = (if (status == 200) connection.inputStream else connection.errorStream ?: throw FiatHttpFailure("unavailable")).use { it.readNBytes(262_145) }
            check(bytes.size <= 262_144)
            val result = JSONObject(bytes.toString(Charsets.UTF_8))
            if (status != 200) {
                val code = result.optString("error")
                throw FiatHttpFailure(if (code.matches(Regex("[a-z_]{1,64}"))) code else "unavailable")
            }
            result
        } finally { connection.disconnect() }
    }
}

internal class HttpFiatGateway(backendUrl: String) : FiatGateway {
    private val client = FiatHttpClient(backendUrl)
    override val origin = client.origin
    private suspend fun request(path: String, body: JSONObject? = null) = client.request("/v1/fiat/$path", body)
    override suspend fun capabilities(): FiatCapabilities {
        val response = request("capabilities")
        check(response.getInt("version") == 2)
        val assets = response.getJSONArray("assets")
        val fiats = response.getJSONArray("fiats")
        val countries = response.getJSONArray("countries")
        return FiatCapabilities((0 until assets.length()).map { index ->
            val row = assets.getJSONObject(index)
            val asset = Asset.valueOf(row.getString("asset"))
            check(row.getString("network") == if (asset == Asset.BTC) "mainnet" else "base")
            check(row.getInt("decimals") == asset.decimals)
            FiatAsset(asset, row.getBoolean("buy"), row.getBoolean("sell"))
        }, (0 until fiats.length()).map { index ->
            val row = fiats.getJSONObject(index)
            val code = row.getString("code")
            val decimals = row.getInt("decimals")
            check(code.matches(Regex("[A-Z]{3}")) && decimals in 0..4)
            FiatCurrency(code, decimals, row.getBoolean("buy"), row.getBoolean("sell"))
        }, (0 until countries.length()).map { index ->
            val row = countries.getJSONObject(index)
            val code = row.getString("code")
            check(code.matches(Regex("[A-Z]{2}")))
            FiatCountry(code, row.getBoolean("buy"), row.getBoolean("sell"))
        })
    }
    override suspend fun offers(selection: FiatSelection): List<FiatOffer> {
        val rows = request("offers", JSONObject().put("operation", selection.operation.name)
            .put("asset", selection.asset.name).put("fiat", selection.fiat).put("amountUnits", selection.amountUnits)
            .put("country", selection.country)).getJSONArray("offers")
        check(rows.length() <= 32)
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val received = FiatSelection(FiatOperation.valueOf(row.getString("operation")), Asset.valueOf(row.getString("asset")),
                row.getString("fiat"), row.getString("amountUnits"), row.getString("country"))
            FiatOffer(row.getString("id"), row.getString("provider"), row.getString("providerName"), received,
                row.getString("fiatAmountUnits"), row.getString("cryptoAmountUnits"), row.getString("feeUnits"),
                row.getString("paymentMethod"), row.getLong("expiresAt")).also { check(it.validFor(selection)) }
        }
    }
    override suspend fun challenge(request: FiatRequest): FiatChallenge {
        val result = request("challenge", JSONObject().put("operation", request.operation.name)
            .put("asset", request.asset.name).put("fiat", request.fiat).put("amountUnits", request.amountUnits)
            .put("address", request.address).put("identityAddress", request.identityAddress)
            .put("country", request.country).put("provider", request.provider).put("offerId", request.offerId))
        return FiatChallenge(result.getString("nonce"), result.getLong("expiresAt"))
    }
    override suspend fun session(nonce: String, signature: String): String = validateFiatCheckoutUrl(
        request("session", JSONObject().put("nonce", nonce).put("signature", signature)).getString("checkoutUrl"), origin)
}
