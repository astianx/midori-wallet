package org.astianx.midori.one.wallet.p2p

import java.math.BigInteger
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.astianx.midori.one.wallet.fiat.FiatOperation

internal enum class P2pAction { PLACE, APPROVE, HANDOFF, PAID, CANCEL, DISPUTE }
internal enum class P2pSubmission { DRAFT, SUBMITTING, PENDING, UNKNOWN, CONFIRMED, REVERTED }
internal data class P2pAvailability(val enabled: Boolean, val reason: String,
    val indexedBlock: String? = null, val latestBlock: String? = null)
internal data class P2pOrder(val id: String, val user: String, val operation: FiatOperation,
    val currency: String, val amount: String, val fiat: String, val actualUsdc: String,
    val actualFiat: String, val circle: String, val status: String, val dispute: String,
    val merchantKey: String, val encryptedPayment: String)
internal data class P2pOrderQuote(val id: String, val user: String, val operation: FiatOperation,
    val country: String, val currency: String, val fiat: String, val amount: String, val totalUsdc: String,
    val fiatUnits: String, val estimatedFiatTotal: String, val fee: String, val price: String,
    val relayPublicKey: String, val circle: String, val to: String, val data: String,
    val quotedAt: Long, val expiresAt: Long)
internal data class P2pPreparedAction(val user: String, val orderId: String, val action: P2pAction,
    val order: P2pOrder, val to: String, val data: String, val approval: String,
    val quotedAt: Long, val expiresAt: Long)
internal data class P2pReceipt(val hash: String, val status: P2pSubmission, val orderId: String?)

/** Never expose this record to Compose, logs, theme exports, backup or the provider. */
internal data class P2pPrivateRecord(val localId: String, val owner: String, val country: String,
    val operation: FiatOperation, val amount: String, val relaySecret: String,
    val destination: String = "", val orderId: String? = null, val quote: P2pOrderQuote? = null,
    val action: P2pAction = P2pAction.PLACE, val submission: P2pSubmission = P2pSubmission.DRAFT,
    val to: String = "", val data: String = "", val hash: String? = null,
    val order: P2pOrder? = null) {
    override fun toString() = "P2pPrivateRecord(redacted)"
}
internal data class P2pOrderReview(val localId: String, val action: P2pAction, val to: String,
    val data: String, val gas: String, val expiresAt: Long, val quote: P2pOrderQuote? = null,
    val prepared: P2pPreparedAction? = null, val encryptedDestination: String? = null,
    val evidenceId: String? = null, val paymentDestination: String? = null) {
    override fun toString() = "P2pOrderReview(redacted)"
}
internal data class P2pPublicRecord(val localId: String, val operation: FiatOperation, val country: String,
    val amount: String, val orderId: String?, val submission: P2pSubmission, val action: P2pAction,
    val hash: String?, val order: P2pOrder?)

internal interface P2pOrderGateway {
    suspend fun availability(): P2pAvailability
    suspend fun quote(user: String, operation: FiatOperation, country: String, amount: String, relayPublicKey: String): P2pOrderQuote
    suspend fun prepare(user: String, quoteId: String): P2pOrderQuote
    suspend fun order(user: String, orderId: String): P2pOrder
    suspend fun history(user: String): List<P2pOrder>
    suspend fun action(user: String, orderId: String, action: P2pAction,
        encryptedDestination: String? = null, evidenceId: String? = null): P2pPreparedAction
    suspend fun receipt(user: String, hash: String, to: String, data: String): P2pReceipt
}
internal interface P2pOrderStore {
    suspend fun load(owner: String): List<P2pPrivateRecord>
    suspend fun save(owner: String, records: List<P2pPrivateRecord>)
}
internal interface P2pRelayCrypto {
    fun createSecret(): String
    fun publicKey(secret: String): String
    fun encrypt(secret: String, recipientPublicKey: String, message: String): String
    fun decrypt(secret: String, encrypted: String): String
}
internal interface P2pWalletPort {
    fun isCurrent(owner: String): Boolean
    suspend fun quote(owner: String, to: String, data: String, requiredUsdc: String): String
    suspend fun submit(owner: String, to: String, data: String, maxGas: String, requiredUsdc: String,
        beforeBroadcast: suspend () -> Unit): String
}
internal class P2pOrderFailure(val code: String) : Exception(code)
internal fun p2pRequire(value: Boolean, code: String = "invalid_protocol_response") {
    if (!value) throw P2pOrderFailure(code)
}
internal fun p2pTimeValid(quotedAt: Long, expiresAt: Long, now: Long) =
    quotedAt in (now - 60)..(now + 5) && expiresAt - quotedAt == 60L && expiresAt > now

/** Order lifecycle independent of Android, Compose, HTTP, storage and WDK implementations. */
internal class P2pOrderInteractor(private val owner: String, private val gateway: P2pOrderGateway,
    private val store: P2pOrderStore, private val crypto: P2pRelayCrypto,
    private val wallet: P2pWalletPort, private val clock: () -> Long = { System.currentTimeMillis() / 1000 }) {
    private val mutex = Mutex()
    private var records: List<P2pPrivateRecord>? = null
    private var review: P2pOrderReview? = null
    private fun current() = p2pRequire(wallet.isCurrent(owner), "wallet_changed")
    private suspend fun read(): List<P2pPrivateRecord> {
        current()
        if (records == null) records = store.load(owner).also { rows ->
            p2pRequire(rows.size <= 200 && rows.map { it.localId }.distinct().size == rows.size, "local_storage_failed")
            rows.forEach { p2pRequire(it.owner.equals(owner, true), "local_storage_failed") }
        }
        return checkNotNull(records)
    }
    private suspend fun commit(row: P2pPrivateRecord) {
        current()
        val next = read().filterNot { it.localId == row.localId } + row
        p2pRequire(next.size <= 200, "local_storage_failed")
        store.save(owner, next) // Confirm durable intent/state before wallet IO.
        records = next
    }
    private fun P2pPrivateRecord.public() = P2pPublicRecord(localId, operation, country, amount, orderId, submission, action, hash, order)
    suspend fun localRecords(): List<P2pPublicRecord> = mutex.withLock { read().filter { it.submission != P2pSubmission.DRAFT }.map { it.public() }.reversed() }
    suspend fun availability() = gateway.availability()
    suspend fun history(): List<P2pOrder> { current(); return gateway.history(owner).also { current() } }
    private fun validate(quote: P2pOrderQuote, record: P2pPrivateRecord) {
        p2pRequire(quote.id.matches(Regex("[a-f0-9]{64}")) && quote.currency == p2pCurrencyForCountry(record.country) &&
            quote.fiat == p2pFiatCode(quote.currency) && p2pInteger(quote.circle, true))
        p2pRequire(quote.user.equals(owner, true) && quote.operation == record.operation && quote.country == record.country &&
            quote.amount == record.amount && quote.relayPublicKey == crypto.publicKey(record.relaySecret) &&
            p2pTimeValid(quote.quotedAt, quote.expiresAt, clock()))
        p2pRequire(quote.to.equals(P2P_DIAMOND, true) && quote.data.equals(P2pAbi.place(quote), true))
    }
    suspend fun quote(operation: FiatOperation, country: String, amount: String, destination: String): P2pOrderReview = mutex.withLock {
        current(); p2pRequire(p2pInteger(amount, true), "invalid_amount")
        p2pRequire(operation != FiatOperation.BUY || country != "IN", "verification_required")
        p2pRequire(destination.length <= 512 && (operation == FiatOperation.BUY || P2pPaymentDetails.valid(country, destination)), "payment_details_required")
        val rows = read()
        p2pRequire(rows.none { it.submission in setOf(P2pSubmission.SUBMITTING, P2pSubmission.PENDING, P2pSubmission.UNKNOWN) ||
            it.orderId != null && it.order?.status !in setOf("completed", "cancelled") }, "order_already_active")
        val draft = P2pPrivateRecord(UUID.randomUUID().toString(), owner, country, operation, amount, crypto.createSecret(), destination)
        // Discard only never-broadcast drafts. A corrupt vault is never replaced by an empty one.
        val retained = rows.filterNot { it.submission == P2pSubmission.DRAFT }
        store.save(owner, retained + draft); records = retained + draft
        val quote = gateway.quote(owner, operation, country, amount, crypto.publicKey(draft.relaySecret))
        current(); validate(quote, draft)
        val gas = wallet.quote(owner, quote.to, quote.data, if (operation == FiatOperation.SELL) quote.totalUsdc else "0")
        val row = draft.copy(quote = quote); commit(row)
        P2pOrderReview(row.localId, P2pAction.PLACE, quote.to, quote.data, gas, quote.expiresAt, quote,
            paymentDestination = row.destination.takeIf { operation == FiatOperation.SELL }).also { review = it }
    }
    suspend fun action(localId: String, action: P2pAction, evidenceId: String? = null): P2pOrderReview = mutex.withLock {
        current(); p2pRequire(action != P2pAction.PLACE)
        val row = read().single { it.localId == localId }
        p2pRequire(row.orderId != null && row.submission !in setOf(P2pSubmission.PENDING, P2pSubmission.UNKNOWN, P2pSubmission.SUBMITTING), "order_pending")
        val order = gateway.order(owner, checkNotNull(row.orderId)); validateOrder(order, row)
        val encrypted = if (action == P2pAction.HANDOFF) {
            p2pRequire(order.status == "accepted" && row.operation == FiatOperation.SELL && row.destination.isNotBlank())
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { crypto.encrypt(row.relaySecret, order.merchantKey, row.destination) }
        } else null
        if (action == P2pAction.DISPUTE) p2pRequire(evidenceId != null && p2pInteger(evidenceId, true), "invalid_evidence")
        val prepared = gateway.action(owner, order.id, action, encrypted, evidenceId)
        p2pRequire(prepared.user.equals(owner, true) && prepared.orderId == order.id && prepared.action == action &&
            prepared.order == order && p2pTimeValid(prepared.quotedAt, prepared.expiresAt, clock()), "order_changed")
        val expected = P2pAbi.action(action, order.id, prepared.approval, encrypted, evidenceId)
        p2pRequire(prepared.to.equals(expected.first, true) && prepared.data.equals(expected.second, true))
        if (action in setOf(P2pAction.APPROVE, P2pAction.HANDOFF)) {
            p2pRequire(prepared.approval == order.actualUsdc && p2pInteger(order.actualUsdc, true) &&
                order.actualUsdc.toBigInteger() >= order.amount.toBigInteger())
        }
        val gas = wallet.quote(owner, prepared.to, prepared.data,
            if (action == P2pAction.HANDOFF) prepared.approval else "0")
        commit(row.copy(order = order))
        P2pOrderReview(localId, action, prepared.to, prepared.data, gas, prepared.expiresAt,
            prepared = prepared, encryptedDestination = encrypted, evidenceId = evidenceId,
            paymentDestination = row.destination.takeIf { action == P2pAction.HANDOFF }).also { review = it }
    }
    fun dismissReview() { review = null }
    suspend fun submit(candidate: P2pOrderReview, paymentConfirmed: Boolean = false): P2pPublicRecord = mutex.withLock {
        current(); p2pRequire(review == candidate && candidate.expiresAt > clock(), "quote_expired")
        p2pRequire(candidate.action != P2pAction.PAID || paymentConfirmed, "payment_confirmation_required")
        var row = read().single { it.localId == candidate.localId }
        p2pRequire(row.submission !in setOf(P2pSubmission.SUBMITTING, P2pSubmission.PENDING, P2pSubmission.UNKNOWN), "order_pending")
        var required = "0"
        if (candidate.action == P2pAction.PLACE) {
            val bound = checkNotNull(candidate.quote)
            val fresh = gateway.prepare(owner, bound.id)
            validate(fresh, row); p2pRequire(fresh == bound, "quote_changed")
            if (row.operation == FiatOperation.SELL) required = bound.totalUsdc
        } else {
            val prior = checkNotNull(candidate.prepared)
            val fresh = gateway.action(owner, prior.orderId, candidate.action, candidate.encryptedDestination, candidate.evidenceId)
            p2pRequire(fresh.to.equals(candidate.to, true) && fresh.data.equals(candidate.data, true) &&
                fresh.order == prior.order && fresh.approval == prior.approval, "order_changed")
            if (candidate.action == P2pAction.HANDOFF) required = prior.approval
        }
        current(); p2pRequire(candidate.expiresAt > clock(), "quote_expired")
        var started = false
        try {
            val hash = wallet.submit(owner, candidate.to, candidate.data, candidate.gas, required) {
                current(); p2pRequire(candidate.expiresAt > clock(), "quote_expired")
                row = row.copy(action = candidate.action, submission = P2pSubmission.SUBMITTING,
                    to = candidate.to, data = candidate.data, hash = null)
                commit(row); review = null; started = true
            }
            p2pRequire(started && hash.matches(Regex("0x[0-9a-fA-F]{64}")), "invalid_transaction_hash")
            row = row.copy(hash = hash, submission = P2pSubmission.PENDING); commit(row)
        } catch (error: Exception) {
            if (!started) throw error
            // Hash may be lost after a successful broadcast. Never automatically repeat it.
            row = row.copy(submission = P2pSubmission.UNKNOWN)
            runCatching { commit(row) }
            throw P2pOrderFailure("submission_unknown")
        }
        row.public()
    }
    private fun validateOrder(order: P2pOrder, row: P2pPrivateRecord) {
        p2pRequire(order.id == row.orderId && order.user.equals(owner, true) && order.operation == row.operation &&
            order.amount == row.amount && row.quote?.currency?.let { it == order.currency } != false)
    }
    suspend fun refresh(localId: String): P2pPublicRecord = mutex.withLock {
        current(); var row = read().single { it.localId == localId }
        if (row.submission in setOf(P2pSubmission.SUBMITTING, P2pSubmission.PENDING, P2pSubmission.UNKNOWN) && row.hash != null) {
            val receipt = gateway.receipt(owner, checkNotNull(row.hash), row.to, row.data)
            p2pRequire(receipt.hash.equals(row.hash, true))
            if (receipt.status in setOf(P2pSubmission.CONFIRMED, P2pSubmission.REVERTED)) {
                if (row.action == P2pAction.PLACE && receipt.status == P2pSubmission.CONFIRMED) p2pRequire(receipt.orderId != null)
                row = row.copy(submission = receipt.status,
                    orderId = if (row.action == P2pAction.PLACE) receipt.orderId else row.orderId)
                commit(row)
            }
        }
        row.orderId?.let { id ->
            val order = gateway.order(owner, id); validateOrder(order, row)
            row = row.copy(order = order); commit(row)
        }
        row.public()
    }
    suspend fun recoverHash(localId: String, hash: String): P2pPublicRecord = mutex.withLock {
        current(); val row = read().single { it.localId == localId }
        p2pRequire(row.submission in setOf(P2pSubmission.UNKNOWN, P2pSubmission.SUBMITTING) && row.hash == null &&
            hash.matches(Regex("0x[0-9a-fA-F]{64}")), "order_pending")
        val receipt = gateway.receipt(owner, hash, row.to, row.data)
        p2pRequire(receipt.hash.equals(hash, true))
        p2pRequire(receipt.status != P2pSubmission.PENDING, "order_pending")
        commit(row.copy(hash = hash, submission = P2pSubmission.PENDING))
        checkNotNull(records).single { it.localId == localId }.public()
    }
    suspend fun paymentInstructions(localId: String): String = mutex.withLock {
        current(); val row = read().single { it.localId == localId }
        p2pRequire(row.operation == FiatOperation.BUY && row.orderId != null, "order_changed")
        val order = gateway.order(owner, checkNotNull(row.orderId)); validateOrder(order, row)
        p2pRequire(order.status in setOf("accepted", "paid") && order.encryptedPayment.isNotBlank(), "order_pending")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { crypto.decrypt(row.relaySecret, order.encryptedPayment) }.also {
            p2pRequire(it.isNotBlank() && it.length <= 4096)
            commit(row.copy(order = order))
        }
    }
}
