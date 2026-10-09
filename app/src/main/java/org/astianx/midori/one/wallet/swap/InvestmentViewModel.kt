package org.astianx.midori.one.wallet.swap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.astianx.midori.one.R
import org.astianx.midori.one.UiText
import org.astianx.midori.one.wallet.Asset
import org.astianx.midori.one.wallet.DigitalDollarAccount
import org.astianx.midori.one.wallet.toSmallestUnit

data class InvestmentState(
    val source: Asset = Asset.USDC,
    val target: Asset = Asset.ETH,
    val amount: String = "",
    val loading: Boolean = false,
    val quote: InvestmentQuote? = null,
    val error: UiText? = null
)

class InvestmentViewModel internal constructor(private val gateway: InvestmentQuoteGateway) : ViewModel() {
    constructor() : this(HttpInvestmentQuoteGateway())
    private val mutable = MutableStateFlow(InvestmentState())
    val state = mutable.asStateFlow()
    private var revision = 0L

    private fun edit(change: InvestmentState.() -> InvestmentState) {
        revision++
        mutable.update { it.change().copy(quote = null, error = null, loading = false) }
    }
    fun source(asset: Asset) { require(asset in DigitalDollarAccount.assets); edit { copy(source = asset) } }
    fun target(asset: Asset) { require(asset == Asset.ETH || asset == Asset.BTC); edit { copy(target = asset) } }
    fun amount(value: String) = edit { copy(amount = value) }
    fun cancel() = edit { this }

    fun quote() {
        val s = mutable.value
        if (s.loading || s.target != Asset.ETH) return
        val input = s.amount.trim()
        val units = input.takeIf { it.matches(Regex("[0-9]{1,18}(\\.[0-9]{0,6})?")) }?.let { toSmallestUnit(it, 6) }
        if (units == null || units.length > 78 || units.toBigInteger().bitLength() > 256) {
            mutable.update { it.copy(error = UiText(R.string.error_amount)) }
            return
        }
        val request = InvestmentRequest(s.source, units)
        val requestRevision = ++revision
        mutable.update { it.copy(loading = true, quote = null, error = null) }
        viewModelScope.launch {
            try {
                val result = gateway.quote(request)
                check(result.request == request)
                if (requestRevision == revision) mutable.update { it.copy(quote = result) }
            } catch (_: Exception) {
                if (requestRevision == revision) mutable.update { it.copy(error = UiText(R.string.invest_quote_unavailable)) }
            } finally {
                if (requestRevision == revision) mutable.update { it.copy(loading = false) }
            }
        }
    }
}
