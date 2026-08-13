package com.franciscor.agendnote.core.notifications

import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate

interface NotificationService {
    suspend fun scheduleTaskNotification(task: TaskItem, taskDate: LocalDate)

    suspend fun cancelTaskNotification(taskId: String)

    suspend fun cancelAllTaskNotifications()

    suspend fun requestPermissions()

}
