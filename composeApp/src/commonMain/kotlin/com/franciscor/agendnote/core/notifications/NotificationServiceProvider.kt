package com.franciscor.agendnote.core.notifications

expect object NotificationServiceProvider {
    fun getNotificationService(): NotificationService
}
