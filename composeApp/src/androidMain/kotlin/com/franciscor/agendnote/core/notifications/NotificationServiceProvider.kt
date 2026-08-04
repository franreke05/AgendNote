package com.franciscor.agendnote.core.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate
import java.util.Calendar

actual object NotificationServiceProvider {
    actual fun getNotificationService(): NotificationService = AndroidNotificationService
}

/** Android notification scheduling backed by AlarmManager so it survives app process death. */
object AndroidNotificationService : NotificationService {
    private var activity: Activity? = null
    private var applicationContext: Context? = null
    private var requestNotificationPermission: (() -> Unit)? = null
    private var hadExactAlarmAccess: Boolean? = null

    fun initialize(
        activity: Activity,
        requestNotificationPermission: () -> Unit,
    ) {
        this.activity = activity
        applicationContext = activity.applicationContext
        this.requestNotificationPermission = requestNotificationPermission
        hadExactAlarmAccess = canScheduleExactAlarms(activity.applicationContext)
        AndroidNotificationReceiver.createChannel(activity.applicationContext)
    }

    fun onHostResumed(activity: Activity) {
        this.activity = activity
        applicationContext = activity.applicationContext
        val hasExactAccess = canScheduleExactAlarms(activity.applicationContext)
        if (hasExactAccess && hadExactAlarmAccess == false) {
            // REVIEW: alarms created before the special access was granted used Android's
            // inexact fallback. Replace them as soon as the user returns from system settings.
            AndroidReminderScheduler.restoreAll(activity.applicationContext)
        }
        hadExactAlarmAccess = hasExactAccess
    }

    fun onNotificationPermissionResult() {
        activity?.let(::requestExactAlarmAccess)
    }

    override suspend fun scheduleTaskNotification(task: TaskItem, taskDate: LocalDate) {
        val context = applicationContext ?: return
        val triggerAt = triggerAtMillis(task, taskDate) ?: return
        AndroidReminderScheduler.schedule(
            context = context,
            reminder = StoredReminder(
                taskId = task.id,
                title = task.title,
                details = task.details,
                triggerAtMillis = triggerAt,
            ),
        )
    }

    /**
     * Prefers the earliest of [TaskItem.reminders] (see [earliestReminderInstant]); falls back
     * to the legacy planned-time computation for tasks that predate that field. Only one alarm
     * is scheduled per task even when there are several reminders - see
     * [com.franciscor.agendnote.core.notifications.earliestReminderInstant]'s doc comment for
     * why, and docs/agendnote/FASE4_PROPUESTA.md for the follow-up.
     */
    private fun triggerAtMillis(task: TaskItem, taskDate: LocalDate): Long? {
        earliestReminderInstant(task)?.let { return it.toEpochMilliseconds() }
        val time = task.time ?: return null
        return Calendar.getInstance().apply {
            clear()
            set(taskDate.year, taskDate.month.ordinal, taskDate.day, time.hour, time.minute, 0)
        }.timeInMillis
    }

    override suspend fun cancelTaskNotification(taskId: String) {
        val context = applicationContext ?: return
        AndroidReminderScheduler.cancel(context, taskId)
    }

    override suspend fun cancelAllTaskNotifications() {
        val context = applicationContext ?: return
        AndroidReminderScheduler.cancelAll(context)
    }

    override suspend fun requestPermissions() {
        val host = activity ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(host, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission?.invoke()
            return
        }
        requestExactAlarmAccess(host)
    }

    private fun requestExactAlarmAccess(host: Activity) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            canScheduleExactAlarms(host)
        ) {
            return
        }
        runCatching {
            host.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${host.packageName}")
                },
            )
        }
    }

    private fun canScheduleExactAlarms(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return true
        return context.getSystemService(android.app.AlarmManager::class.java)
            .canScheduleExactAlarms()
    }
}
