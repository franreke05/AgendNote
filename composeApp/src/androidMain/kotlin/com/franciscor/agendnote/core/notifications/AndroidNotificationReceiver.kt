package com.franciscor.agendnote.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class AndroidNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOW_TASK) return
        createChannel(context)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Recordatorio de tarea" }
        val details = intent.getStringExtra(EXTRA_DETAILS).orEmpty()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(details.ifBlank { "Tienes una tarea pendiente" })
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(intent.getStringExtra(EXTRA_TASK_ID).orEmpty().hashCode(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "task_reminders"
        private const val CHANNEL_NAME = "Recordatorios de tareas"
        private const val ACTION_SHOW_TASK = "com.franciscor.agendnote.SHOW_TASK_NOTIFICATION"
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_TITLE = "task_title"
        private const val EXTRA_DETAILS = "task_details"

        fun createChannel(context: Context) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Avisos de tareas con hora programada"
                }
                context.getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(channel)
            }
        }
    }
}
