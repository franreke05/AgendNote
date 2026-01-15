package com.franciscor.agendnote.data

import platform.Foundation.NSBundle

actual object AppSecrets {
    private const val fallbackSecret = "xocHim-nucti8-submam"

    actual val appSecret: String = run {
        val raw = NSBundle.mainBundle.objectForInfoDictionaryKey("APP_SECRET") as? String
        if (raw.isNullOrBlank() || raw.contains("\$(")) fallbackSecret else raw
    }
}
