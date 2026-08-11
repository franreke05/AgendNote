package com.franciscor.agendnote.core.notifications

/**
 * Mirrors `UNAuthorizationStatus` (iOS) closely enough to display real state in Settings without
 * pretending the app can grant/override what only the OS controls (Operación Aniversario,
 * "Sprint Final" directive, item 12: "no inventes falsas opciones si iOS controla la
 * autorización"). Android has no equivalent multi-state model pre-Android 13 - see the androidMain
 * actual for how it degrades to just [AUTHORIZED]/[DENIED].
 */
enum class NotificationPermissionStatus {
    NOT_DETERMINED,
    AUTHORIZED,
    DENIED,
    /** iOS-only "quiet" delivery granted via `.provisional` - notifications land in Notification
     * Center without an alert/sound until the user upgrades it. Android actual never reports this. */
    PROVISIONAL,
}
