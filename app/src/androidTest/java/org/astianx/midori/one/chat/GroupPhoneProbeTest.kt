package org.astianx.midori.one.chat

import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import org.astianx.midori.one.UiText
import org.jivesoftware.smack.android.AndroidSmackInitializer
import org.jivesoftware.smack.filter.StanzaTypeFilter
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** One real Android client, private test vault, 199 remote members plus its other devices. */
@RunWith(AndroidJUnit4::class)
class GroupPhoneProbeTest {
    private fun await(label: String, seconds: Int = 45, predicate: () -> Boolean) {
        val end = System.nanoTime() + seconds * 1_000_000_000L
        while (!predicate() && System.nanoTime() < end) Thread.sleep(25)
        assertTrue(label, predicate())
    }

    @Test fun fullGroupFromAndroidWithDurableRatchets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun file(suffix: String) = File(context.filesDir, "group-phone-$suffix")
        assumeTrue(file("fixture.json").exists())
        val config = JSONObject(file("fixture.json").readText())
        val account = ChatAccount(config.getString("account"), config.getString("password"), "10.0.2.2")
        assertTrue(account.address.startsWith("midori-group-it-") && account.address.endsWith("@localhost"))
        val room = config.getString("room")
        val membersJson = config.getJSONArray("members")
        val members = (0 until membersJson.length()).map { membersJson.getString(it) }.toSet()
        assertEquals(GroupPolicy.MAX_ENCRYPTED_MEMBERS, members.size)
        val name = "group-phone-${UUID.randomUUID()}"
        val vaultFile = File(context.noBackupFilesDir, "$name.enc")
        val alias = "midori.${name.replace('-', '.')}"
        val messages = CopyOnWriteArrayList<ChatMessage>()
        val wire = CopyOnWriteArrayList<Message>()
        AndroidSmackInitializer.initialize(context)
        val client = SmackMessenger(ChatVault(context, name), config.getString("pin")) { c ->
            XMPPTCPConnection(c).apply {
                addStanzaSendingListener({ if (it is Message) wire.add(it) }, StanzaTypeFilter.MESSAGE)
            }
        }
        try {
            client.connect(account, object : ChatEvents {
                override fun connection(status: ChatConnection) {}
                override fun contacts(contacts: List<ChatContact>) {}
                override fun message(message: ChatMessage, historical: Boolean) { messages.add(message) }
                override fun receipt(peer: String, id: String, status: MessageDelivery) {}
                override fun fingerprint(value: String) {}
                override fun blocked(addresses: Set<String>) {}
                override fun failure(error: UiText) {}
            })
            assertEquals(members, client.joinGroup(room).members)
            client.devices(room).forEach { client.trust(it, true) } // Isolated synthetic identities only.
            file("ready").writeText("ready")
            await("host approved this phone's device", 180) { file("go").exists() }
            val samples = mutableListOf<Double>()
            var largestStanza = 0
            repeat(10) { sequence ->
                val id = UUID.randomUUID().toString()
                val body = "Synthetic Android group probe $sequence"
                file("outgoing.json").writeText(JSONObject().put("id", id).put("body", body).toString())
                val start = System.nanoTime()
                client.send(room, body, id)
                await("all remote members decrypted") { file("ack").takeIf { it.exists() }?.readText() == id }
                samples.add((System.nanoTime() - start) / 1_000_000.0)
                await("encrypted stanza captured") { wire.any { it.stanzaId == id } }
                val stanza = wire.first { it.stanzaId == id }
                assertNull(stanza.body)
                assertTrue(stanza.hasExtension("encrypted", SmackMessenger.OMEMO_NAMESPACE))
                assertFalse(stanza.toXML().toString().contains(body))
                largestStanza = maxOf(largestStanza, stanza.toXML().toString().toByteArray().size)
                android.util.Log.i("MidoriGroupTest", "phone_all_received=$sequence delivery_ms=${samples.last()}")
            }
            // Host sends back an encrypted message after acknowledging every receiver of all ten sends.
            await("phone receives group ciphertext") { messages.any { it.id == config.getString("reply_id") && it.body == "Synthetic Android reply" } }
            val sorted = samples.sorted()
            assertTrue("Android p95 exceeds 15 s delivery budget", sorted.last() < 15_000)
            file("report.json").writeText(JSONObject().put("status", "passed").put("api", android.os.Build.VERSION.SDK_INT)
                .put("members", members.size).put("samples", samples.size).put("delivery_p50_ms", sorted[4])
                .put("delivery_p95_ms", sorted.last()).put("delivery_p99_ms", sorted.last())
                .put("largest_stanza_bytes", largestStanza).put("process_pss_kib_at_end", Debug.getPss())
                .put("storage", "isolated Android Keystore vault; real app vault untouched").toString(2))
        } finally {
            runCatching { client.disconnect() }
            vaultFile.delete()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }
}
