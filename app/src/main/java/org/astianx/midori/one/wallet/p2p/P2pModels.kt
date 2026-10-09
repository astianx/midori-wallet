package org.astianx.midori.one.wallet.p2p

import java.math.BigDecimal
import java.math.BigInteger

internal const val P2P_DIAMOND = "0x4cad6ec90e65babec9335cad728ddc610c316368"
internal data class P2pCountry(val country: String, val fiat: String, val currency: String,
    val paymentMethod: String, val alpha: Boolean, val buy: Boolean, val sell: Boolean)
internal data class P2pRate(val country: P2pCountry, val buyPrice: String, val sellPrice: String,
    val smallOrderThreshold: String, val smallOrderFixedFeeBuy: String, val smallOrderFixedFeeSell: String,
    val block: String, val quotedAt: Long, val expiresAt: Long) {
    fun validAt(now: Long) = quotedAt in (now - 60)..(now + 5) && expiresAt - now in 1..65 && expiresAt - quotedAt == 60L &&
        listOf(buyPrice, sellPrice, block).all { p2pInteger(it, positive = true) } &&
        listOf(smallOrderThreshold, smallOrderFixedFeeBuy, smallOrderFixedFeeSell).all { p2pInteger(it) }
}
internal fun p2pInteger(value: String, positive: Boolean = false) = value.matches(Regex("0|[1-9][0-9]{0,77}")) &&
    value.toBigInteger().bitLength() <= 256 && (!positive || value.toBigInteger() > BigInteger.ZERO)
internal fun p2pDecimal(units: String) = BigDecimal(units).movePointLeft(6).stripTrailingZeros().toPlainString()

internal interface P2pGateway {
    suspend fun catalog(): List<P2pCountry>
    suspend fun rates(country: P2pCountry): P2pRate
}

internal fun p2pFiatCode(currency: String) = when(currency) { "VEN" -> "VES"; "ECU" -> "USD"; "MEX" -> "MXN"; else -> currency }
internal fun p2pCurrencyForCountry(country: String): String? = when(country) {
    "IN" -> "INR"; "ID" -> "IDR"; "BR" -> "BRL"; "AR" -> "ARS"; "MX" -> "MEX"
    "VE" -> "VEN"; "BO" -> "BOB"; "NG" -> "NGN"; "CO" -> "COP"; "CU" -> "CUP"
    "EC" -> "ECU"; "PE" -> "PEN"; "PH" -> "PHP"; "KE" -> "KES"; "EG" -> "EGP"; else -> null
}
