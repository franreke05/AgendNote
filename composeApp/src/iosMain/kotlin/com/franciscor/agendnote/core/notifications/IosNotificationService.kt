package com.franciscor.agendnote.core.notifications

import com.franciscor.agendnote.core.model.PersonalMessage
import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/** Payload keys used in `UNMutableNotificationContent.userInfo` - see [NotificationRoute] and
 * [IosNotificationDelegate] for the reader side. Kept tiny and explicit (directive item 9: "no
 * serialices objetos gigantes"), never anything beyond an id/day/type. */
internal object NotificationPayloadKeys {
    const val TYPE = "type"
    const val TASK_ID = "task_id"
    const val TASK_DAY = "task_day"
    const val MESSAGE_ID = "message_id"
    const val TYPE_TASK = "task"
    const val TYPE_PERSONAL_MESSAGE = "personal_message"
}

private fun taskNotificationIdentifier(taskId: String) = "task_$taskId"
private fun personalMessageNotificationIdentifier(messageId: String) = "personal_message_$messageId"

actual object NotificationServiceProvider {
    actual fun getNotificationService(): NotificationService = IosNotificationService
}

/**
 * NOT INDEPENDENTLY VERIFIED - this file compiles nowhere in this environment (no Xcode/macOS
 * available to build the iosMain source set). Written carefully against documented
 * UserNotifications/Kotlin-Native interop APIs and reviewed line by line, but IOS_RUNTIME =
 * NOT_VERIFIED until built and run on a real Mac/simulator. See
 * docs/OPERATION_ANNIVERSARY_STATUS.md.
 */
object IosNotificationService : NotificationService {
    override suspend fun scheduleTaskNotification(task: TaskItem, taskDate: LocalDate) {
        val zone = TimeZone.currentSystemDefault()
        val reminderInstant = earliestReminderInstant(task)
        val reminderLocal = reminderInstant?.toLocalDateTime(zone)
        val year: Long
        val month: Long
        val day: Long
        val hour: Long
        val minute: Long
        if (reminderLocal != null) {
            year = reminderLocal.year.toLong()
            month = reminderLocal.monthNumber.toLong()
            day = reminderLocal.dayOfMonth.toLong()
            hour = reminderLocal.hour.toLong()
            minute = reminderLocal.minute.toLong()
        } else {
            val time = task.time ?: return
            year = taskDate.year.toLong()
            month = taskDate.monthNumber.toLong()
            day = taskDate.dayOfMonth.toLong()
            hour = time.hour.toLong()
            minute = time.minute.toLong()
        }

        val soundId = if (reminderInstant != null) {
            resolveTaskReminderSoundId(task, taskDate, reminderInstant, zone)
        } else {
            NotificationSoundId.REMINDER_GENERAL
        }

        val content = UNMutableNotificationContent()
        content.setTitle("Recordatorio de tarea")
        content.setBody(task.title)
        if (!task.details.isNullOrBlank()) {
            content.setSubtitle(task.details)
        }
        content.setSound(resolveSound(soundId))
        content.setUserInfo(
            mapOf(
                NotificationPayloadKeys.TYPE to NotificationPayloadKeys.TYPE_TASK,
                NotificationPayloadKeys.TASK_ID to task.id,
                NotificationPayloadKeys.TASK_DAY to taskDate.toString(),
            ),
        )

        val components = NSDateComponents()
        components.day = day
        components.month = month
        components.year = year
        components.hour = hour
        components.minute = minute

        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)

        // Same identifier every time for this task (not a random UUID) - addNotificationRequest
        // with an identifier that already has a pending request replaces it in place. This is
        // exactly how AgendaViewModel.reconcileDayNotifications relies on rescheduling working:
        // it calls this again on every edit rather than always cancel-then-schedule, and a
        // random identifier per call would silently leave the old notification still pending
        // (directive item 5: "no uses UUID aleatorios... si eso impide cancelar correctamente").
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

    override suspend fun schedulePersonalMessageNotification(message: PersonalMessage) {
        val zone = TimeZone.currentSystemDefault()
        val local = message.scheduledAt.toLocalDateTime(zone)

        val content = UNMutableNotificationContent()
        content.setTitle(message.title ?: "Mensaje para ti")
        content.setBody(message.body)
        content.setSound(resolveSound(message.notificationSoundId ?: NotificationSoundId.PERSONAL_MESSAGE))
        content.setUserInfo(
            mapOf(
                NotificationPayloadKeys.TYPE to NotificationPayloadKeys.TYPE_PERSONAL_MESSAGE,
                NotificationPayloadKeys.MESSAGE_ID to message.id,
            ),
        )

        val components = NSDateComponents()
        components.day = local.dayOfMonth.toLong()
        components.month = local.monthNumber.toLong()
        components.year = local.year.toLong()
        components.hour = local.hour.toLong()
        components.minute = local.minute.toLong()

        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = personalMessageNotificationIdentifier(message.id),
            content = content,
            trigger = trigger,
        )

        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { error ->
            if (error != null) {
                println(
                    "IosNotificationService: error scheduling personal message ${message.id}: " +
                        error.localizedDescription,
                )
            }
        }
    }

    override suspend fun cancelPersonalMessageNotification(messageId: String) {
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(
                listOf(personalMessageNotificationIdentifier(messageId)),
            )
    }

    override suspend fun requestPermissions() {
        val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound

        // requestAuthorizationWithOptions itself is already safe to call more than once - iOS
        // only shows the system prompt the first time; every call after the user has answered
        // (authorized or denied) just returns that same answer immediately, no second prompt
        // (directive item 12: "no volver a spamear el prompt del sistema"). Nothing extra needed
        // here beyond calling it from an explicit user action, which SettingsScreen already does.
        notificationCenter.requestAuthorizationWithOptions(options) { granted, error ->
            if (error != null) {
                println("IosNotificationService: error requesting permissions: ${error.localizedDescription}")
            } else {
                println("IosNotificationService: notification permission granted=$granted")
            }
        }
    }

    override suspend fun checkPermissionStatus(): NotificationPermissionStatus =
        suspendCancellableCoroutine { continuation ->
            UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
                val status = when (settings?.authorizationStatus) {
                    UNAuthorizationStatusAuthorized -> NotificationPermissionStatus.AUTHORIZED
                    UNAuthorizationStatusDenied -> NotificationPermissionStatus.DENIED
                    UNAuthorizationStatusProvisional -> NotificationPermissionStatus.PROVISIONAL
                    UNAuthorizationStatusNotDetermined -> NotificationPermissionStatus.NOT_DETERMINED
                    else -> NotificationPermissionStatus.NOT_DETERMINED
                }
                if (continuation.isActive) continuation.resume(status)
            }
        }

    /**
     * Resolves a [NotificationSoundId] to a real, bundled `UNNotificationSound`, falling back to
     * `.defaultSound()` when the asset isn't there - directive item 3/11: "si el asset falta: NO
     * CRASH... fallback: UNNotificationSound.defaultSound()". [IosSoundAssets] already only
     * returns a filename when it actually verified the resource exists in the bundle.
     */
    private fun resolveSound(id: NotificationSoundId): UNNotificationSound {
        val filename = IosSoundAssets.resolveNotificationSoundFilename(id)
        if (filename == null) {
            println("IosNotificationService: no bundled sound for $id, using default")
            return UNNotificationSound.defaultSound()
        }
        return UNNotificationSound.soundNamed("$filename.wav")
    }
}
