package com.franciscor.agendnote.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/** Shows one standard task reminder using the phone's default notification sound. */
class AndroidNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOW_TASK) return

        createChannel(context)
        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        AndroidReminderStore.remove(context, taskId)

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
            .ifBlank { "Recordatorio de tarea" }
        val details = intent.getStringExtra(EXTRA_DETAILS).orEmpty()
        val openAppIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        val contentIntent = openAppIntent?.let {
            PendingIntent.getActivity(
                context,
                taskId.hashCode(),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(details.ifBlank { "Tienes una tarea pendiente" })
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(taskId.hashCode(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "task_reminders"
        private const val CHANNEL_NAME = "Recordatorios"

        fun createChannel(context: Context) {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = "Avisos de tareas programadas"
                    },
                )
        }
    }
}
