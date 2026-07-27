package com.franciscor.agendnote.core.notifications

import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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
        // Solo programar si la tarea tiene hora
        if (task.time == null) return
        
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
        components.day = taskDate.dayOfMonth.toLong()
        components.month = taskDate.monthNumber.toLong()
        components.year = taskDate.year.toLong()
        components.hour = task.time.hour.toLong()
        components.minute = task.time.minute.toLong()
        
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
