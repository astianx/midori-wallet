package org.astianx.midori.one

import org.astianx.midori.one.chat.*
import org.astianx.midori.one.wallet.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ThemeBundleTest {
    private val theme = ThemeBundle(AppStyle(accent = AccentTone.OCEAN, textScale = 120),
        ChatAppearance(wallpaper = ChatWallpaperStyle.PHOTO, photo = "12345678-1234-1234-1234-123456789abc.jpg", fontSize = 20),
        WalletAppearance(hideBalances = true, preferredToken = Asset.USDT))

    @Test fun portableThemeExcludesLocalPhotosAndPreservesAppearance() {
        val encoded = theme.encode()
        assertFalse(encoded.contains("12345678"))
        val decoded = ThemeBundle.decode(encoded.toByteArray())
        assertEquals(theme.app, decoded.app)
        assertEquals(theme.wallet, decoded.wallet)
        assertEquals(ChatWallpaperStyle.BOTANICAL, decoded.chats.wallpaper)
        assertNull(decoded.chats.photo)
        assertEquals(20, decoded.chats.fontSize)
    }
    @Test fun rejectsUnsafeOrUnsupportedImportsBeforeApplyingAnything() {
        val edits: List<(JSONObject) -> Unit> = listOf(
            { it.put("version", 2) }, { it.put("format", "other") },
            { it.getJSONObject("app").put("textScale", 999) },
            { it.getJSONObject("chats").put("fontSize", 0) },
            { it.getJSONObject("chats").put("photo", "../../vault") },
            { it.getJSONObject("chats").put("photo", "12345678-1234-1234-1234-123456789abc.jpg") },
            { it.getJSONObject("chats").put("wallpaper", "PHOTO") },
            { it.getJSONObject("wallet").put("preferredToken", "BTC") })
        edits.forEach { edit ->
            val json = JSONObject(theme.encode()); edit(json)
            assertTrue(runCatching { ThemeBundle.decode(json.toString().toByteArray()) }.isFailure)
        }
        assertTrue(runCatching { ThemeBundle.decode(ByteArray(ThemeBundle.MAX_BYTES + 1)) }.isFailure)
        assertTrue(runCatching { ThemeBundle.decode("broken".toByteArray()) }.isFailure)
    }
    @Test fun shortHandleIsServerScopedAndNeverAResourceOrArbitraryDomain() {
        assertEquals("ponchale@example.org", resolveChatHandle("@ponchale", "me@example.org"))
        assertEquals("ponchale@other.org", resolveChatHandle("ponchale@other.org", "me@example.org"))
        listOf("@", "@name/phone", "@name@other.org", "@some name").forEach {
            assertTrue(runCatching { resolveChatHandle(it, "me@example.org") }.isFailure)
        }
        assertTrue(runCatching { resolveChatHandle("@ponchale", "") }.isFailure)
    }
}
