package org.astianx.midori.one.wallet.p2p

import kotlinx.coroutines.CompletableDeferred
import org.astianx.midori.one.wallet.fiat.FiatOperation

/** Isolated public fixtures shared by JVM and Pixel tests. Never reads a wallet or uses HTTP. */
internal class P2pOrderFixture {
    val owner = "0x" + "1".repeat(40)
    val countries = listOf(P2pCountry("CO", "COP", "COP", "bank_transfer", true, true, true),
        P2pCountry("BR", "BRL", "BRL", "pix", false, true, true),
        P2pCountry("IN", "INR", "INR", "upi", false, true, true))
    var ready = true
    var buyLimit = "100000000"
    var balance = "100000000"
    var emissions = 0
    var identities = 0
    var loseHash = false
    var catalogGate: CompletableDeferred<Unit>? = null
    var accountFailure = false
    var boundQuote: P2pOrderQuote? = null
    var order: P2pOrder? = null
    private fun now() = System.currentTimeMillis() / 1000
    class Store : P2pOrderStore {
        var rows = emptyList<P2pPrivateRecord>()
        override suspend fun load(owner: String) = rows
        override suspend fun save(owner: String, records: List<P2pPrivateRecord>) { rows = records.toList() }
    }
    val store = Store()
    val catalog = object : P2pGateway {
        override suspend fun catalog(): List<P2pCountry> { catalogGate?.await(); return countries }
        override suspend fun rates(country: P2pCountry): P2pRate = error("No public rate requested by order fixtures")
    }
    val gateway = object : P2pOrderGateway {
        override suspend fun availability() = P2pAvailability(ready, if (ready) "ready" else "index_syncing")
        override suspend fun account(user: String, country: String): P2pAccount {
            if (accountFailure) throw P2pOrderFailure("provider_unavailable")
            val c = countries.single { it.country == country }; val time = now()
            return P2pAccount(user, c.country, c.currency, c.fiat, balance, buyLimit, "100000000", "20", time, time + 60)
        }
        override suspend fun quote(user: String, operation: FiatOperation, country: String, amount: String, relayPublicKey: String): P2pOrderQuote {
            val c = countries.single { it.country == country }; val time = now()
            val fiat = (amount.toBigInteger() * 3000.toBigInteger()).toString()
            val q = P2pOrderQuote("a".repeat(64), user, operation, country, c.currency, c.fiat,
                amount, amount, fiat, fiat, "0", "3000000000", relayPublicKey, "4", P2P_DIAMOND, "", time, time + 60)
                .let { it.copy(data = P2pAbi.place(it)) }
            boundQuote = q
            order = P2pOrder("7", owner, operation, c.currency, amount, fiat, amount, fiat, "4",
                "accepted", "none", relayPublicKey, "SYNTHETIC_CIPHER")
            return q
        }
        override suspend fun prepare(user: String, quoteId: String) = checkNotNull(boundQuote)
        override suspend fun order(user: String, orderId: String) = checkNotNull(order)
        override suspend fun history(user: String) = store.rows.filter { it.orderId != null }.mapNotNull { it.order }
        override suspend fun action(user: String, orderId: String, action: P2pAction, encryptedDestination: String?, evidenceId: String?): P2pPreparedAction {
            val o = checkNotNull(order); val time = now()
            val amount = if (action in setOf(P2pAction.APPROVE, P2pAction.HANDOFF)) o.actualUsdc else "0"
            val (to, data) = P2pAbi.action(action, orderId, amount, encryptedDestination, evidenceId)
            return P2pPreparedAction(user, orderId, action, o, to, data, amount, time, time + 60)
        }
        override suspend fun receipt(user: String, hash: String, to: String, data: String) =
            P2pReceipt(hash, P2pSubmission.CONFIRMED, if (data == boundQuote?.data) "7" else null)
    }
    private val crypto = object : P2pRelayCrypto {
        override fun createSecret(): String { identities++; return "1".repeat(64) }
        override fun publicKey(secret: String) = "a".repeat(128)
        override fun encrypt(secret: String, recipientPublicKey: String, message: String) = "ab".repeat(128)
        override fun decrypt(secret: String, encrypted: String) = "Fixture bank · 123456"
    }
    private val wallet = object : P2pWalletPort {
        override fun isCurrent(owner: String) = owner == this@P2pOrderFixture.owner
        override suspend fun quote(owner: String, to: String, data: String, requiredUsdc: String) = "1000"
        override suspend fun submit(owner: String, to: String, data: String, maxGas: String, requiredUsdc: String, beforeBroadcast: suspend () -> Unit): String {
            beforeBroadcast()
            check(store.rows.last().submission == P2pSubmission.SUBMITTING)
            emissions++
            if (loseHash) throw P2pOrderFailure("provider_unavailable")
            val action = store.rows.last().action
            if (action in setOf(P2pAction.PAID, P2pAction.HANDOFF)) order = order?.copy(status = "paid")
            if (action == P2pAction.CANCEL) order = order?.copy(status = "cancelled")
            return "0x" + emissions.toString(16).padStart(64, '0')
        }
    }
    fun interactor() = P2pOrderInteractor(owner, gateway, store, crypto, wallet)
    fun viewModel(operation: FiatOperation = FiatOperation.BUY) = P2pOrderViewModel(operation, catalog, interactor())
}
