package org.astianx.midori.one.wallet.p2p

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.astianx.midori.one.R
import org.astianx.midori.one.UiText
import org.astianx.midori.one.wallet.fiat.FiatOperation
import org.astianx.midori.one.wallet.toSmallestUnit

internal data class P2pOrderState(val operation: FiatOperation, val countries: List<P2pCountry> = emptyList(),
    val country: String = "", val amount: String = "", val destination: String = "",
    val availability: P2pAvailability? = null, val account: P2pAccount? = null,
    val records: List<P2pPublicRecord> = emptyList(),
    val history: List<P2pOrder> = emptyList(), val review: P2pOrderReview? = null,
    val payment: Pair<String, String>? = null, val busy: Boolean = false, val error: UiText? = null,
    val locked: Boolean = false) {
    val available get() = countries.filter { if (operation == FiatOperation.BUY) it.buy && it.country != "IN" else it.sell }
}

/** Presentation only. The interactor owns signing intent, recovery and validation. */
internal class P2pOrderViewModel(operation: FiatOperation, private val catalog: P2pGateway?,
    private val orders: P2pOrderInteractor?, private val lockedCountry: String = "") : ViewModel() {
    private val mutable = MutableStateFlow(P2pOrderState(operation, country = lockedCountry, locked = lockedCountry.isNotBlank()))
    val state = mutable.asStateFlow()
    private var pendingEntry: FiatOperation? = null
    init { reload() }
    private fun task(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(review = null, payment = null, error = errorText(e)) } }
            finally {
                // Load local state even after a timeout; never hide an ambiguous broadcast.
                if (orders != null) runCatching { orders.localRecords() }.onSuccess { rows -> mutable.update { it.copy(records = rows) } }
                    .onFailure { mutable.update { it.copy(error = UiText(R.string.p2po_storage)) } }
                mutable.update { it.copy(busy = false) }
                pendingEntry?.let { desired ->
                    pendingEntry = null
                    if (state.value.operation != desired) { operation(desired); reload() }
                }
            }
        }
    }
    fun reload() = task {
        val interactor = orders ?: throw P2pOrderFailure("orders_disabled")
        interactor.dismissReview()
        val local = interactor.localRecords(); mutable.update { it.copy(records = local, review = null, payment = null) }
        val rows = catalog?.catalog() ?: emptyList()
        val availability = interactor.availability()
        mutable.update { it.copy(countries = rows, availability = availability) }
        loadAccount()
        val reconciled = interactor.reconcile(); mutable.update { it.copy(records = reconciled) }
        val history = interactor.history(); mutable.update { it.copy(history = history) }
    }
    /** Navigation may arrive during initial loading; do not discard its requested direction. */
    fun enter(value: FiatOperation) {
        if (state.value.busy) { pendingEntry = value; return }
        operation(value); reload()
    }
    private suspend fun loadAccount() {
        val selected = state.value.country
        mutable.update { it.copy(account = null) }
        if (selected.isBlank() || state.value.available.none { it.country == selected }) return
        val account = checkNotNull(orders).account(selected)
        mutable.update { it.copy(account = account) }
    }
    private fun edit(block: (P2pOrderState) -> P2pOrderState) {
        if (state.value.busy) return
        orders?.dismissReview(); mutable.update { block(it).copy(review = null, payment = null, error = null) }
    }
    fun operation(value: FiatOperation) {
        if (value != state.value.operation) edit { it.copy(operation = value, country = lockedCountry, destination = "", account = null) }
    }
    fun country(value: String) {
        if (state.value.locked || state.value.busy || state.value.available.none { it.country == value }) return
        edit { it.copy(country = value, destination = "", account = null) }
        task { loadAccount() }
    }
    fun amount(value: String) { if (value.length <= 40) edit { it.copy(amount = value) } }
    fun destination(value: String) { if (value.length <= 512) edit { it.copy(destination = value) } }
    fun quote() = task {
        val s = state.value
        val units = toSmallestUnit(s.amount.replace(',', '.'), 6) ?: throw P2pOrderFailure("invalid_amount")
        if (!p2pInteger(units, true)) throw P2pOrderFailure("invalid_amount")
        loadAccount()
        val account = checkNotNull(state.value.account)
        if (units.toBigInteger() > account.limit(s.operation).toBigInteger()) throw P2pOrderFailure("verification_required")
        if (s.operation == FiatOperation.SELL && units.toBigInteger() > account.usdcBalance.toBigInteger()) throw P2pOrderFailure("insufficient_usdc")
        val review = checkNotNull(orders).quote(s.operation, s.country, units, s.destination)
        mutable.update { it.copy(review = review) }
    }
    fun action(id: String, value: P2pAction, evidence: String? = null) = task {
        val review = checkNotNull(orders).action(id, value, evidence); mutable.update { it.copy(review = review, payment = null) }
    }
    fun dismiss() { if (!state.value.busy) { orders?.dismissReview(); mutable.update { it.copy(review = null, payment = null) } } }
    fun expire() { if (state.value.review?.expiresAt?.let { it <= System.currentTimeMillis() / 1000 } == true) dismiss() }
    fun submit(paymentConfirmed: Boolean) = task {
        val review = state.value.review ?: throw P2pOrderFailure("quote_expired")
        checkNotNull(orders).submit(review, paymentConfirmed)
        mutable.update { it.copy(review = null, payment = null, destination = "") }
    }
    fun refresh(id: String) = task { checkNotNull(orders).refresh(id); mutable.update { it.copy(payment = null) } }
    fun refreshPending() {
        if (state.value.busy || state.value.review != null || state.value.payment != null) return
        val row = state.value.records.firstOrNull {
            it.hash != null && (it.submission in setOf(P2pSubmission.PENDING, P2pSubmission.UNKNOWN, P2pSubmission.SUBMITTING) ||
                it.order?.status in setOf("placed", "accepted", "paid"))
        }
        if (row != null) refresh(row.localId)
        else if (state.value.availability?.enabled != true) reload()
        else if (state.value.account?.expiresAt?.let { it <= System.currentTimeMillis() / 1000 } == true) task { loadAccount() }
    }
    fun recover(id: String, hash: String) = task {
        checkNotNull(orders).recoverHash(id, hash.trim()); orders.refresh(id)
    }
    fun instructions(id: String) = task {
        val payment = checkNotNull(orders).paymentInstructions(id); mutable.update { it.copy(payment = id to payment) }
    }
    private fun errorText(e: Exception): UiText = UiText(when ((e as? P2pOrderFailure)?.code) {
        "invalid_amount" -> R.string.error_amount
        "verification_required" -> R.string.p2po_verification
        "no_counterparty" -> R.string.p2po_no_counterparty
        "insufficient_usdc" -> R.string.p2po_balance
        "insufficient_gas", "gas_unavailable" -> R.string.p2po_gas_error
        "quote_expired", "quote_changed", "order_changed" -> R.string.p2po_changed
        "local_storage_failed" -> R.string.p2po_storage
        "submission_unknown" -> R.string.p2po_unknown
        "order_already_active", "order_pending" -> R.string.p2po_active
        "payment_details_required" -> R.string.p2po_destination
        "approval_required" -> R.string.p2po_approval_required
        "order_not_eligible" -> R.string.p2po_not_eligible
        "receipt_mismatch", "invalid_protocol_response", "wallet_changed" -> R.string.p2po_validation
        else -> R.string.p2po_unavailable
    })
}
