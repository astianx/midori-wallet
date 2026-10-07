package org.astianx.midori.one

import android.content.Context
import androidx.annotation.StringRes

enum class Screen { Welcome, RestoreError, Create, Import, Home, Send, Receive, Sign, Messaging, Chats, Markets, More, Feature, MarketAsset, Help, Conversation, ChatSecurity, Licenses, GroupDetails, Personalization, ChatAppearance, ConversationAppearance, WalletAppearance, Profile }

internal val mainScreens = setOf(Screen.Chats, Screen.Home, Screen.Markets, Screen.More)

enum class AppFeature(@StringRes val title: Int, @StringRes val description: Int) {
    CONTACTS(R.string.contacts, R.string.contacts_pending),
    PROFILE(R.string.profile, R.string.profile_pending),
    SCANNER(R.string.scan_qr, R.string.scan_pending),
    BUY(R.string.buy, R.string.buy_pending),
    TRADE(R.string.trade, R.string.trade_pending),
    ALERTS(R.string.price_alerts, R.string.alerts_pending),
    DESKTOP(R.string.link_desktop, R.string.desktop_pending)
}

/** Non-secret introduction preference, independent of wallet creation and deletion. */
internal interface AppEntryStore {
    fun hasEntered(): Boolean
    fun markEntered()
}

internal class MemoryAppEntryStore : AppEntryStore {
    private var entered = false
    override fun hasEntered() = entered
    override fun markEntered() { entered = true }
}

internal class AppEntryPreferences(context: Context) : AppEntryStore {
    private val preferences = context.getSharedPreferences("midori_navigation", Context.MODE_PRIVATE)
    override fun hasEntered() = preferences.getBoolean("entered", false)
    override fun markEntered() { preferences.edit().putBoolean("entered", true).apply() }
}
