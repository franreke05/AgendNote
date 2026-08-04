package com.franciscor.agendnote.core.notifications

import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSDateComponents
import platform.Foundation.NSTimeZone
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

actual object NotificationServiceProvider {
    actual fun getNotificationService(): NotificationService = IosNotificationService
}

object IosNotificationService : NotificationService {
    override suspend fun scheduleTaskNotification(task: TaskItem, taskDate: LocalDate) {
        // Prefiere el recordatorio explícito más próximo (task.reminders); si no hay ninguno,
        // usa la hora planificada como antes (tareas anteriores a este modelo). Solo se
        // programa una notificación por tarea aunque haya varios recordatorios guardados - ver
        // earliestReminderInstant() y docs/agendnote/FASE4_PROPUESTA.md.
        val zone = TimeZone.currentSystemDefault()
        val reminderLocal = earliestReminderInstant(task)?.toLocalDateTime(zone)
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

        val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()

        // Crear contenido de la notificación
        val content = UNMutableNotificationContent()
        content.setTitle("Recordatorio de tarea")
        content.setBody(task.title)
        if (!task.details.isNullOrBlank()) {
            content.setSubtitle(task.details)
        }
        content.setSound(platform.UserNotifications.UNNotificationSound.defaultSound())

        // Crear el trigger para la hora específica
        val components = NSDateComponents()
        components.day = day
        components.month = month
        components.year = year
        components.hour = hour
        components.minute = minute

        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        
        // Crear la solicitud de notificación
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = "task_${task.id}",
            content = content,
            trigger = trigger
        )
        
        // Programar la notificación
        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                println("Error scheduling notification: ${error.localizedDescription}")
            } else {
                println("Notification scheduled for task: ${task.id}")
            }
        }
    }

    override suspend fun cancelTaskNotification(taskId: String) {
        val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(listOf("task_$taskId"))
    }

    override suspend fun cancelAllTaskNotifications() {
        UNUserNotificationCenter.currentNotificationCenter()
            .removeAllPendingNotificationRequests()
    }

    override suspend fun requestPermissions() {
        val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound
        
        notificationCenter.requestAuthorizationWithOptions(options) { granted, error ->
            if (error != null) {
                println("Error requesting notification permissions: ${error.localizedDescription}")
            } else if (granted) {
                println("Notification permissions granted")
            } else {
                println("Notification permissions denied by user")
            }
        }
    }
}
