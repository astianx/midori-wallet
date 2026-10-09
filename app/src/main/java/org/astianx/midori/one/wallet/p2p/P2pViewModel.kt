package org.astianx.midori.one.wallet.p2p

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
import org.astianx.midori.one.wallet.fiat.FiatOperation

internal data class P2pState(val operation: FiatOperation, val countries: List<P2pCountry> = emptyList(),
    val country: String = "", val rate: P2pRate? = null, val busy: Boolean = false, val error: UiText? = null,
    val configured: Boolean = true) {
    val available get() = countries.filter { if (operation == FiatOperation.BUY) it.buy else it.sell }
}

internal class P2pViewModel(operation: FiatOperation, private val gateway: P2pGateway?) : ViewModel() {
    private val mutable = MutableStateFlow(P2pState(operation, configured = gateway != null))
    val state = mutable.asStateFlow()
    private var request: Job? = null
    private var revision = 0L
    init { reload() }

    fun cancel() { revision++; request?.cancel(); mutable.update { it.copy(busy = false, rate = null) } }
    fun operation(value: FiatOperation) {
        cancel()
        mutable.update { it.copy(operation = value, country = "", error = null) }
        if (state.value.countries.isEmpty()) reload()
    }
    fun country(value: String) {
        if (state.value.available.none { it.country == value }) return
        cancel(); mutable.update { it.copy(country = value, error = null) }
    }
    fun expire() { mutable.update { if (it.rate?.validAt(System.currentTimeMillis() / 1000) == false)
        it.copy(rate = null, error = UiText(R.string.p2p_expired)) else it } }
    fun reload() {
        if (state.value.busy) return
        if (gateway == null) { mutable.update { it.copy(error = UiText(R.string.fiat_not_enabled)) }; return }
        val current = ++revision
        mutable.update { it.copy(busy = true, error = null, rate = null) }
        request = viewModelScope.launch {
            try {
                val rows = gateway.catalog()
                check(rows.map { it.country }.distinct().size == rows.size)
                if (current == revision) mutable.update { it.copy(countries = rows, busy = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (current == revision) mutable.update { it.copy(busy = false, error = UiText(R.string.p2p_unavailable)) } }
        }
    }
    fun rates() {
        if (state.value.busy || gateway == null) return
        val selection = state.value.available.find { it.country == state.value.country } ?: return
        val current = ++revision
        mutable.update { it.copy(busy = true, error = null, rate = null) }
        request = viewModelScope.launch {
            try {
                val rate = gateway.rates(selection)
                check(rate.country == selection && rate.validAt(System.currentTimeMillis() / 1000))
                if (current == revision) mutable.update { it.copy(rate = rate, busy = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (current == revision) mutable.update { it.copy(busy = false, error = UiText(R.string.p2p_unavailable)) } }
        }
    }
}
