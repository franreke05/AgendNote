package com.franciscor.agendnote.core.notifications

import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate

actual object NotificationServiceProvider {
    actual fun getNotificationService(): NotificationService = AndroidNotificationService
}

object AndroidNotificationService : NotificationService {
    override suspend fun scheduleTaskNotification(task: TaskItem, taskDate: LocalDate) {
        // Android implementation would go here
        // For now, we'll leave it as a stub
    }

    override suspend fun cancelTaskNotification(taskId: String) {
        // Android implementation would go here
    }

    override suspend fun requestPermissions() {
        // Android implementation would go here
    }
}
