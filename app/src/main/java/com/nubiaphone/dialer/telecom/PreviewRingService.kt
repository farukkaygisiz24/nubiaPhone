package com.nubiaphone.dialer.telecom

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
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
 * Volume keys or swipe-dismiss silence the ringtone; the notification stays for Answer/Decline.
 */
class PreviewRingService : Service() {
    private var volumeReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                IncomingPreview.dismiss(this)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                promote(buildNotification())
                return START_STICKY
            }
        }
        promote(buildNotification())
        if (!IncomingPreview.silenced) IncomingPreview.startRingFromService(this)
        if (volumeReceiver == null) {
            volumeReceiver =
                VolumeSilence.register(this) {
                    IncomingPreview.silence(this)
                }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        VolumeSilence.unregister(this, volumeReceiver)
        volumeReceiver = null
        IncomingPreview.stopRingFromService()
        super.onDestroy()
    }

    private fun promote(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(IncomingPreview.NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            startForeground(IncomingPreview.NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val mutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val silenced = IncomingPreview.silenced
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
        val silence =
            PendingIntent.getBroadcast(
                this,
                14,
                Intent(this, CallActionReceiver::class.java).setAction(CallActionReceiver.PREVIEW_SILENCE),
                mutable,
            )
        return NotificationCompat.Builder(this, IncomingPreview.CH)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("Önizleme Kişi")
            .setContentText(if (silenced) "Gelen arama · sessiz" else "Gelen arama")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(silenced)
            .setOnlyAlertOnce(silenced)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setDeleteIntent(silence)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer))
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.nubiaphone.dialer.STOP_PREVIEW_RING"
        const val ACTION_REFRESH = "com.nubiaphone.dialer.REFRESH_PREVIEW_RING"

        fun start(context: Context) {
            startCmd(context, null)
        }

        fun refresh(context: Context) {
            startCmd(context, ACTION_REFRESH)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PreviewRingService::class.java))
        }

        private fun startCmd(context: Context, action: String?) {
            val i = Intent(context, PreviewRingService::class.java).apply { this.action = action }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
