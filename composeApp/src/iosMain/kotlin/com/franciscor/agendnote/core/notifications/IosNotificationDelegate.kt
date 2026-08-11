package com.franciscor.agendnote.core.notifications

import kotlinx.datetime.LocalDate
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * NOT INDEPENDENTLY VERIFIED - see IosNotificationService's doc comment; no Xcode/macOS in this
 * environment to build/run this. Written against the standard, documented Kotlin/Native mapping
 * of `UNUserNotificationCenterDelegate`'s two Objective-C methods (parameter labels become named
 * Kotlin parameters).
 *
 * Handles all three states the directive calls out (item 9):
 * - **Foreground**: `willPresentNotification` - without this override, a notification that
 *   arrives while AgendNote is already open shows nothing at all (default UNUserNotificationCenter
 *   behavior). Explicitly opts into showing it exactly like backgrounded/killed would.
 * - **Background tap** and **cold-start tap** (app was killed, iOS launches it fresh because the
 *   user tapped the notification): both funnel through the same `didReceiveNotificationResponse`
 *   - iOS itself is what decides whether that's a warm resume or a cold launch, this delegate
 *   doesn't need to know which. [NotificationRouter] being a `StateFlow` (not a one-shot event) is
 *   what makes the cold-start case work: `AppNavHost` picks up whatever route was set here the
 *   moment it starts collecting, even if that happened before `AppNavHost` existed yet.
 *
 * Must be assigned to `UNUserNotificationCenter.currentNotificationCenter().delegate` once, at
 * app start - see MainViewController.kt.
 */
class IosNotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(
            UNNotificationPresentationOptionBanner or
                UNNotificationPresentationOptionSound or
                UNNotificationPresentationOptionList,
        )
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        routeFromUserInfo(didReceiveNotificationResponse.notification.request.content.userInfo)
        withCompletionHandler()
    }

    private fun routeFromUserInfo(userInfo: Map<Any?, *>) {
        when (userInfo[NotificationPayloadKeys.TYPE] as? String) {
            NotificationPayloadKeys.TYPE_TASK -> {
                val taskId = userInfo[NotificationPayloadKeys.TASK_ID] as? String ?: return
                val dayString = userInfo[NotificationPayloadKeys.TASK_DAY] as? String ?: return
                val day = runCatching { LocalDate.parse(dayString) }.getOrNull() ?: return
                NotificationRouter.route(NotificationRoute.Task(taskId, day))
            }
            NotificationPayloadKeys.TYPE_PERSONAL_MESSAGE -> {
                val messageId = userInfo[NotificationPayloadKeys.MESSAGE_ID] as? String ?: return
                NotificationRouter.route(NotificationRoute.PersonalMessage(messageId))
            }
            // Directive item 9: tapping a notification must never just fall back to "open Home"
            // silently - an unrecognized/missing payload is logged instead of pretending routing
            // succeeded, even though there is genuinely nowhere useful to send the user for a
            // payload this app didn't create itself.
            else -> println("IosNotificationDelegate: unrecognized notification payload: $userInfo")
        }
    }
}
