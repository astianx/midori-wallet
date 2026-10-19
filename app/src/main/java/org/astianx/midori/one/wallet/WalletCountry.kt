package org.astianx.midori.one.wallet

import android.content.Context
import java.util.Locale

/** The country is bound to one wallet address and can be written only once. It is not a credential. */
internal interface WalletCountryStore {
    fun country(address: String): String?
    fun bind(address: String, country: String): Boolean
}

internal fun walletCountryValid(code: String) = code in Locale.getISOCountries()

internal fun walletCountries(locale: Locale): List<Pair<String, String>> =
    Locale.getISOCountries().map { it to Locale("", it).getDisplayCountry(locale) }
        .filter { it.second.isNotBlank() }.sortedBy { it.second }

internal class MemoryWalletCountryStore : WalletCountryStore {
    private val values = mutableMapOf<String, String>()
    override fun country(address: String) = values[address.lowercase()]
    @Synchronized override fun bind(address: String, country: String): Boolean {
        if (!walletCountryValid(country)) return false
        return values.getOrPut(address.lowercase()) { country } == country
    }
}

internal class WalletCountryPreferences(context: Context) : WalletCountryStore {
    private val prefs = context.getSharedPreferences("midori_wallet_country_v1", Context.MODE_PRIVATE)
    private fun key(address: String) = "country:${address.lowercase()}"

    override fun country(address: String) = prefs.getString(key(address), null)?.takeIf(::walletCountryValid)

    @Synchronized override fun bind(address: String, country: String): Boolean {
        if (!walletCountryValid(country) || address.isBlank()) return false
        val existing = prefs.getString(key(address), null)
        if (existing != null) return existing == country
        return prefs.edit().putString(key(address), country).commit()
    }
}
