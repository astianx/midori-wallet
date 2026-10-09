package org.astianx.midori.one.wallet.fiat

import java.math.BigDecimal
import java.net.URI
import org.astianx.midori.one.wallet.Asset

enum class FiatOperation { BUY, SELL }
internal data class FiatAsset(val asset: Asset, val buy: Boolean, val sell: Boolean) {
    fun supports(operation: FiatOperation) = if (operation == FiatOperation.BUY) buy else sell
}
internal data class FiatCurrency(val code: String, val decimals: Int, val buy: Boolean, val sell: Boolean) {
    fun supports(operation: FiatOperation) = if (operation == FiatOperation.BUY) buy else sell
}
internal data class FiatCountry(val code: String, val buy: Boolean, val sell: Boolean) {
    fun supports(operation: FiatOperation) = if (operation == FiatOperation.BUY) buy else sell
}
internal data class FiatCapabilities(val assets: List<FiatAsset>, val fiats: List<FiatCurrency>,
    val countries: List<FiatCountry> = emptyList())
internal data class FiatSelection(val operation: FiatOperation, val asset: Asset, val fiat: String,
    val amountUnits: String, val country: String)
internal data class FiatOffer(val id: String, val provider: String, val providerName: String,
    val selection: FiatSelection, val fiatAmountUnits: String, val cryptoAmountUnits: String,
    val feeUnits: String, val paymentMethod: String, val expiresAt: Long) {
    fun validFor(selection: FiatSelection, now: Long = System.currentTimeMillis() / 1000) =
        this.selection == selection && expiresAt > now && expiresAt - now <= 90 &&
        id.matches(Regex("[a-f0-9]{64}")) && provider.matches(Regex("[a-z][a-z0-9_-]{0,39}")) &&
        providerName.isNotBlank() && providerName.length <= 80 && providerName.none { it.isISOControl() } &&
        fiatAmountUnits.matches(Regex("[1-9][0-9]{0,29}")) && cryptoAmountUnits.matches(Regex("[1-9][0-9]{0,29}")) &&
        feeUnits.matches(Regex("0|[1-9][0-9]{0,29}")) && paymentMethod.matches(Regex("[a-z0-9_-]{1,64}")) &&
        (if (selection.operation == FiatOperation.BUY) fiatAmountUnits else cryptoAmountUnits) == selection.amountUnits
}
internal data class FiatRequest(val operation: FiatOperation, val asset: Asset, val fiat: String,
    val amountUnits: String, val address: String, val identityAddress: String,
    val country: String = "", val provider: String = "", val offerId: String = "")
internal data class FiatChallenge(val nonce: String, val expiresAt: Long)

internal interface FiatGateway {
    val origin: String
    suspend fun capabilities(): FiatCapabilities
    suspend fun offers(selection: FiatSelection): List<FiatOffer>
    suspend fun challenge(request: FiatRequest): FiatChallenge
    suspend fun session(nonce: String, signature: String): String
}

internal fun fiatBackendOrigin(value: String): String {
    val uri = URI(value)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
        uri.rawQuery == null && uri.rawFragment == null && uri.rawPath in listOf("", "/"))
    require(uri.port == -1 || uri.port in 1..65535)
    return URI("https", null, uri.host.lowercase(), if (uri.port == 443) -1 else uri.port, null, null, null).toString()
}

internal fun fiatAmountUnits(value: String, decimals: Int): String? = try {
    require(decimals in 0..18 && value.length <= 50 && value.matches(Regex("[0-9]+([.,][0-9]+)?")))
    BigDecimal(value.replace(',', '.')).movePointRight(decimals).toBigIntegerExact().toString()
        .takeIf { it.matches(Regex("[1-9][0-9]{0,29}")) }
} catch (_: Exception) { null }

/** Built locally from the user's selection; never signs text supplied by a remote service. */
internal fun fiatChallengeMessage(origin: String, challenge: FiatChallenge, request: FiatRequest,
    nowSeconds: Long = System.currentTimeMillis() / 1000): String {
    require(fiatBackendOrigin(origin) == origin)
    require(challenge.nonce.matches(Regex("[a-f0-9]{64}")) && challenge.expiresAt > nowSeconds &&
        challenge.expiresAt - nowSeconds <= 180)
    require(request.fiat.matches(Regex("[A-Z]{3}")) && request.amountUnits.matches(Regex("[1-9][0-9]{0,29}")))
    require(request.identityAddress.matches(Regex("0x[a-fA-F0-9]{40}")))
    require(request.country.matches(Regex("[A-Z]{2}")) && request.provider.matches(Regex("[a-z][a-z0-9_-]{0,39}")) &&
        request.offerId.matches(Regex("[a-f0-9]{64}")))
    if (request.asset == Asset.BTC) require(org.astianx.midori.one.wallet.isBitcoinMainnetAddress(request.address))
    else require(request.address.equals(request.identityAddress, ignoreCase = true))
    return listOf("Midori Wallet - Fiat checkout", "Origin: $origin", "Nonce: ${challenge.nonce}",
        "Expires: ${challenge.expiresAt}", "Account: ${request.identityAddress.lowercase()}",
        "Operation: ${request.operation}", "Asset: ${request.asset}",
        "Network: ${if (request.asset == Asset.BTC) "mainnet" else "base"}", "Fiat: ${request.fiat}",
        "Amount units: ${request.amountUnits}", "Wallet: ${request.address}",
        "Country: ${request.country}", "Provider: ${request.provider}", "Offer: ${request.offerId}",
        "This authorizes one checkout session. It does not authorize a transfer.").joinToString("\n")
}

internal fun validateFiatCheckoutUrl(value: String, origin: String): String {
    val uri = URI(value)
    require(fiatBackendOrigin(origin) == origin && uri.scheme == "https" &&
        fiatBackendOrigin("https://${uri.rawAuthority}") == origin && uri.rawUserInfo == null && uri.rawFragment == null &&
        uri.rawQuery == null && uri.rawPath.matches(Regex("/v1/fiat/launch/[a-f0-9]{64}")))
    return value
}
