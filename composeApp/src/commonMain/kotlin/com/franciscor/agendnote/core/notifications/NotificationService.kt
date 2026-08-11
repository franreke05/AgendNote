package com.franciscor.agendnote.core.notifications

import com.franciscor.agendnote.core.model.PersonalMessage
import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate

interface NotificationService {
    suspend fun scheduleTaskNotification(task: TaskItem, taskDate: LocalDate)

    suspend fun cancelTaskNotification(taskId: String)

    suspend fun cancelAllTaskNotifications()

    suspend fun requestPermissions()

    /** Live read of the OS-level authorization state - see [NotificationPermissionStatus]. Never
     * triggers the system permission prompt itself (that is only [requestPermissions]'s job). */
    suspend fun checkPermissionStatus(): NotificationPermissionStatus

    suspend fun schedulePersonalMessageNotification(message: PersonalMessage)

    suspend fun cancelPersonalMessageNotification(messageId: String)
}
