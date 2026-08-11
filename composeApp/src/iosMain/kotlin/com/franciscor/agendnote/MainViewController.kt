package com.franciscor.agendnote

import androidx.compose.ui.window.ComposeUIViewController
import com.franciscor.agendnote.core.notifications.IosNotificationDelegate
import platform.UserNotifications.UNUserNotificationCenter

// Held at file scope, not created inline where it's assigned - UNUserNotificationCenter.delegate
// is a weak reference (the standard iOS delegate pattern), so nothing else in the app would keep
// this Kotlin/Native object alive otherwise, and it would be deallocated right after being set.
// NOT INDEPENDENTLY VERIFIED - no Xcode/macOS in this environment to build/run this.
private val notificationDelegate = IosNotificationDelegate()

fun MainViewController() = ComposeUIViewController {
    UNUserNotificationCenter.currentNotificationCenter().delegate = notificationDelegate
    App()
}
