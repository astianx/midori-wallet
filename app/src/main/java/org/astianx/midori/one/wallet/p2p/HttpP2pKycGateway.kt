// Copyright 2026 Midori. Licensed under the Apache License, Version 2.0.
package org.astianx.midori.one.wallet.p2p

import org.astianx.midori.one.wallet.fiat.FiatHttpClient
import org.astianx.midori.one.wallet.fiat.FiatHttpFailure
import org.json.JSONObject

internal class HttpP2pKycGateway(url: String) : P2pKycGateway {
    private val client = FiatHttpClient(url)
    private val orders = HttpP2pOrderGateway(url)
    override val origin = client.origin
    private suspend fun request(path: String, body: JSONObject? = null): JSONObject = try {
        client.request("/v1/p2p/verification/$path", body).also {
            p2pRequire(it.getInt("version") == 1 && it.getInt("chainId") == 8453 && it.getString("method") == "SIMPLE_KYC" &&
                it.getString("reputationManager").equals(P2P_REPUTATION_MANAGER, true))
        }
    } catch (e: FiatHttpFailure) { throw P2pOrderFailure(e.code) }
    private fun credentials(r: P2pKycRecord) = JSONObject().put("nonce", r.nonce).put("token", r.token)
    override suspend fun verified(user: String): Boolean {
        val j = request("account", JSONObject().put("user", user)); p2pRequire(j.getString("user").equals(user, true))
        return j.getBoolean("verified")
    }
    override suspend fun account(user: String, country: String) = orders.account(user, country)
    override suspend fun capabilities(): P2pKycCapability {
        val j = request("capabilities"); val rows = j.getJSONArray("countries"); p2pRequire(rows.length() <= 13)
        val countries = (0 until rows.length()).map { rows.getString(it).also { c -> p2pRequire(c.matches(Regex("[A-Z]{2}"))) } }
        return P2pKycCapability(j.getBoolean("enabled"), j.getString("reason"), countries)
    }
    override suspend fun session(record: P2pKycRecord, expiresAt: Long, signature: String): P2pKycSession {
        val j = request("session", credentials(record).put("user", record.owner).put("country", record.country)
            .put("expiresAt", expiresAt).put("signature", signature))
        return P2pKycSession(j.getString("nonce"), j.getString("user"), j.getString("country"),
            j.getString("widgetUrl"), j.getString("widgetOrigin"), j.getLong("expiresAt")).also(::validateKycWidget)
    }
    override suspend fun status(record: P2pKycRecord): P2pKycStatus {
        val j = request("status", credentials(record))
        return P2pKycStatus(j.getString("user"), j.getString("country"), j.getString("status"),
            j.optJSONObject("attestation")?.let(P2pKycJson::proof))
    }
    override suspend fun prepare(record: P2pKycRecord): P2pKycPrepared {
        val j = request("prepare", credentials(record)); p2pRequire(j.getString("value") == "0" && j.getString("user").equals(record.owner, true))
        return P2pKycPrepared(P2pKycJson.proof(j.getJSONObject("attestation")), j.getString("to"), j.getString("data"),
            j.getLong("quotedAt"), j.getLong("expiresAt"))
    }
    override suspend fun receipt(record: P2pKycRecord, hash: String): P2pReceipt {
        val j = request("receipt", JSONObject().put("hash", hash).put("attestation", P2pKycJson.proof(checkNotNull(record.proof))))
        val status = P2pSubmission.valueOf(j.getString("status"))
        p2pRequire(status in setOf(P2pSubmission.CONFIRMED, P2pSubmission.PENDING, P2pSubmission.UNKNOWN, P2pSubmission.REVERTED))
        return P2pReceipt(j.getString("hash"), status, null)
    }
}

internal object P2pKycJson {
    fun proof(p: P2pKycProof) = JSONObject().put("user", p.user).put("country", p.country).put("nullifier", p.nullifier)
        .put("limit", p.limit).put("expiry", p.expiry).put("signature", p.signature).put("signer", p.signer)
    fun proof(j: JSONObject) = P2pKycProof(j.getString("user"), j.getString("country"), j.getString("nullifier"),
        j.getString("limit"), j.getString("expiry"), j.getString("signature"), j.getString("signer"))
    fun record(r: P2pKycRecord) = JSONObject().put("owner", r.owner).put("country", r.country).put("nonce", r.nonce)
        .put("token", r.token).put("state", r.state).put("data", r.data).put("hash", r.hash).apply {
            r.proof?.let { put("proof", proof(it)) }
            r.session?.let { put("session", JSONObject().put("nonce", it.nonce).put("user", it.user).put("country", it.country)
                .put("widgetUrl", it.widgetUrl).put("widgetOrigin", it.widgetOrigin).put("expiresAt", it.expiresAt)) }
        }
    fun record(j: JSONObject): P2pKycRecord {
        val r = P2pKycRecord(j.getString("owner"), j.getString("country"), j.getString("nonce"), j.getString("token"), j.getString("state"),
            j.optJSONObject("session")?.let { P2pKycSession(it.getString("nonce"), it.getString("user"), it.getString("country"),
                it.getString("widgetUrl"), it.getString("widgetOrigin"), it.getLong("expiresAt")) },
            j.optJSONObject("proof")?.let(::proof), j.getString("data"), j.optString("hash").takeIf { it.isNotEmpty() && it != "null" })
        p2pRequire(r.owner.matches(Regex("0x[a-fA-F0-9]{40}")) && r.country.matches(Regex("[A-Z]{2}")) &&
            r.nonce.matches(Regex("[a-f0-9]{64}")) && r.token.matches(Regex("[a-f0-9]{64}")) && r.state in
            setOf("STARTING", "WAITING", "READY", "EXPIRED", "REJECTED", "CANCELLED", "DUPLICATE_IDENTITY", "EXCHANGE_UNKNOWN", "SUBMITTING", "PENDING", "UNKNOWN", "CONFIRMED", "REVERTED"))
        r.session?.let { validateKycWidget(it); p2pRequire(it.user.equals(r.owner, true) && it.country == r.country && it.nonce == r.nonce) }
        r.proof?.let { P2pKycEncoding.validate(it, r.owner, r.country, System.currentTimeMillis() / 1000, true) }
        p2pRequire(r.hash == null || r.hash.matches(Regex("0x[a-fA-F0-9]{64}")))
        return r
    }
}
