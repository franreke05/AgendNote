package com.franciscor.agendnote.core.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
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

    internal var applicationContext: Context? = null
    private var requestNotificationPermission: (() -> Unit)? = null
    private var permissionRequestInFlight = false

    fun initialize(
        activity: Activity,
        requestNotificationPermission: () -> Unit,
    ) {
        this.activity = activity
        applicationContext = activity.applicationContext
        this.requestNotificationPermission = requestNotificationPermission
        AndroidNotificationReceiver.createChannel(activity.applicationContext)
    }

    fun onHostResumed(activity: Activity) {
        this.activity = activity
        applicationContext = activity.applicationContext
    }

    fun onNotificationPermissionResult() {
        permissionRequestInFlight = false
    }

    override suspend fun scheduleTaskNotification(task: TaskItem, taskDate: LocalDate) {
        val context = applicationContext ?: return
        val reminderInstant = earliestReminderInstant(task)
        val triggerAt = triggerAtMillis(task, taskDate, reminderInstant) ?: return
        requestPermissions()
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
    private fun triggerAtMillis(
        task: TaskItem,
        taskDate: LocalDate,
        reminderInstant: kotlinx.datetime.Instant?,
    ): Long? {
        reminderInstant?.let { return it.toEpochMilliseconds() }
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
            if (!permissionRequestInFlight) {
                permissionRequestInFlight = true
                host.runOnUiThread {
                    requestNotificationPermission?.invoke()
                }
            }
        }
    }

}
