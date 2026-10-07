package org.astianx.midori.one.chat

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smackx.omemo.element.OmemoDeviceListElement
import org.jivesoftware.smackx.omemo.util.OmemoConstants
import org.jivesoftware.smackx.pubsub.PayloadItem
import org.jivesoftware.smackx.pubsub.PubSubManager
import org.jxmpp.jid.impl.JidCreate

/** Fresh PEP reads only. No recipient caching, trust changes, or parallel ratchet mutation. */
internal object GroupDeviceDirectory {
    private val requests = Executors.newFixedThreadPool(8) { task ->
        Thread(task, "midori-group-devices").apply { isDaemon = true }
    }

    fun fetch(connection: XMPPConnection, members: Set<String>): Map<String, Set<Int>> =
        fetch(members) { member ->
            val pubsub = PubSubManager.getInstanceFor(connection, JidCreate.bareFrom(member))
            val node = pubsub.getLeafNode(OmemoConstants.PEP_NODE_DEVICE_LIST)
            val items = node.getItems<PayloadItem<OmemoDeviceListElement>>()
            items.lastOrNull()?.payload?.deviceIds?.toSet() ?: emptySet()
        }

    // Eight concurrent network reads at most. Collect failures as they arrive and cancel pending work.
    internal fun fetch(members: Set<String>, read: (String) -> Set<Int>): Map<String, Set<Int>> {
        require(members.size <= GroupPolicy.MAX_ENCRYPTED_MEMBERS)
        val completed = ExecutorCompletionService<Pair<String, Set<Int>>>(requests)
        val pending = members.map { member -> completed.submit(Callable { member to read(member) }) }
        return try {
            buildMap { repeat(members.size) { val (member, devices) = completed.take().get(); put(member, devices) } }
        } catch (e: ExecutionException) {
            throw (e.cause as? Exception ?: e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } finally {
            pending.forEach { if (!it.isDone) it.cancel(true) }
        }
    }
}
