package com.franciscor.agendnote.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

internal const val ACTION_SHOW_TASK = "com.franciscor.agendnote.SHOW_TASK_NOTIFICATION"
internal const val EXTRA_TASK_ID = "task_id"
internal const val EXTRA_TITLE = "task_title"
internal const val EXTRA_DETAILS = "task_details"

/**
 * Small local copy of each pending reminder.
 *
 * REVIEW: AlarmManager removes alarms after a device restart. Keeping only the minimum
 * notification payload lets [AndroidReminderRestoreReceiver] rebuild them without starting the
 * UI or depending on a network request during boot.
 */
internal data class StoredReminder(
    val taskId: String,
    val title: String,
    val details: String?,
    val triggerAtMillis: Long,
)

internal object AndroidReminderStore {
    private const val PREFERENCES_NAME = "task_reminders"
    private const val IDS_KEY = "reminder_ids"
    private const val TRIGGER_PREFIX = "trigger:"
    private const val TITLE_PREFIX = "title:"
    private const val DETAILS_PREFIX = "details:"

    fun save(context: Context, reminder: StoredReminder) {
        val preferences = preferences(context)
        val ids = preferences.getStringSet(IDS_KEY, emptySet()).orEmpty().toMutableSet()
        ids += reminder.taskId
        preferences.edit()
            .putStringSet(IDS_KEY, ids)
            .putLong(TRIGGER_PREFIX + reminder.taskId, reminder.triggerAtMillis)
            .putString(TITLE_PREFIX + reminder.taskId, reminder.title)
            .putString(DETAILS_PREFIX + reminder.taskId, reminder.details)
            .apply()
    }

    fun remove(context: Context, taskId: String) {
        val preferences = preferences(context)
        val ids = preferences.getStringSet(IDS_KEY, emptySet()).orEmpty().toMutableSet()
        ids -= taskId
        preferences.edit()
            .putStringSet(IDS_KEY, ids)
            .remove(TRIGGER_PREFIX + taskId)
            .remove(TITLE_PREFIX + taskId)
            .remove(DETAILS_PREFIX + taskId)
            .apply()
    }

    fun all(context: Context): List<StoredReminder> {
        val preferences = preferences(context)
        return preferences.getStringSet(IDS_KEY, emptySet())
            .orEmpty()
            .mapNotNull { taskId ->
                val triggerAt = preferences.getLong(TRIGGER_PREFIX + taskId, 0L)
                val title = preferences.getString(TITLE_PREFIX + taskId, null)
                if (triggerAt <= 0L || title == null) {
                    remove(context, taskId)
                    null
                } else {
                    StoredReminder(
                        taskId = taskId,
                        title = title,
                        details = preferences.getString(DETAILS_PREFIX + taskId, null),
                        triggerAtMillis = triggerAt,
                    )
                }
            }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

internal object AndroidReminderScheduler {
    fun schedule(context: Context, reminder: StoredReminder) {
        val appContext = context.applicationContext
        if (reminder.triggerAtMillis <= System.currentTimeMillis()) {
            cancel(appContext, reminder.taskId)
            return
        }

        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val pendingIntent = reminderPendingIntent(
            context = appContext,
            reminder = reminder,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Exact alarms require a special user grant on Android 12+. The inexact fallback still
        // delivers the reminder and avoids a SecurityException when that grant is unavailable.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAtMillis,
                pendingIntent,
            )
        } else {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerAtMillis,
                    pendingIntent,
                )
            }.getOrElse {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerAtMillis,
                    pendingIntent,
                )
            }
        }
        AndroidReminderStore.save(appContext, reminder)
    }

    fun cancel(context: Context, taskId: String) {
        val appContext = context.applicationContext
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            taskId.hashCode(),
            Intent(appContext, AndroidNotificationReceiver::class.java).apply {
                action = ACTION_SHOW_TASK
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pendingIntent?.let {
            appContext.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
        AndroidReminderStore.remove(appContext, taskId)
    }

    fun cancelAll(context: Context) {
        AndroidReminderStore.all(context).forEach { reminder ->
            cancel(context, reminder.taskId)
        }
    }

    fun restoreAll(context: Context) {
        AndroidReminderStore.all(context).forEach { reminder ->
            schedule(context, reminder)
        }
    }

    private fun reminderPendingIntent(
        context: Context,
        reminder: StoredReminder,
        flags: Int,
    ): PendingIntent {
        val intent = Intent(context, AndroidNotificationReceiver::class.java).apply {
            action = ACTION_SHOW_TASK
            putExtra(EXTRA_TASK_ID, reminder.taskId)
            putExtra(EXTRA_TITLE, reminder.title)
            putExtra(EXTRA_DETAILS, reminder.details)
        }
        return PendingIntent.getBroadcast(context, reminder.taskId.hashCode(), intent, flags)
    }
}
