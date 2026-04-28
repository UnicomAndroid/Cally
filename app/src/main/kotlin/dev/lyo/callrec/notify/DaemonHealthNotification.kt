// SPDX-License-Identifier: GPL-3.0-or-later
package dev.lyo.callrec.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dev.lyo.callrec.recorder.DaemonHealth
import dev.lyo.callrec.ui.MainActivity

object DaemonHealthNotification {
    private const val NOTIF_ID = 0xCA12
    const val EXTRA_FROM_HEALTH_NOTIF = "from_daemon_health_notif"

    fun update(ctx: Context, health: DaemonHealth) {
        val nm = ctx.getSystemService<NotificationManager>() ?: return
        if (health is DaemonHealth.Bound) {
            runCatching { nm.cancel(NOTIF_ID) }
            return
        }
        // Inline strings — replaced by R.string.daemon_* resources in Task 17
        val title: String
        val body: String
        when (health) {
            DaemonHealth.NotInstalled -> {
                title = "cally: Shizuku not installed"
                body = "Install Shizuku to enable call recording"
            }
            DaemonHealth.NotRunning -> {
                title = "cally: Shizuku stopped"
                body = "Open Shizuku and start the service"
            }
            DaemonHealth.NoPermission -> {
                title = "cally: Shizuku permission needed"
                body = "Open cally and grant Shizuku permission"
            }
            DaemonHealth.Stale -> {
                title = "cally: Recorder needs update"
                body = "Open cally to restart the recorder daemon"
            }
            is DaemonHealth.Unhealthy -> {
                title = "cally: Recorder error"
                body = "Open cally to restart the recorder daemon"
            }
            is DaemonHealth.Bound -> return  // unreachable; handled above
        }
        val tap = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_FROM_HEALTH_NOTIF, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(ctx, NotificationChannels.ID_STATUS)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tap)
            .build()
        runCatching { nm.notify(NOTIF_ID, notif) }
            .onFailure { /* POST_NOTIFICATIONS denied — silent drop is acceptable */ }
    }
}
