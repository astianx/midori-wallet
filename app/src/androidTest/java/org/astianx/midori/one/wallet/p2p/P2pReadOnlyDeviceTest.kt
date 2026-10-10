package org.astianx.midori.one.wallet.p2p

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.astianx.midori.one.BuildConfig
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in public gateway reads. No wallet, journal, private identity or order writes. */
@RunWith(AndroidJUnit4::class)
class P2pReadOnlyDeviceTest {
    @Test fun liveIndexCountryRatesAndSyntheticAccount() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("p2pReadOnly") == "true")
        val gateway = HttpP2pGateway(BuildConfig.FIAT_BACKEND_URL)
        val co = gateway.catalog().single { it.country == "CO" }
        assertTrue(gateway.rates(co).validAt(System.currentTimeMillis() / 1000))
        val orders = HttpP2pOrderGateway(BuildConfig.FIAT_BACKEND_URL)
        val available = orders.availability()
        assertTrue(available.reason in setOf("ready", "orders_disabled", "index_syncing", "index_unavailable", "index_unconfigured"))
        assertTrue(!available.enabled || available.reason == "ready")
        val user = "0x" + "1".repeat(40)
        val account = orders.account(user, "CO")
        assertEquals(user, account.user); assertEquals("COP", account.currency)
        assertTrue(p2pInteger(account.buyLimit)); assertTrue(p2pInteger(account.sellLimit)); assertTrue(p2pInteger(account.usdcBalance))
        assertTrue(p2pTimeValid(account.quotedAt, account.expiresAt, System.currentTimeMillis() / 1000))
    }
}
