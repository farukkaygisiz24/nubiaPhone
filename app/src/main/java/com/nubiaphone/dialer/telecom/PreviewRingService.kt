package com.nubiaphone.dialer.telecom

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.nubiaphone.dialer.R
import com.nubiaphone.dialer.ui.incall.InCallActivity

/**
 * Foreground service that hosts the unlocked fake-incoming CallStyle notification.
 * Android requires CallStyle (without fullScreenIntent) to ride on an FGS / job.
 */
class PreviewRingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            IncomingPreview.dismiss(this)
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(IncomingPreview.NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            startForeground(IncomingPreview.NOTIF_ID, notification)
        }
        IncomingPreview.startRingFromService(this)
        return START_STICKY
    }

    override fun onDestroy() {
        IncomingPreview.stopRingFromService()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val mutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val person = Person.Builder().setName("Önizleme Kişi").setImportant(true).build()
        val open =
            PendingIntent.getActivity(
                this,
                11,
                InCallActivity.intent(this)
                    .putExtra(InCallActivity.EXTRA_PREVIEW, "incoming")
                    .putExtra(InCallActivity.EXTRA_PREVIEW_SILENT, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val answer =
            PendingIntent.getActivity(
                this,
                12,
                InCallActivity.intent(this)
                    .putExtra(InCallActivity.EXTRA_PREVIEW, "active")
                    .putExtra(InCallActivity.EXTRA_PREVIEW_SILENT, true),
                mutable,
            )
        val decline =
            PendingIntent.getBroadcast(
                this,
                13,
                Intent(this, CallActionReceiver::class.java).setAction(CallActionReceiver.PREVIEW_DECLINE),
                mutable,
            )
        return NotificationCompat.Builder(this, IncomingPreview.CH)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("Önizleme Kişi")
            .setContentText("Gelen arama")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer))
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.nubiaphone.dialer.STOP_PREVIEW_RING"

        fun start(context: Context) {
            val i = Intent(context, PreviewRingService::class.java)
            ContextCompatStart(context, i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PreviewRingService::class.java))
        }

        private fun ContextCompatStart(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
