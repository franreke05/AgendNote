package com.franciscor.agendnote.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat

class AndroidNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOW_TASK) return
        createChannels(context)
        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        AndroidReminderStore.remove(context, taskId)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Recordatorio de tarea" }
        val details = intent.getStringExtra(EXTRA_DETAILS).orEmpty()
        val soundId = intent.getStringExtra(EXTRA_SOUND_ID)
            ?.let { runCatching { NotificationSoundId.valueOf(it) }.getOrNull() }
        val notification = NotificationCompat.Builder(context, channelId(soundId))
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(details.ifBlank { "Tienes una tarea pendiente" })
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(taskId.hashCode(), notification)
    }

    companion object {
        private const val CHANNEL_ID_PREFIX = "task_reminders_"
        private const val CHANNEL_NAME = "Recordatorios de tareas"
        private const val DEFAULT_CHANNEL_SUFFIX = "default"

        private fun channelId(soundId: NotificationSoundId?): String =
            CHANNEL_ID_PREFIX + (soundId?.name?.lowercase() ?: DEFAULT_CHANNEL_SUFFIX)

        /**
         * One `NotificationChannel` per [NotificationSoundId] plus a no-custom-sound default -
         * Android (8+) locks a channel's sound at creation time, so a distinct sound per
         * notification needs a distinct channel, not a per-notification `setSound()` call (which
         * Android silently ignores once a channel exists). Safe to call repeatedly:
         * `createNotificationChannel` is a no-op if the channel id already exists with the same
         * settings.
         *
         * Same no-crash-if-missing contract as iOS: a [NotificationSoundId] whose
         * [AndroidSoundAssets] lookup returns `0` (no bundled `res/raw` entry) just gets a
         * channel with the system default sound, never a broken channel.
         */
        fun createChannels(context: Context) {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            manager.createNotificationChannel(
                NotificationChannel(channelId(null), CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Avisos de tareas con hora programada"
                },
            )

            NotificationSoundId.entries.forEach { soundId ->
                val channel = NotificationChannel(
                    channelId(soundId),
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Avisos de tareas con hora programada"
                }
                val resId = AndroidSoundAssets.resolveNotificationSoundRawRes(context, soundId)
                if (resId != 0) {
                    val uri = Uri.parse("android.resource://${context.packageName}/$resId")
                    channel.setSound(uri, audioAttributes)
                } else {
                    println("AndroidNotificationReceiver: no bundled sound for $soundId, channel uses system default")
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
