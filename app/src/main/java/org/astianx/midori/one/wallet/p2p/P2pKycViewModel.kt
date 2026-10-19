// Copyright 2026 Midori. Licensed under the Apache License, Version 2.0.
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

internal data class P2pKycState(val owner: String, val country: String, val stage: String = "IDLE",
    val capabilities: P2pKycCapability? = null, val account: P2pAccount? = null, val expiresAt: Long? = null, val hash: String? = null,
    val review: P2pKycReview? = null, val launchUrl: String? = null, val shareUrl: String? = null, val busy: Boolean = false, val error: UiText? = null)

internal class P2pKycViewModel(owner: String, country: String, private val flow: P2pKycInteractor?) : ViewModel() {
    private val mutable = MutableStateFlow(P2pKycState(owner, country))
    val state = mutable.asStateFlow()
    private var accountVerified = false
    private fun task(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = kycError(e), review = null, launchUrl = null) } }
            finally {
                flow?.let { runCatching { it.local() }.onSuccess(::display)
                    .onFailure { mutable.update { s -> s.copy(error = UiText(R.string.p2po_storage)) } } }
                mutable.update { it.copy(busy = false) }
            }
        }
    }
    private fun display(r: P2pKycRecord?) {
        if (r != null) mutable.update { it.copy(country = r.country,
            stage = when {
                accountVerified && r.state !in setOf("SUBMITTING", "PENDING", "UNKNOWN", "CONFIRMED") -> "ALREADY_VERIFIED"
                r.session == null && r.proof == null && r.state in setOf("STARTING", "WAITING") -> "INTERRUPTED"
                else -> r.state
            }, shareUrl = it.shareUrl.takeIf { _ -> r.state == "WAITING" && r.session != null }, hash = r.hash,
            expiresAt = r.proof?.expiry?.toLongOrNull() ?: r.session?.expiresAt) }
        else mutable.update { it.copy(stage = if (accountVerified) "ALREADY_VERIFIED" else "IDLE") }
    }
    fun refresh() = task {
        val f = flow ?: throw P2pOrderFailure("verification_service_unavailable")
        display(f.local())
        val caps = f.capabilities(); mutable.update { it.copy(capabilities = caps, account = null) }
        accountVerified = f.verified()
        display(f.refresh())
        readAccount()
    }
    private suspend fun readAccount() {
        mutable.update { it.copy(account = null) }
        if (state.value.stage in setOf("CONFIRMED", "ALREADY_VERIFIED") && state.value.country.isNotBlank()) {
            val account = checkNotNull(flow).account(state.value.country); mutable.update { it.copy(account = account) }
        }
    }
    fun start() = task {
        val f = checkNotNull(flow); display(f.start(state.value.country)); val url = f.widget(); mutable.update { it.copy(launchUrl = url) }
    }
    fun reopen() = task { val url = checkNotNull(flow).widget(); mutable.update { it.copy(launchUrl = url) } }
    fun otherDevice() = task { val url = checkNotNull(flow).widget(); mutable.update { it.copy(shareUrl = url) } }
    fun hideShare() { mutable.update { it.copy(shareUrl = null) } }
    fun launched() { mutable.update { it.copy(launchUrl = null) } }
    fun launchFailed() { mutable.update { it.copy(launchUrl = null, error = UiText(R.string.p2pk_browser_error)) } }
    fun prepare() = task { val review = checkNotNull(flow).review(); mutable.update { it.copy(review = review) } }
    fun submit() = task {
        display(checkNotNull(flow).submit(checkNotNull(state.value.review))); mutable.update { it.copy(review = null) }
    }
    fun dismiss() { flow?.dismiss(); mutable.update { it.copy(review = null) } }
    fun recover(hash: String) = task { display(checkNotNull(flow).recover(hash.trim())); readAccount() }
    private fun kycError(e: Exception) = UiText(when ((e as? P2pOrderFailure)?.code) {
        "local_storage_failed" -> R.string.p2po_storage
        "insufficient_gas", "gas_unavailable" -> R.string.p2po_gas_error
        "expired_session", "quote_expired" -> R.string.p2pk_expired
        "submission_unknown" -> R.string.p2po_unknown
        "quote_changed" -> R.string.p2po_changed
        "verification_pending" -> R.string.p2pk_waiting
        "already_verified" -> R.string.p2pk_already_verified
        "invalid_verification", "invalid_protocol_response", "verification_signer_unrecognized", "receipt_mismatch", "wallet_changed" -> R.string.p2po_validation
        else -> R.string.p2pk_unavailable
    })
}
