package org.astianx.midori.one.wallet

import java.math.BigDecimal
import java.math.BigInteger

/** A presentation account backed by distinct on-chain holdings, never an exchange rate. */
object DigitalDollarAccount {
    val assets: List<Asset> = listOf(Asset.USDC, Asset.USDT)

    fun nominalBalance(balances: Map<Asset, String>): BigDecimal? {
        var total = BigDecimal.ZERO
        for (asset in assets) {
            val units = units(balances[asset]) ?: return null
            total += BigDecimal(units).movePointLeft(asset.decimals)
        }
        return total
    }

    fun displayBalance(balances: Map<Asset, String>): String? = nominalBalance(balances)?.let {
        it.stripTrailingZeros().setScale(maxOf(2, it.stripTrailingZeros().scale())).toPlainString()
    }

    /** Destination asset is explicit: an external address does not identify its token. */
    fun funding(amount: BigInteger, destination: Asset, balances: Map<Asset, String>): DollarFunding {
        require(destination in assets && amount.signum() > 0)
        val destinationBalance = units(balances[destination]) ?: return DollarFunding.UNAVAILABLE
        if (destinationBalance >= amount) return DollarFunding.DIRECT
        val total = assets.fold(BigInteger.ZERO) { sum, asset ->
            sum + (units(balances[asset]) ?: return DollarFunding.UNAVAILABLE)
        }
        return if (total >= amount) DollarFunding.CONVERSION_REQUIRED else DollarFunding.INSUFFICIENT
    }

    private fun units(raw: String?): BigInteger? = raw?.takeIf { it.matches(Regex("[0-9]{1,78}")) }
        ?.toBigIntegerOrNull()?.takeIf { it.bitLength() <= 256 }
}

enum class DollarFunding { DIRECT, CONVERSION_REQUIRED, INSUFFICIENT, UNAVAILABLE }

data class SendReview(
    val asset: Asset,
    val address: String,
    val amount: String,
    val units: String,
    val estimatedFee: String,
    val expiresAt: Long
) {
    fun matches(asset: Asset, address: String, amount: String, now: Long): Boolean =
        now < expiresAt && this.asset == asset && this.address == address.trim() && this.amount == amount.trim()

    val feeDisplay: String get() = BigDecimal(estimatedFee).movePointLeft(
        if (asset.network == Network.BASE) 18 else 8
    ).stripTrailingZeros().toPlainString() + if (asset.network == Network.BASE) " ETH" else " BTC"
}
