package org.astianx.midori.one.chat

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatNotificationsTest {
    @Test fun privateAlertsCountMessagesAndKeepCollidingPeersSeparate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        org.junit.Assume.assumeTrue("Notification permission is required for this device test", granted)
        val first = "Aa@notification-test.invalid"
        val second = "BB@notification-test.invalid"
        val notifications = ChatNotifications(context)
        try {
            assertEquals(first.hashCode(), second.hashCode())
            notifications.show(ChatMessage("one", first, "secret body one", false, System.currentTimeMillis()), 1)
            notifications.show(ChatMessage("two", first, "secret body two", false, System.currentTimeMillis()), 2)
            notifications.show(ChatMessage("three", second, "secret body three", false, System.currentTimeMillis()), 1)
            val deadline = android.os.SystemClock.elapsedRealtime() + 3_000
            while (manager.activeNotifications.count { it.tag == first || it.tag == second } < 2 &&
                android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
            val alerts = manager.activeNotifications.filter { it.tag == first || it.tag == second }
            assertEquals(2, alerts.size)
            val a = alerts.first { it.tag == first }.notification
            val b = alerts.first { it.tag == second }.notification
            assertEquals(2, a.number)
            assertEquals(1, b.number)
            assertNotEquals(a.contentIntent, b.contentIntent)
            alerts.forEach { alert ->
                assertEquals(Notification.VISIBILITY_PRIVATE, alert.notification.visibility)
                val title = alert.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
                val text = alert.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
                assertFalse((title + text).contains("secret body"))
                assertFalse((title + text).contains("notification-test.invalid"))
            }
            notifications.clear(first)
            assertFalse(manager.activeNotifications.any { it.tag == first })
            assertTrue(manager.activeNotifications.any { it.tag == second })
        } finally {
            notifications.clear(first); notifications.clear(second)
        }
    }
}
