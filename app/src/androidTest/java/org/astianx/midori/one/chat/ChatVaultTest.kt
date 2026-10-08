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
    private fun withVault(test: (ChatVault, File, () -> ChatVault) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "batch-test-${UUID.randomUUID()}"
        val file = File(context.noBackupFilesDir, "$name.enc")
        try { test(ChatVault(context, name), file) { ChatVault(context, name) } }
        finally {
            file.delete()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("midori.${name.replace('-', '.')}") }
        }
    }

    @Test fun recordUpdatesPreserveEscapedTextAndRemoveDeletedEntries() = withVault { vault, _, reopen ->
        val name = "history/\"\\\n\u0000"
        val original = "Español, português, 日本語: \"texto\" / \\ \n\t\r\u0000"
        vault.put(name, original)
        vault.put("session", "first")
        vault.put("session", "next")
        assertEquals(original, reopen().get(name))
        vault.put(name, "$original updated")
        vault.put("session", null)
        assertEquals("$original updated", reopen().get(name))
        assertNull(reopen().get("session"))
        vault.put(name, null)
        assertTrue(reopen().keys("").isEmpty())
    }

    @Test fun fullGroupSessionUpdatesSurviveReopen() = withVault { vault, file, reopen ->
        val session = "Synthetic session / ".repeat(64)
        vault.durableBatch {
            repeat(200) { member ->
                val prefix = "synthetic-group/member-$member/"
                vault.put(prefix + "session", session)
                vault.put(prefix + "identity", "Synthetic identity $member")
                vault.put(prefix + "received", "0")
                vault.put(prefix + "counter", "0")
            }
        }
        val start = System.nanoTime()
        repeat(200) { member ->
            val prefix = "synthetic-group/member-$member/"
            vault.put(prefix + "session", "$session advanced $member")
            vault.put(prefix + "received", "1")
            vault.put(prefix + "counter", "1")
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0
        android.util.Log.i("MidoriVaultTest", "group_updates=600 elapsed_ms=$elapsed encrypted_bytes=${file.length()}")
        val restored = reopen()
        repeat(200) { member ->
            val prefix = "synthetic-group/member-$member/"
            assertEquals("$session advanced $member", restored.get(prefix + "session"))
            assertEquals("1", restored.get(prefix + "received"))
            assertEquals("1", restored.get(prefix + "counter"))
        }
    }

    @Test fun batchedRatchetsAreDurableBeforeReturnIncludingFailedEncryptionAdvances() = withVault { vault, file, reopen ->
        vault.put("session/first", "original")
        val original = file.readBytes()
        vault.durableBatch {
            vault.put("session/first", "advanced")
            vault.durableBatch { vault.put("session/second", "advanced") }
            assertEquals("advanced", vault.get("session/first"))
            assertEquals(setOf("session/first", "session/second"), vault.keys("session/"))
            assertArrayEquals(original, file.readBytes())
        }
        assertEquals("advanced", reopen().get("session/first"))
        assertEquals("advanced", reopen().get("session/second"))
        val failure = java.io.IOException("Synthetic encryption failure")
        assertSame(failure, assertThrows(java.io.IOException::class.java) {
            vault.durableBatch { vault.put("session/first", "advanced again"); throw failure }
        })
        assertEquals("advanced again", reopen().get("session/first"))
    }

    @Test fun batchesPreserveConcurrentIndependentWritesAndRejectConflictingState() = withVault { vault, _, reopen ->
        fun concurrent(key: String, value: String) {
            val thread = Thread { vault.put(key, value) }.apply { start() }
            thread.join(3_000)
            assertFalse("A batch held the vault lock during work", thread.isAlive)
        }
        vault.put("ratchet", "initial")
        vault.durableBatch { vault.put("ratchet", "next"); concurrent("history", "arrived") }
        assertEquals("arrived", reopen().get("history"))
        assertEquals("next", reopen().get("ratchet"))
        assertThrows(IllegalStateException::class.java) {
            vault.durableBatch {
                assertEquals("next", vault.get("ratchet"))
                concurrent("ratchet", "newer")
                vault.put("ratchet", "stale")
                vault.put("not-committed", "stale")
            }
        }
        assertEquals("newer", reopen().get("ratchet"))
        assertNull(reopen().get("not-committed"))
        vault.put("after-conflict", "works")
        assertEquals("works", reopen().get("after-conflict"))
    }

    @Test fun failedPersistenceCannotReturnCiphertext() = withVault { vault, file, reopen ->
        vault.put("ratchet", "durable")
        val original = file.readBytes()
        val blockedWrite = File(file.path + ".new")
        val blocker = File(blockedWrite, "blocker")
        try {
            assertThrows(java.io.IOException::class.java) {
                vault.durableBatch {
                    vault.put("ratchet", "uncommitted")
                    assertTrue(blockedWrite.mkdir())
                    blocker.writeText("Prevent AtomicFile from creating its temporary file")
                    "ciphertext that must not escape"
                }
            }
        } finally {
            blocker.delete()
            blockedWrite.delete()
        }
        assertArrayEquals(original, file.readBytes())
        assertEquals("durable", reopen().get("ratchet"))
    }

    @Test fun legacyVaultReadsWithoutChangesAndMigratesOnlyAfterSuccessfulWrite() = withVault { vault, file, reopen ->
        vault.put("ratchet", "initial") // Creates only this test's Keystore alias.
        val name = file.nameWithoutExtension
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val secret = store.getKey("midori.${name.replace('-', '.')}", null) as javax.crypto.SecretKey
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secret)
        cipher.updateAAD("midori:${name.replace('-', ':')}".toByteArray())
        val legacy = byteArrayOf(1) + cipher.iv + cipher.doFinal("{\"ratchet\":\"legacy\"}".toByteArray())
        file.writeBytes(legacy)
        val restored = reopen()
        assertEquals("legacy", restored.get("ratchet"))
        assertArrayEquals(legacy, file.readBytes())
        val blocked = File(file.path + ".new").apply { mkdir() }
        val blocker = File(blocked, "blocker").apply { writeText("block") }
        try { assertThrows(java.io.IOException::class.java) { restored.put("ratchet", "must not survive") } }
        finally { blocker.delete(); blocked.delete() }
        assertArrayEquals(legacy, file.readBytes())
        assertEquals("legacy", restored.get("ratchet"))
        restored.put("ratchet", "migrated")
        assertEquals(2, file.readBytes()[0].toInt())
        assertEquals("migrated", reopen().get("ratchet"))
        val valid = file.readBytes()
        file.writeBytes(valid.clone().also { it[20] = (it[20].toInt() xor 1).toByte() })
        assertThrows(javax.crypto.AEADBadTagException::class.java) { reopen().get("ratchet") }
        assertEquals(2, file.readBytes()[0].toInt()) // Never downgraded or replaced after authentication failure.
    }

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
