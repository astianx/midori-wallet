package org.astianx.midori.one.wallet

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

data class WalletAppearance(val hideBalances: Boolean = false, val showTexture: Boolean = false,
    val compact: Boolean = false, val showHelp: Boolean = true,
    val preferredToken: Asset = Asset.USDC) {
    fun toJson() = JSONObject().put("hideBalances", hideBalances).put("showTexture", showTexture)
        .put("compact", compact).put("showHelp", showHelp).put("preferredToken", preferredToken.name)
    companion object {
        fun fromJson(j: JSONObject) = WalletAppearance(j.getBoolean("hideBalances"), j.getBoolean("showTexture"),
            j.getBoolean("compact"), j.getBoolean("showHelp"),
            Asset.valueOf(j.getString("preferredToken")).also { require(it in setOf(Asset.USDC, Asset.USDT)) })
    }
}
internal val LocalWalletAppearance = staticCompositionLocalOf { WalletAppearance() }
internal class WalletAppearanceStore(context: Context) {
    private val prefs = context.getSharedPreferences("midori_wallet_appearance", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(runCatching { WalletAppearance.fromJson(JSONObject(prefs.getString("style", "")!!)) }.getOrDefault(WalletAppearance()))
    val state: StateFlow<WalletAppearance> = mutable
    fun save(value: WalletAppearance) { prefs.edit().putString("style", value.toJson().toString()).apply(); mutable.value = value }
}
internal val LocalWalletAppearanceChange = staticCompositionLocalOf<(WalletAppearance) -> Unit> { {} }
