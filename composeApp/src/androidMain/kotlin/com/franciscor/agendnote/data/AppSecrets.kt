package com.franciscor.agendnote.data

import com.franciscor.agendnote.BuildConfig

actual object AppSecrets {
    actual val appSecret: String = BuildConfig.APP_SECRET
}
