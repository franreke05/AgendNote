package com.franciscor.agendnote.core.network

import com.franciscor.agendnote.shared.BuildConfig

actual object AppSecrets {
    actual val appSecret: String = BuildConfig.APP_SECRET
}
