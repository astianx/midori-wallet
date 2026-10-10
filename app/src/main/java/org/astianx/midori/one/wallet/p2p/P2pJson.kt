package org.astianx.midori.one.wallet.p2p

import org.astianx.midori.one.wallet.BaseMainnet
import org.astianx.midori.one.wallet.fiat.FiatOperation
import org.json.JSONObject

/** Strict wire and vault codecs. Numeric token quantities must be strings, never floating point. */
internal object P2pJson {
    private fun JSONObject.s(key: String) = get(key).let { require(it is String); it }
    private fun JSONObject.u(key: String, positive: Boolean = false) = s(key).also { require(p2pInteger(it, positive)) }
    fun time(j: JSONObject, key: String): Long = j.get(key).let {
        require(it is Number && it.toString().matches(Regex("0|[1-9][0-9]{0,15}")))
        it.toString().toLong()
    }
    fun protocol(j: JSONObject) {
        require(j.get("version").toString() == "2" && j.get("chainId").toString() == "8453" && j.s("asset") == "USDC" &&
            j.s("token").equals(BaseMainnet.USDC_CONTRACT, true) && j.s("diamond").equals(P2P_DIAMOND, true) &&
            j.s("execution") == "LOCAL_WDK")
    }
    fun account(j: JSONObject) = P2pAccount(j.s("user"), j.s("country"), j.s("currency"), j.s("fiat"),
        j.u("usdcBalanceUnits"), j.u("buyLimitUnits"), j.u("sellLimitUnits"), j.u("block", true),
        time(j, "quotedAt"), time(j, "expiresAt"))
    fun order(j: JSONObject) = P2pOrder(j.u("orderId", true), j.s("user"), when (j.s("type")) {
        "buy" -> FiatOperation.BUY; "sell" -> FiatOperation.SELL; else -> error("Unsupported order")
    }, j.s("currency"), j.u("usdcAmount", true), j.u("fiatAmount"), j.u("actualUsdcAmount"),
        j.u("actualFiatAmount"), j.u("circleId"), j.s("status").also {
            require(it in setOf("placed", "accepted", "paid", "completed", "cancelled"))
        }, j.s("disputeStatus").also { require(it in setOf("none", "open", "resolved")) },
        j.s("pubkey"), j.s("encUpi"))
    fun order(o: P2pOrder) = JSONObject().put("orderId", o.id).put("user", o.user)
        .put("type", if (o.operation == FiatOperation.BUY) "buy" else "sell").put("currency", o.currency)
        .put("usdcAmount", o.amount).put("fiatAmount", o.fiat).put("actualUsdcAmount", o.actualUsdc)
        .put("actualFiatAmount", o.actualFiat).put("circleId", o.circle).put("status", o.status)
        .put("disputeStatus", o.dispute).put("pubkey", o.merchantKey).put("encUpi", o.encryptedPayment)
    fun quote(j: JSONObject) = P2pOrderQuote(j.s("id"), j.s("user"), FiatOperation.valueOf(j.s("operation")),
        j.s("country"), j.s("currency"), j.s("fiat"), j.u("amountUnits", true), j.u("totalUsdcUnits", true),
        j.u("fiatUnits", true), j.u("estimatedFiatTotalUnits", true), j.u("feeUsdcUnits"), j.u("priceUnits", true),
        j.s("relayPublicKey"), j.u("circleId", true), j.s("to"), j.s("data"), time(j, "quotedAt"), time(j, "expiresAt"))
    fun quote(q: P2pOrderQuote) = JSONObject().put("id", q.id).put("user", q.user).put("operation", q.operation.name)
        .put("country", q.country).put("currency", q.currency).put("fiat", q.fiat).put("amountUnits", q.amount)
        .put("totalUsdcUnits", q.totalUsdc).put("fiatUnits", q.fiatUnits).put("estimatedFiatTotalUnits", q.estimatedFiatTotal)
        .put("feeUsdcUnits", q.fee).put("priceUnits", q.price).put("relayPublicKey", q.relayPublicKey)
        .put("circleId", q.circle).put("to", q.to).put("data", q.data).put("quotedAt", q.quotedAt).put("expiresAt", q.expiresAt)
    fun record(j: JSONObject) = P2pPrivateRecord(j.s("localId"), j.s("owner"), j.s("country"),
        FiatOperation.valueOf(j.s("operation")), j.u("amount", true), j.s("relaySecret"), j.s("destination"),
        j.optString("orderId").takeIf { it.isNotEmpty() }, j.optJSONObject("quote")?.let(::quote),
        P2pAction.valueOf(j.s("action")), P2pSubmission.valueOf(j.s("submission")), j.s("to"), j.s("data"),
        j.optString("hash").takeIf { it.isNotEmpty() }, j.optJSONObject("order")?.let(::order))
    fun record(r: P2pPrivateRecord) = JSONObject().put("localId", r.localId).put("owner", r.owner)
        .put("country", r.country).put("operation", r.operation.name).put("amount", r.amount)
        .put("relaySecret", r.relaySecret).put("destination", r.destination).put("orderId", r.orderId ?: "")
        .put("quote", r.quote?.let(::quote)).put("action", r.action.name).put("submission", r.submission.name)
        .put("to", r.to).put("data", r.data).put("hash", r.hash ?: "").put("order", r.order?.let(::order))
}
