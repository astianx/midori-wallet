package org.astianx.midori.one.chat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.astianx.midori.one.MainActivity
import org.astianx.midori.one.R

/** Private alerts: neither the sender nor decrypted content is exposed to system surfaces. */
internal class ChatNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(message: ChatMessage, unreadCount: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.chats), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.chat_notifications_summary)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        })
        // Data, unlike extras, participates in PendingIntent identity. Hash collisions must not open another peer.
        val intent = Intent(context, MainActivity::class.java).putExtra(PEER_EXTRA, message.peer)
            .setData(Uri.Builder().scheme("midori").authority("chat").appendPath(message.peer).build())
            .setAction("org.astianx.midori.one.OPEN_CHAT")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // The serialized chat state owns counts; asynchronous system posting cannot lose a burst.
        val count = unreadCount.coerceAtLeast(1)
        val body = if (count == 1) context.getString(R.string.chat_new_private_message)
            else context.getString(R.string.chat_notification_count, count)
        manager.notify(message.peer, CHILD_ID, builder().setContentText(body).setNumber(count)
            .setWhen(message.timestamp).setShowWhen(true).setContentIntent(pending)
            .setGroup(GROUP).setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN).build())
        updateSummary()
    }

    fun clear(peer: String?) {
        if (peer == null) {
            manager.activeNotifications.filter { it.notification.channelId == CHANNEL }
                .forEach { manager.cancel(it.tag, it.id) }
        } else {
            manager.cancel(peer, CHILD_ID)
            manager.cancel(peer.hashCode()) // Remove any alert posted by versions before tagged IDs.
            updateSummary()
        }
    }

    private fun builder() = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_chat_notification).setColor(0xFF236B56.toInt())
        .setContentTitle(context.getString(R.string.app_name))
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE).setAutoCancel(true)

    private fun updateSummary() {
        val children = manager.activeNotifications.filter { it.notification.group == GROUP && it.tag != SUMMARY_TAG }
        // Android cancels grouped children when their summary is cancelled. Keep it until the last peer clears.
        if (children.isEmpty()) {
            manager.cancel(SUMMARY_TAG, CHILD_ID)
            return
        }
        val pending = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java)
            .setAction("org.astianx.midori.one.OPEN_MESSAGES")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(SUMMARY_TAG, CHILD_ID, builder()
            .setContentText(context.getString(R.string.chat_notification_count, children.sumOf { it.notification.number }))
            .setContentIntent(pending).setGroup(GROUP).setGroupSummary(true).setSilent(true).build())
    }

    companion object {
        const val PEER_EXTRA = "chat_peer"
        private const val CHANNEL = "private_messages"
        private const val GROUP = "midori_private_chats"
        private const val SUMMARY_TAG = "midori_chat_summary"
        private const val CHILD_ID = 0
    }
}
