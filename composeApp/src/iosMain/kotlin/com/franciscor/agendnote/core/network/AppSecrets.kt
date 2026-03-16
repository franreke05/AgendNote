package com.franciscor.agendnote.core.network

import platform.Foundation.NSBundle

actual object AppSecrets {
    actual val appSecret: String = run {
        val raw = NSBundle.mainBundle.objectForInfoDictionaryKey("APP_SECRET") as? String
        if (raw.isNullOrBlank() || raw.contains("\$(")) "" else raw
    }
}
