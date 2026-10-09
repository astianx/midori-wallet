package org.astianx.midori.one.wallet.p2p

import org.astianx.midori.one.wallet.fiat.FiatHttpClient
import org.astianx.midori.one.wallet.fiat.FiatHttpFailure
import org.astianx.midori.one.wallet.fiat.FiatOperation
import org.json.JSONObject

internal class HttpP2pOrderGateway(url: String) : P2pOrderGateway {
    private val client = FiatHttpClient(url)
    private suspend fun request(path: String, body: JSONObject? = null): JSONObject = try {
        client.request("/v1/p2p/$path", body).also(P2pJson::protocol)
    } catch (e: FiatHttpFailure) { throw P2pOrderFailure(e.code) }
    private fun owner(user: String) = JSONObject().put("user", user)
    override suspend fun availability(): P2pAvailability {
        val j = request("operations"); val index = j.getJSONObject("index")
        return P2pAvailability(j.getBoolean("enabled"), j.getString("reason"),
            index.optString("indexedBlock").takeIf { it.isNotEmpty() }, index.optString("latestBlock").takeIf { it.isNotEmpty() })
    }
    override suspend fun quote(user: String, operation: FiatOperation, country: String, amount: String, relayPublicKey: String) =
        P2pJson.quote(request("quote", owner(user).put("operation", operation.name).put("country", country)
            .put("amountUnits", amount).put("relayPublicKey", relayPublicKey)))
    override suspend fun prepare(user: String, quoteId: String) = P2pJson.quote(request("prepare", owner(user).put("quoteId", quoteId)))
    override suspend fun order(user: String, orderId: String) = P2pJson.order(request("order", owner(user).put("orderId", orderId)).getJSONObject("order"))
    override suspend fun history(user: String): List<P2pOrder> {
        val rows = request("history", owner(user)).getJSONArray("orders"); require(rows.length() <= 10)
        return (0 until rows.length()).map { P2pJson.order(rows.getJSONObject(it)) }.also { orders ->
            require(orders.all { it.user.equals(user, true) })
        }
    }
    override suspend fun action(user: String, orderId: String, action: P2pAction, encryptedDestination: String?, evidenceId: String?): P2pPreparedAction {
        val j = request("action", owner(user).put("orderId", orderId).put("action", action.name)
            .apply { encryptedDestination?.let { put("encryptedDestination", it) }; evidenceId?.let { put("evidenceId", it) } })
        require(j.getString("value") == "0" && p2pInteger(j.getString("approvalUnits")))
        return P2pPreparedAction(j.getString("user"), j.getString("orderId"), P2pAction.valueOf(j.getString("action")),
            P2pJson.order(j.getJSONObject("order")), j.getString("to"), j.getString("data"), j.getString("approvalUnits"),
            j.getLong("quotedAt"), j.getLong("expiresAt"))
    }
    override suspend fun receipt(user: String, hash: String, to: String, data: String): P2pReceipt {
        val j = request("receipt", owner(user).put("hash", hash).put("to", to).put("data", data))
        val status = P2pSubmission.valueOf(j.getString("status"))
        require(status in setOf(P2pSubmission.PENDING, P2pSubmission.CONFIRMED, P2pSubmission.REVERTED))
        return P2pReceipt(j.getString("hash"), status, j.optString("orderId").takeIf { p2pInteger(it, true) })
    }
}
