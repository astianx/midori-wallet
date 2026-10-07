package org.astianx.midori.one.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.jivesoftware.smackx.omemo.internal.OmemoDevice
import org.jxmpp.jid.impl.JidCreate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatVaultTest {
    @Test fun deviceListRefreshPersistsRevocationAndAvoidsRewritingUnchangedCiphertext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "group-directory-test-${UUID.randomUUID()}"
        val file = File(context.noBackupFilesDir, "$name.enc")
        val keyAlias = "midori.${name.replace('-', '.')}"
        val own = OmemoDevice(JidCreate.bareFrom("synthetic-owner@example.org"), 7)
        val peer = JidCreate.bareFrom("synthetic-peer@example.org")
        try {
            val store = EncryptedOmemoStore(ChatVault(context, name))
            store.mergePublishedDevices(own, peer, setOf(11, 12))
            val ciphertext = file.readBytes()
            store.mergePublishedDevices(own, peer, setOf(12, 11))
            assertArrayEquals("An identical fresh list should not rewrite the vault", ciphertext, file.readBytes())
            store.mergePublishedDevices(own, peer, setOf(11, 13))
            val restored = EncryptedOmemoStore(ChatVault(context, name)).loadCachedDeviceList(own, peer)
            assertEquals(setOf(11, 13), restored.activeDevices)
            assertEquals(setOf(12), restored.inactiveDevices)
        } finally {
            file.delete()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(keyAlias) }
        }
    }

    @Test fun keysAndHistorySurviveReopenAndRejectTamperingWithoutOverwrite() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "messenger-test-${UUID.randomUUID()}"
        val file = File(context.noBackupFilesDir, "$name.enc")
        val keyAlias = "midori.${name.replace('-', '.')}"
        try {
            val vault = ChatVault(context, name)
            vault.put("identity", "private-test-key")
            vault.put("message", "private-test-message")
            val ciphertext = file.readBytes()
            assertFalse(String(ciphertext).contains("private-test"))
            val restored = ChatVault(context, name)
            assertEquals("private-test-key", restored.get("identity"))
            assertEquals("private-test-message", restored.get("message"))
            val tampered = ciphertext.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            file.writeBytes(tampered)
            try { ChatVault(context, name).get("identity"); fail("Corrupted data was accepted") }
            catch (_: javax.crypto.AEADBadTagException) { }
            assertArrayEquals(tampered, file.readBytes())
            file.writeBytes(ciphertext)
            assertEquals("private-test-key", ChatVault(context, name).get("identity"))
        } finally {
            file.delete()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(keyAlias) }
        }
    }
}
