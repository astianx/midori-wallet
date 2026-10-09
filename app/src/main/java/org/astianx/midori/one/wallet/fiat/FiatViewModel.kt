package org.astianx.midori.one.wallet.fiat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.astianx.midori.one.R
import org.astianx.midori.one.UiText
import org.astianx.midori.one.wallet.Asset

internal data class FiatUiState(
    val operation: FiatOperation, val asset: Asset = Asset.USDC, val fiat: String = "USD",
    val amount: String = "", val capabilities: FiatCapabilities? = null, val busy: Boolean = false,
    val error: UiText? = null, val widgetUrl: String? = null, val configured: Boolean = true,
    val country: String = "", val offers: List<FiatOffer> = emptyList(), val selectedOfferId: String? = null
) {
    val assets get() = capabilities?.assets.orEmpty().filter { it.supports(operation) }.map { it.asset }
    val fiats get() = capabilities?.fiats.orEmpty().filter { it.supports(operation) }
    val amountUnits get() = fiatAmountUnits(amount,
        if (operation == FiatOperation.SELL) asset.decimals else fiats.find { it.code == fiat }?.decimals ?: 2)
    val countries get() = capabilities?.countries.orEmpty().filter { it.supports(operation) }
    val selection get() = amountUnits?.let { FiatSelection(operation, asset, fiat, it, country) }
    val canCompare get() = !busy && asset in assets && fiats.any { it.code == fiat } &&
        countries.any { it.code == country } && amountUnits != null && widgetUrl == null
    val selectedOffer get() = offers.firstOrNull { it.id == selectedOfferId && selection?.let(it::validFor) == true }
    val canContinue get() = canCompare && selectedOffer != null
    fun withoutOffers() = copy(offers = emptyList(), selectedOfferId = null, widgetUrl = null)
}

internal class FiatViewModel(
    operation: FiatOperation, private val gateway: FiatGateway?, private val identityAddress: String,
    private val bitcoinAddress: String, private val isCurrentWallet: () -> Boolean,
    private val sign: suspend (FiatRequest, FiatChallenge, String) -> String
) : ViewModel() {
    private val mutableState = MutableStateFlow(FiatUiState(operation, configured = gateway != null))
    val state = mutableState.asStateFlow()
    private var checkoutJob: Job? = null
    init { reload() }

    fun reload() {
        if (state.value.busy) return
        if (gateway == null) {
            mutableState.update { it.copy(error = UiText(R.string.fiat_not_enabled)) }
            return
        }
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                check(isCurrentWallet())
                val capabilities = checkNotNull(gateway).capabilities()
                check(isCurrentWallet())
                mutableState.update { selectAvailable(it.copy(capabilities = capabilities, busy = false)) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.update { it.copy(busy = false, error = UiText(R.string.fiat_unavailable)) } }
        }
    }
    private fun selectAvailable(state: FiatUiState): FiatUiState = state.copy(
        asset = state.asset.takeIf { it in state.assets } ?: state.assets.firstOrNull() ?: Asset.USDC,
        country = state.country.takeIf { code -> state.countries.any { it.code == code } } ?: "",
        fiat = state.fiat.takeIf { code -> state.fiats.any { it.code == code } } ?: state.fiats.firstOrNull()?.code ?: "USD")

    fun enter(operation: FiatOperation) {
        cancelCheckout()
        mutableState.update { selectAvailable(it.copy(operation = operation, amount = "", widgetUrl = null).withoutOffers()) }
        if (state.value.capabilities == null) reload()
    }

    fun operation(value: FiatOperation) { if (!state.value.busy) mutableState.update {
        selectAvailable(it.copy(operation = value, amount = "", error = null, widgetUrl = null).withoutOffers()) } }
    fun asset(value: Asset) { if (!state.value.busy && value in state.value.assets) mutableState.update {
        it.copy(asset = value, amount = "", error = null, widgetUrl = null).withoutOffers() } }
    fun fiat(value: String) { if (!state.value.busy && state.value.fiats.any { it.code == value }) mutableState.update {
        it.copy(fiat = value, amount = "", error = null, widgetUrl = null).withoutOffers() } }
    fun amount(value: String) { if (!state.value.busy && value.length <= 50) mutableState.update {
        it.copy(amount = value, error = null, widgetUrl = null).withoutOffers() } }

    fun country(value: String) { if (!state.value.busy && state.value.countries.any { it.code == value }) mutableState.update {
        it.copy(country = value, error = null).withoutOffers() } }
    fun chooseOffer(id: String) { if (!state.value.busy && state.value.offers.any { it.id == id }) {
        mutableState.update { it.copy(selectedOfferId = id, error = null) }
    } }
    fun expireOffers() { mutableState.update { current ->
        if (current.offers.any { it.expiresAt <= System.currentTimeMillis() / 1000 } && !current.busy)
            current.withoutOffers().copy(error = UiText(R.string.fiat_offer_expired)) else current
    } }
    fun compare() {
        val current = state.value
        if (!current.canCompare || gateway == null) return
        val selection = checkNotNull(current.selection)
        mutableState.update { it.withoutOffers().copy(busy = true, error = null) }
        checkoutJob = viewModelScope.launch {
            try {
                check(isCurrentWallet())
                val offers = gateway.offers(selection)
                check(isCurrentWallet() && offers.all { it.validFor(selection) } && offers.map { it.id }.distinct().size == offers.size)
                mutableState.update { it.copy(busy = false, offers = offers, selectedOfferId = offers.firstOrNull()?.id,
                    error = if (offers.isEmpty()) UiText(R.string.fiat_no_offers) else null) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.update { it.copy(busy = false, error = UiText(R.string.fiat_offers_failed)) } }
        }
    }

    fun checkout() {
        val selection = state.value
        if (!selection.canContinue || gateway == null) return
        val offer = checkNotNull(selection.selectedOffer)
        mutableState.update { it.copy(busy = true, error = null) }
        checkoutJob = viewModelScope.launch {
            try {
                check(isCurrentWallet())
                val request = FiatRequest(selection.operation, selection.asset, selection.fiat,
                    checkNotNull(selection.amountUnits), if (selection.asset == Asset.BTC) bitcoinAddress else identityAddress,
                    identityAddress.lowercase(), selection.country, offer.provider, offer.id)
                val challenge = gateway.challenge(request)
                fiatChallengeMessage(gateway.origin, challenge, request)
                check(isCurrentWallet() && offer.validFor(checkNotNull(selection.selection)))
                val signature = sign(request, challenge, gateway.origin)
                check(isCurrentWallet())
                val url = validateFiatCheckoutUrl(gateway.session(challenge.nonce, signature), gateway.origin)
                check(isCurrentWallet())
                mutableState.update { it.copy(busy = false, widgetUrl = url) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.update { it.copy(busy = false, error = UiText(R.string.fiat_checkout_failed)) } }
        }
    }
    fun consumeWidget() { mutableState.update { it.copy(widgetUrl = null) } }
    fun browserFailed() { mutableState.update { it.copy(error = UiText(R.string.fiat_browser_failed)) } }
    fun cancelCheckout() {
        if (checkoutJob != null) {
            checkoutJob?.cancel()
            checkoutJob = null
            mutableState.update { it.copy(busy = false, widgetUrl = null) }
        }
    }
}
