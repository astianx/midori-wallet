package org.astianx.midori.one.wallet.p2p

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.astianx.midori.one.wallet.fiat.FiatOperation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Dedicated temporary preferences and alias. Never accesses wallet/chat/production journals. */
@RunWith(AndroidJUnit4::class)
class P2pJournalDeviceTest {
    @Test fun encryptedRestartConflictAndCorruptionPreserveTheRecoveryRecord() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "p2p-order-fixture-${UUID.randomUUID()}"
        val alias = "midori.p2p.fixture.${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val owner = "0x" + "1".repeat(40)
        val secret = "2".repeat(64)
        val row = P2pPrivateRecord("fixture", owner, "CO", FiatOperation.SELL, "1000000", secret,
            "3001234567", submission = P2pSubmission.UNKNOWN, to = P2P_DIAMOND, data = "0x12345678")
        try {
            val first = KeystoreP2pOrderStore(context, name, alias)
            val stale = KeystoreP2pOrderStore(context, name, alias)
            assertTrue(first.load(owner).isEmpty()); assertTrue(stale.load(owner).isEmpty())
            first.save(owner, listOf(row))
            val envelope = prefs.getString(owner, null)!!
            assertFalse(envelope.contains(secret)); assertFalse(envelope.contains(row.destination))
            assertTrue(runCatching { stale.save(owner, emptyList()) }.isFailure)
            assertEquals(envelope, prefs.getString(owner, null))
            assertEquals(row, KeystoreP2pOrderStore(context, name, alias).load(owner).single())
            val corrupt = envelope.replace("\"ciphertext\":\"", "\"ciphertext\":\"AAAA")
            assertTrue(prefs.edit().putString(owner, corrupt).commit())
            val restarted = KeystoreP2pOrderStore(context, name, alias)
            assertTrue(runCatching { restarted.load(owner) }.isFailure)
            assertTrue(runCatching { restarted.save(owner, emptyList()) }.isFailure)
            assertEquals(corrupt, prefs.getString(owner, null))
        } finally {
            prefs.edit().clear().commit(); context.deleteSharedPreferences(name)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); if (containsAlias(alias)) deleteEntry(alias) }
        }
    }
}
