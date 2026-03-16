package com.franciscor.agendnote.core.network

import com.franciscor.agendnote.BuildConfig

actual object AppSecrets {
    actual val appSecret: String = BuildConfig.APP_SECRET
}
