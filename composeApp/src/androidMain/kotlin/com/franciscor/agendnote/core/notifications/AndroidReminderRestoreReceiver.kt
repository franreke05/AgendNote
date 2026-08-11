package com.franciscor.agendnote.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AndroidReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        AndroidNotificationReceiver.createChannels(context)
        AndroidReminderScheduler.restoreAll(context)
    }
}
