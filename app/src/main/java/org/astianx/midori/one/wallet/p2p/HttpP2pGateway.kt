package org.astianx.midori.one.wallet.p2p

import org.astianx.midori.one.wallet.BaseMainnet
import org.astianx.midori.one.wallet.fiat.FiatHttpClient
import org.json.JSONObject

internal class HttpP2pGateway(backendUrl: String) : P2pGateway {
    private val client = FiatHttpClient(backendUrl)
    override suspend fun catalog() = parseP2pCatalog(client.request("/v1/p2p/catalog"))
    override suspend fun rates(country: P2pCountry) = parseP2pRate(
        client.request("/v1/p2p/rates", JSONObject().put("country", country.country)), country)
}

private fun verifyProtocol(json: JSONObject) {
    require(json.get("version").toString() == "1" && json.get("chainId").toString() == "8453")
    require(json.getString("asset") == "USDC" && json.getString("token").equals(BaseMainnet.USDC_CONTRACT, true))
    require(json.getString("diamond").equals(P2P_DIAMOND, true) && json.getString("execution") == "READ_ONLY")
}
private fun country(json: JSONObject): P2pCountry {
    val row = P2pCountry(json.getString("country"), json.getString("fiat"), json.getString("currency"),
        json.getString("paymentMethod"), json.getBoolean("alpha"), json.getBoolean("buy"), json.getBoolean("sell"))
    require(row.country.matches(Regex("[A-Z]{2}")) && row.fiat.matches(Regex("[A-Z]{3}")) &&
        row.currency.matches(Regex("[A-Z]{3}")) && row.paymentMethod.matches(Regex("[a-z_]{1,32}")) && (row.buy || row.sell))
    require(row.currency == p2pCurrencyForCountry(row.country) && row.fiat == p2pFiatCode(row.currency))
    return row
}
internal fun parseP2pCatalog(json: JSONObject): List<P2pCountry> {
    verifyProtocol(json)
    val countries = json.getJSONArray("countries")
    require(countries.length() in 1..32)
    return (0 until countries.length()).map { country(countries.getJSONObject(it)) }.also { rows ->
        require(rows.map { it.country }.distinct().size == rows.size)
    }
}
internal fun parseP2pRate(json: JSONObject, expected: P2pCountry, now: Long = System.currentTimeMillis() / 1000): P2pRate {
    verifyProtocol(json)
    require(country(json) == expected && json.get("decimals").toString() == "6")
    return P2pRate(expected, json.getString("buyPrice"), json.getString("sellPrice"),
        json.getString("smallOrderThreshold"), json.getString("smallOrderFixedFeeBuy"), json.getString("smallOrderFixedFeeSell"),
        json.getString("block"), json.getLong("quotedAt"), json.getLong("expiresAt")).also { require(it.validAt(now)) }
}
