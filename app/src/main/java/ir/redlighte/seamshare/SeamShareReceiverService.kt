package ir.redlighte.seamshare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ir.redlighte.seamshare.network.IncomingRequest
import ir.redlighte.seamshare.network.IncomingText
import ir.redlighte.seamshare.network.SeamIdentityStore
import ir.redlighte.seamshare.network.SeamReceiverServer

class SeamShareReceiverService : Service() {
    companion object {
        const val ACTION_START = "ir.redlighte.seamshare.START_RECEIVING"
        const val ACTION_APPROVE = "ir.redlighte.seamshare.APPROVE_INCOMING"
        const val EXTRA_ID = "id"
        const val EXTRA_APPROVED = "approved"
        private const val CHANNEL_ID = "seam_share_transfer"
        private const val NOTIFICATION_ID = 38949
    }

    private lateinit var receiver: SeamReceiverServer

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val identity = SeamIdentityStore(this).load()
        receiver = SeamReceiverServer(this, identity)
        receiver.onIncomingRequest = { request -> notifyIncoming(request) }
        receiver.onIncomingText = { text -> notifyText(text) }
        startForeground(NOTIFICATION_ID, serviceNotification())
        receiver.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_APPROVE) {
            val id = intent.getStringExtra(EXTRA_ID) ?: return START_STICKY
            receiver.approve(id, intent.getBooleanExtra(EXTRA_APPROVED, false))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        receiver.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun serviceNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle("SEAM Share")
        .setContentText("Ready to receive files and text")
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private fun notifyIncoming(request: IncomingRequest) {
        val accept = PendingIntent.getService(this, request.id.hashCode(), Intent(this, SeamShareReceiverService::class.java).apply {
            action = ACTION_APPROVE
            putExtra(EXTRA_ID, request.id)
            putExtra(EXTRA_APPROVED, true)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val decline = PendingIntent.getService(this, request.id.hashCode() + 1, Intent(this, SeamShareReceiverService::class.java).apply {
            action = ACTION_APPROVE
            putExtra(EXTRA_ID, request.id)
            putExtra(EXTRA_APPROVED, false)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Incoming transfer")
            .setContentText("${request.name} · ${request.size / 1024} KB")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Accept", accept)
            .addAction(0, "Decline", decline)
            .build()
        getSystemService(NotificationManager::class.java).notify(request.id.hashCode(), notification)
    }

    private fun notifyText(text: IncomingText) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(if (text.kind == "clipboard") "Clipboard received" else "Text received")
            .setContentText(text.text.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(text.id.hashCode(), notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "SEAM Share transfers", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming SEAM Share transfers and text"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}