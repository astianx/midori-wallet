package org.astianx.midori.one.chat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.astianx.midori.one.MainActivity
import org.astianx.midori.one.R

/** User-visible IM connection. Android can still suspend networking in Doze or stop the app. */
class ChatConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var chat: ChatViewModel
    private var networkRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { chat.networkAvailable() }
        override fun onLost(network: Network) { chat.networkLost() }
    }

    override fun onCreate() {
        super.onCreate()
        chat = ChatSession.get(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            getString(R.string.chat_background_title), NotificationManager.IMPORTANCE_LOW))
        val notification = notification(chat.state.value.connection)
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, notification)
        chat.setConnectionServiceRunning(true)
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
        networkRegistered = true
        scope.launch {
            chat.state.map { Triple(it.account, it.backgroundConnection, it.connection) }.distinctUntilChanged().collect { (account, enabled, connection) ->
                if (account.isBlank() || !enabled) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else manager.notify(ID, notification(connection))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            getString(R.string.chat_background_title), NotificationManager.IMPORTANCE_LOW))
        manager.notify(ID, notification(chat.state.value.connection))
    }
    override fun onDestroy() {
        if (networkRegistered) getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        chat.setConnectionServiceRunning(false)
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(connection: ChatConnection): Notification {
        val pending = PendingIntent.getActivity(this, ID, Intent(this, MainActivity::class.java)
            .setAction(OPEN_CONNECTION).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_chat_notification).setContentTitle(getString(R.string.app_name))
            .setContentText(getString(connection.label())).setContentIntent(pending)
            .setOngoing(true).setSilent(true).setShowWhen(false).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
    }

    companion object {
        private const val CHANNEL = "chat_connection"
        private const val ID = 4812
        const val OPEN_CONNECTION = "org.astianx.midori.one.CONNECTION"
        // Only called while the activity is STARTED; never from network callbacks or a boot receiver.
        fun start(context: Context): Boolean = runCatching {
            ContextCompat.startForegroundService(context, Intent(context, ChatConnectionService::class.java))
        }.isSuccess
        fun stop(context: Context) { context.stopService(Intent(context, ChatConnectionService::class.java)) }
    }
}
