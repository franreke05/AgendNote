package com.franciscor.agendnote.core.notifications

import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

internal object NotificationPayloadKeys {
    const val TYPE = "type"
    const val TASK_ID = "task_id"
    const val TASK_DAY = "task_day"
    const val TYPE_TASK = "task"
}

private fun taskNotificationIdentifier(taskId: String) = "task_$taskId"

actual object NotificationServiceProvider {
    actual fun getNotificationService(): NotificationService = IosNotificationService
}

/** Standard iOS local reminders with the system-provided notification sound. */
object IosNotificationService : NotificationService {
    override suspend fun scheduleTaskNotification(task: TaskItem, taskDate: LocalDate) {
        requestPermissions()

        val zone = TimeZone.currentSystemDefault()
        val reminderInstant = earliestReminderInstant(task)
        val reminderLocal = reminderInstant?.toLocalDateTime(zone)
        val time = task.time
        if (reminderLocal == null && time == null) return
        val components = NSDateComponents().apply {
            year = (reminderLocal?.year ?: taskDate.year).toLong()
            month = (reminderLocal?.monthNumber ?: taskDate.monthNumber).toLong()
            day = (reminderLocal?.dayOfMonth ?: taskDate.dayOfMonth).toLong()
            hour = (reminderLocal?.hour ?: time?.hour ?: return).toLong()
            minute = (reminderLocal?.minute ?: time?.minute ?: return).toLong()
        }

        val content = UNMutableNotificationContent()
        content.setTitle("Recordatorio de tarea")
        content.setBody(task.title)
        if (!task.details.isNullOrBlank()) {
            content.setSubtitle(task.details)
        }
        content.setSound(UNNotificationSound.defaultSound())
        content.setUserInfo(
            mapOf(
                NotificationPayloadKeys.TYPE to NotificationPayloadKeys.TYPE_TASK,
                NotificationPayloadKeys.TASK_ID to task.id,
                NotificationPayloadKeys.TASK_DAY to taskDate.toString(),
            ),
        )

        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
            components,
            repeats = false,
        )
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = taskNotificationIdentifier(task.id),
            content = content,
            trigger = trigger,
        )
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { error ->
            if (error != null) {
                println("IosNotificationService: error scheduling task ${task.id}: ${error.localizedDescription}")
            }
        }
    }

    override suspend fun cancelTaskNotification(taskId: String) {
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(listOf(taskNotificationIdentifier(taskId)))
    }

    override suspend fun cancelAllTaskNotifications() {
        UNUserNotificationCenter.currentNotificationCenter().removeAllPendingNotificationRequests()
    }

    override suspend fun requestPermissions() {
        val options = UNAuthorizationOptionAlert or
            UNAuthorizationOptionBadge or
            UNAuthorizationOptionSound
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(options) { granted, error ->
                if (error != null) {
                    println("IosNotificationService: permission error: ${error.localizedDescription}")
                } else {
                    println("IosNotificationService: notification permission granted=$granted")
                }
            }
    }
}
